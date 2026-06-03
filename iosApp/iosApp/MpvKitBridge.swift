import Foundation
import UIKit
import Libmpv
import shared

// MARK: - Layer & view subclasses

/// `CAMetalLayer` subclass that ignores the spurious 1×1 `drawableSize` MoltenVK
/// forces during presentation completion (causes flicker / stuck-tiny rendering
/// on rotation). Same workaround the upstream MPVKit demo uses.
/// Reference: https://github.com/KhronosGroup/MoltenVK/issues/2226
final class MPVMetalLayer: CAMetalLayer {
    override var drawableSize: CGSize {
        get { super.drawableSize }
        set {
            if Int(newValue.width) > 1 && Int(newValue.height) > 1 {
                super.drawableSize = newValue
            }
        }
    }
}

/// Host `UIView` containing a `CAMetalLayer`. mpv is bound to `metalLayer` via the
/// `wid` option; this class keeps the layer's frame in sync with the host view's
/// bounds via `layoutSubviews`. Without this override the layer would stay at its
/// initial bounds (`.zero`) regardless of how the parent is laid out by Compose's
/// `UIKitView`, and video would render into a 0×0 framebuffer.
final class MPVRenderView: UIView {
    let metalLayer = MPVMetalLayer()

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .black
        metalLayer.contentsScale = UIScreen.main.nativeScale
        metalLayer.framebufferOnly = true
        metalLayer.backgroundColor = UIColor.black.cgColor
        metalLayer.frame = bounds
        layer.addSublayer(metalLayer)
        syncDrawableSize()
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("MPVRenderView is code-instantiated only") }

    override func layoutSubviews() {
        super.layoutSubviews()
        // Match the metal layer to the host view with NO implicit animation: an animated
        // bounds change would present a stretched frame for one cycle and fight mpv's own
        // swapchain resize. Compose resizes this interop view (the player uses fillMaxSize),
        // which is what drives this callback.
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        metalLayer.frame = bounds
        syncDrawableSize()
        CATransaction.commit()
    }

    /// mpv's `moltenvk` gpu-context reads `layer.drawableSize` directly (see MPVKit's
    /// `0001-player-add-moltenvk-context.patch`) and sizes its Vulkan swapchain from it when the
    /// video output (re)configures. CAMetalLayer's implicit bounds->drawableSize derivation is
    /// unreliable for a manually-managed sublayer and is 0x0 until the first real layout — and a
    /// 0x0 drawableSize makes mpv build a 0-sized swapchain that never recovers: black screen +
    /// stalled playback. So set it explicitly, and never clobber a good size with a transient 0
    /// (Compose can briefly flash a zero-size frame during its first layout pass).
    private func syncDrawableSize() {
        let scale = metalLayer.contentsScale
        let size = CGSize(width: bounds.width * scale, height: bounds.height * scale)
        guard size.width > 1, size.height > 1 else { return }
        metalLayer.drawableSize = size
    }
}

// MARK: - Bridge

/// Swift implementation of `MpvKitPlayerBridge` driving the raw libmpv C API via MPVKit.
///
/// All bridge methods are called from the Kotlin side through `withContext(Dispatchers.Main.immediate)`,
/// so they land on the main thread. mpv's C API is itself thread-safe, so no extra
/// locking is required for property/command calls.
///
/// **Rendering** uses Vulkan via MoltenVK (`vo=gpu-next, gpu-api=vulkan, gpu-context=moltenvk`).
/// The `CAMetalLayer` pointer is bound to mpv via the `wid` option *before* `mpv_initialize`;
/// mpv then creates its own `VkInstance`/`VkSurface` and draws frames straight into the layer.
///
/// **Events** are delivered via `mpv_set_wakeup_callback`. mpv calls it on its internal
/// thread when events are available; we hop to a serial queue and drain via `mpv_wait_event`
/// with a 0-second timeout. `destroy()` removes the callback and `sync`s on the queue as a
/// barrier before calling `mpv_terminate_destroy`, so no in-flight drain can race the teardown.
class MpvKitBridgeImpl: MpvKitPlayerBridge, @unchecked Sendable {

    /// The opaque mpv handle. Read on multiple threads; only mutated from main, and the
    /// destroy path drains the event queue before clearing it, so no atomic needed.
    private var mpv: OpaquePointer?

    /// The view containing the Metal layer. Created in `getPlayerView()`, retained until
    /// `destroy()`. Compose's `UIKitView` borrows the reference for display.
    private var renderView: MPVRenderView?

    /// Serial queue draining mpv events. Wakeup callback dispatches a drain here;
    /// `destroy()` syncs on it as a barrier.
    private let eventQueue = DispatchQueue(label: "com.syncplay.mpvkit.events", qos: .userInitiated)

    override init() { super.init() }

    // MARK: - Lifecycle

    override func create() {
        // Idempotent: Compose's UIKitView factory may re-fire on recomposition, and
        // MpvKitImpl.initialize() guards too — but defending here as well is cheap.
        guard mpv == nil else { return }

        guard let ctx = mpv_create() else {
            print("[MPVKit] mpv_create failed")
            return
        }
        mpv = ctx

        // Surface mpv's own diagnostics (vo selection, swapchain, decoder, hwdec fallback).
        // Without this, a failed video output or codec is completely silent — which is exactly
        // what made the iOS MPV black-screen so hard to pin down. Delivered via
        // MPV_EVENT_LOG_MESSAGE (handled below) and printed to the Xcode console. Lower the
        // level to "info"/"warn" if "v" (verbose) gets too chatty.
        mpv_request_log_messages(ctx, "v")

        // 1. Configure mpv. All options must be set BEFORE mpv_initialize.
        check(mpv_set_option_string(ctx, "vo", "gpu-next"))
        check(mpv_set_option_string(ctx, "gpu-api", "vulkan"))
        check(mpv_set_option_string(ctx, "gpu-context", "moltenvk"))
        check(mpv_set_option_string(ctx, "hwdec", "videotoolbox"))
        check(mpv_set_option_string(ctx, "keep-open", "yes"))
        check(mpv_set_option_string(ctx, "idle", "yes"))
        check(mpv_set_option_string(ctx, "input-default-bindings", "no"))
        check(mpv_set_option_string(ctx, "input-vo-keyboard", "no"))
        check(mpv_set_option_string(ctx, "video-rotate", "no"))
        check(mpv_set_option_string(ctx, "subs-match-os-language", "yes"))
        check(mpv_set_option_string(ctx, "subs-fallback", "yes"))

        // 2. Bind the Metal layer to mpv. mpv reads 8 bytes at the address as an Int64,
        //    treating it as the layer/window handle. With gpu-context=moltenvk this is
        //    where mpv will create its surface and present frames. MUST happen before
        //    mpv_initialize — setting `wid` afterwards has no effect.
        if let view = renderView {
            // mpv's moltenvk context does `(__bridge CAMetalLayer*)(intptr_t)WinID`, so `wid`
            // must carry the raw pointer value of the CAMetalLayer as an Int64. (Passing the
            // address of a class-typed local happens to alias the same bytes today, but spelling
            // out the pointer keeps it from silently breaking under a compiler/ABI change.)
            var wid = Int64(Int(bitPattern: Unmanaged.passUnretained(view.metalLayer).toOpaque()))
            check(mpv_set_option(ctx, "wid", MPV_FORMAT_INT64, &wid))
        } else {
            print("[MPVKit] WARNING: create() called before getPlayerView() — video will not render")
        }

        // 3. Initialize.
        if mpv_initialize(ctx) < 0 {
            print("[MPVKit] mpv_initialize failed")
            mpv_destroy(ctx)
            mpv = nil
            return
        }

        // 4. Observe properties for state sync with Kotlin.
        mpv_observe_property(ctx, 0, "time-pos", MPV_FORMAT_DOUBLE)
        mpv_observe_property(ctx, 0, "duration", MPV_FORMAT_DOUBLE)
        mpv_observe_property(ctx, 0, "pause", MPV_FORMAT_FLAG)

        // 5. Wakeup callback drives the event drain. The opaque pointer holds an
        //    UNRETAINED reference to self; destroy() removes the callback (with
        //    a barrier on the event queue) before any deallocation can happen.
        let opaqueSelf = Unmanaged.passUnretained(self).toOpaque()
        mpv_set_wakeup_callback(ctx, { ctxPtr in
            guard let ctxPtr else { return }
            let bridge = Unmanaged<MpvKitBridgeImpl>.fromOpaque(ctxPtr).takeUnretainedValue()
            bridge.scheduleEventDrain()
        }, opaqueSelf)

        // 6. Drain any events queued before the callback was wired up
        //    (start-file, log messages emitted during initialize, etc.).
        scheduleEventDrain()
    }

    override func destroy() {
        guard let ctx = mpv else { return }

        // Stop further wakeups, then sync the event queue so any in-flight drain
        // finishes before we free the handle. Without this barrier, a drain in
        // progress on `eventQueue` could call `mpv_wait_event` on a destroyed ctx.
        mpv_set_wakeup_callback(ctx, nil, nil)
        eventQueue.sync { /* barrier */ }

        // Clear the property/event callbacks Kotlin set on us — there's nothing
        // left to deliver.
        onPropertyChange = nil
        onEvent = nil

        mpv_terminate_destroy(ctx)
        mpv = nil
        renderView = nil
    }

    override func getPlayerView() -> UIView {
        if let existing = renderView {
            return existing
        }
        // Non-zero initial frame is critical: mpv reads the layer's drawableSize when it first
        // configures the video output (right after loadfile). If the layer were still 0x0 then —
        // which `.zero` guarantees until Compose's first layout pass — the swapchain is built at
        // 0x0 and never recovers, i.e. the reported black screen that won't play. Screen bounds
        // is just a sane starting size; layoutSubviews refines it to the exact container size.
        let view = MPVRenderView(frame: UIScreen.main.bounds)
        renderView = view
        return view
    }

    // MARK: - Event drain

    /// Hop from mpv's internal thread (where the wakeup callback fires) onto our
    /// serial queue, then drain every event with a 0-timeout `mpv_wait_event`.
    private func scheduleEventDrain() {
        eventQueue.async { [weak self] in
            guard let self else { return }
            while let ctx = self.mpv {
                let ev = mpv_wait_event(ctx, 0)
                guard let pointee = ev?.pointee else { return }
                if pointee.event_id == MPV_EVENT_NONE { return }
                self.handle(event: pointee)
            }
        }
    }

    private func handle(event: mpv_event) {
        switch event.event_id {
        case MPV_EVENT_PROPERTY_CHANGE:
            guard let prop = event.data?
                .assumingMemoryBound(to: mpv_event_property.self)
                .pointee
            else { return }
            let name = String(cString: prop.name)

            var value: Any? = nil
            switch prop.format {
            case MPV_FORMAT_DOUBLE:
                if let ptr = prop.data {
                    value = ptr.assumingMemoryBound(to: Double.self).pointee
                }
            case MPV_FORMAT_FLAG:
                if let ptr = prop.data {
                    value = ptr.assumingMemoryBound(to: Int32.self).pointee != 0
                }
            case MPV_FORMAT_INT64:
                if let ptr = prop.data {
                    value = ptr.assumingMemoryBound(to: Int64.self).pointee
                }
            case MPV_FORMAT_STRING:
                if let ptr = prop.data {
                    let cstr = ptr.assumingMemoryBound(to: UnsafePointer<CChar>.self).pointee
                    value = String(cString: cstr)
                }
            default: break
            }
            DispatchQueue.main.async { [weak self] in
                self?.onPropertyChange?(name, value)
            }

        case MPV_EVENT_FILE_LOADED:
            DispatchQueue.main.async { [weak self] in self?.onEvent?("file-loaded") }

        case MPV_EVENT_END_FILE:
            DispatchQueue.main.async { [weak self] in self?.onEvent?("end-file") }

        case MPV_EVENT_LOG_MESSAGE:
            // mpv's internal log line. `text` already carries a trailing newline.
            if let msg = event.data?
                .assumingMemoryBound(to: mpv_event_log_message.self)
                .pointee
            {
                let level = String(cString: msg.level)
                let prefix = String(cString: msg.prefix)
                let text = String(cString: msg.text)
                print("[mpv/\(level)] \(prefix): \(text)", terminator: "")
            }

        case MPV_EVENT_SHUTDOWN:
            // The handle is being destroyed elsewhere; nothing to do here.
            break

        default: break
        }
    }

    // MARK: - Media Loading

    override func loadFile(path: String) {
        command(["loadfile", path, "replace"])
    }

    override func loadURL(url: String) {
        command(["loadfile", url, "replace"])
    }

    // MARK: - Playback

    override func setPaused(paused: Bool) {
        guard let ctx = mpv else { return }
        var flag: Int32 = paused ? 1 : 0
        mpv_set_property(ctx, "pause", MPV_FORMAT_FLAG, &flag)
    }

    override func isPaused() -> Bool {
        guard let ctx = mpv else { return true }
        var flag: Int32 = 0
        mpv_get_property(ctx, "pause", MPV_FORMAT_FLAG, &flag)
        return flag != 0
    }

    override func seekTo(positionSeconds: Double) {
        guard let ctx = mpv else { return }
        var pos = positionSeconds
        mpv_set_property(ctx, "time-pos", MPV_FORMAT_DOUBLE, &pos)
    }

    override func getTimePos() -> Double {
        guard let ctx = mpv else { return 0 }
        var pos: Double = 0
        mpv_get_property(ctx, "time-pos", MPV_FORMAT_DOUBLE, &pos)
        return pos
    }

    override func getDuration() -> Double {
        guard let ctx = mpv else { return 0 }
        var dur: Double = 0
        mpv_get_property(ctx, "duration", MPV_FORMAT_DOUBLE, &dur)
        return dur
    }

    override func setSpeed(speed: Double) {
        guard let ctx = mpv else { return }
        var s = speed
        mpv_set_property(ctx, "speed", MPV_FORMAT_DOUBLE, &s)
    }

    // MARK: - Tracks

    override func getTrackCount() -> Int32 {
        guard let ctx = mpv else { return 0 }
        var count: Int64 = 0
        mpv_get_property(ctx, "track-list/count", MPV_FORMAT_INT64, &count)
        return Int32(count)
    }

    override func getTrackType(index: Int32) -> String? {
        getPropertyString("track-list/\(index)/type")
    }

    override func getTrackId(index: Int32) -> Int32 {
        guard let ctx = mpv else { return 0 }
        var id: Int64 = 0
        mpv_get_property(ctx, "track-list/\(index)/id", MPV_FORMAT_INT64, &id)
        return Int32(id)
    }

    override func getTrackLang(index: Int32) -> String? {
        getPropertyString("track-list/\(index)/lang")
    }

    override func getTrackTitle(index: Int32) -> String? {
        getPropertyString("track-list/\(index)/title")
    }

    override func isTrackSelected(index: Int32) -> Bool {
        guard let ctx = mpv else { return false }
        var flag: Int32 = 0
        mpv_get_property(ctx, "track-list/\(index)/selected", MPV_FORMAT_FLAG, &flag)
        return flag != 0
    }

    override func setAudioTrack(id: Int32) {
        guard let ctx = mpv else { return }
        if id < 0 {
            mpv_set_property_string(ctx, "aid", "no")
        } else {
            var v = Int64(id)
            mpv_set_property(ctx, "aid", MPV_FORMAT_INT64, &v)
        }
    }

    override func setSubtitleTrack(id: Int32) {
        guard let ctx = mpv else { return }
        if id < 0 {
            mpv_set_property_string(ctx, "sid", "no")
        } else {
            var v = Int64(id)
            mpv_set_property(ctx, "sid", MPV_FORMAT_INT64, &v)
        }
    }

    override func addSubtitleFile(path: String) {
        command(["sub-add", path, "cached"])
    }

    // MARK: - Chapters

    override func getChapterCount() -> Int32 {
        guard let ctx = mpv else { return 0 }
        var count: Int64 = 0
        mpv_get_property(ctx, "chapter-list/count", MPV_FORMAT_INT64, &count)
        return Int32(count)
    }

    override func getChapterTitle(index: Int32) -> String? {
        getPropertyString("chapter-list/\(index)/title")
    }

    override func getChapterTime(index: Int32) -> Double {
        guard let ctx = mpv else { return 0 }
        var t: Double = 0
        mpv_get_property(ctx, "chapter-list/\(index)/time", MPV_FORMAT_DOUBLE, &t)
        return t
    }

    override func setChapter(index: Int32) {
        guard let ctx = mpv else { return }
        var idx = Int64(index)
        mpv_set_property(ctx, "chapter", MPV_FORMAT_INT64, &idx)
    }

    // MARK: - Aspect / panscan

    override func getAspectOverride() -> String? {
        getPropertyString("video-aspect-override")
    }

    override func setAspectOverride(aspect: String) {
        guard let ctx = mpv else { return }
        mpv_set_property_string(ctx, "video-aspect-override", aspect)
    }

    override func getPanscan() -> Double {
        guard let ctx = mpv else { return 0 }
        var v: Double = 0
        mpv_get_property(ctx, "panscan", MPV_FORMAT_DOUBLE, &v)
        return v
    }

    override func setPanscan(value: Double) {
        guard let ctx = mpv else { return }
        var v = value
        mpv_set_property(ctx, "panscan", MPV_FORMAT_DOUBLE, &v)
    }

    // MARK: - Subtitles

    override func setSubScale(scale: Double) {
        guard let ctx = mpv else { return }
        var s = scale
        mpv_set_property(ctx, "sub-scale", MPV_FORMAT_DOUBLE, &s)
    }

    // MARK: - Volume
    // mpv's `volume` property is a Double (0.0 – 100.0+). The previous impl used
    // INT64 here, which silently no-op'd on every read/write.

    override func getVolume() -> Int32 {
        guard let ctx = mpv else { return 0 }
        var vol: Double = 0
        mpv_get_property(ctx, "volume", MPV_FORMAT_DOUBLE, &vol)
        return Int32(vol.rounded())
    }

    override func setVolume(volume: Int32) {
        guard let ctx = mpv else { return }
        var vol = Double(volume)
        mpv_set_property(ctx, "volume", MPV_FORMAT_DOUBLE, &vol)
    }

    override func getMaxVolume() -> Int32 { 100 }

    // MARK: - Helpers

    /// Run an mpv command from a string array. Logs (and returns) the mpv error code.
    /// Without surfacing failures, bad URLs / missing files surface as "the player
    /// shows nothing" with no log entry — debug nightmare.
    @discardableResult
    private func command(_ args: [String]) -> Int32 {
        guard let ctx = mpv else { return -1 }
        var cargs: [UnsafeMutablePointer<CChar>?] = args.map { strdup($0) }
        cargs.append(nil)
        let rc: Int32 = cargs.withUnsafeMutableBufferPointer { buffer in
            buffer.baseAddress?.withMemoryRebound(
                to: UnsafePointer<CChar>?.self,
                capacity: buffer.count
            ) { ptr in
                mpv_command(ctx, ptr)
            } ?? -1
        }
        for ptr in cargs { free(ptr) }
        if rc < 0 {
            print("[MPVKit] command \(args) failed: \(String(cString: mpv_error_string(rc)))")
        }
        return rc
    }

    private func getPropertyString(_ name: String) -> String? {
        guard let ctx = mpv else { return nil }
        guard let cstr = mpv_get_property_string(ctx, name) else { return nil }
        let str = String(cString: cstr)
        mpv_free(cstr)
        return str
    }

    /// Log an mpv return code if it's an error. Used during option/initialize setup
    /// so failures aren't silent.
    @discardableResult
    private func check(_ status: Int32) -> Int32 {
        if status < 0 {
            print("[MPVKit] mpv error: \(String(cString: mpv_error_string(status)))")
        }
        return status
    }
}
