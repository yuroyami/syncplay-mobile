import Foundation
import UIKit
import Libmpv
import shared

/// Swift implementation of MpvKitPlayerBridge using the raw libmpv C API via MPVKit.
///
/// This class wraps all libmpv operations (create, load, play, seek, track management, etc.)
/// and exposes them through the Kotlin-defined abstract class interface.
///
/// Rendering uses Metal via MoltenVK (Vulkan backend) for hardware-accelerated video output.
///
/// ## Integration
/// Registered with Kotlin at app startup via:
/// ```swift
/// MpvKitBridgeKt.instantiateMpvKitPlayer = { viewmodel in
///     let bridge = MpvKitBridgeImpl()
///     return MpvKitImpl(viewmodel: viewmodel, bridge: bridge)
/// }
/// ```
@MainActor
class MpvKitBridgeImpl: MpvKitPlayerBridge, @unchecked Sendable {

    /// The opaque mpv handle.
    private var mpv: OpaquePointer?

    /// The UIView containing the CAMetalLayer for video rendering.
    private var playerView: UIView?

    /// The Metal layer used by mpv for rendering.
    private var metalLayer: CAMetalLayer?

    /// mpv render context for GPU-based rendering.
    private var renderContext: OpaquePointer?

    /// Dispatch queue for processing mpv events.
    private let eventQueue = DispatchQueue(label: "com.syncplay.mpvkit.events", qos: .userInitiated)

    /// Whether the event loop should keep running.
    private var isRunning = false

    override init() {
        self.mpv = nil
        self.playerView = nil
        self.metalLayer = nil
        self.renderContext = nil
        super.init()
    }

    // MARK: - Lifecycle

    override func create() {
        // 1. Create mpv instance
        guard let ctx = mpv_create() else {
            print("[MPVKit] Failed to create mpv context")
            return
        }
        mpv = ctx

        // 2. Configure mpv options
        mpv_set_option_string(ctx, "vo", "gpu-next")
        mpv_set_option_string(ctx, "gpu-api", "vulkan")
        mpv_set_option_string(ctx, "gpu-context", "moltenvk")
        mpv_set_option_string(ctx, "hwdec", "videotoolbox")
        mpv_set_option_string(ctx, "keep-open", "yes")
        mpv_set_option_string(ctx, "idle", "yes")
        mpv_set_option_string(ctx, "input-default-bindings", "no")
        mpv_set_option_string(ctx, "input-vo-keyboard", "no")

        // 3. Initialize mpv
        if mpv_initialize(ctx) < 0 {
            print("[MPVKit] Failed to initialize mpv")
            mpv_destroy(ctx)
            mpv = nil
            return
        }

        // 4. Set up Metal rendering
        setupMetalRendering(ctx)

        // 5. Observe key properties
        mpv_observe_property(ctx, 0, "time-pos", MPV_FORMAT_DOUBLE)
        mpv_observe_property(ctx, 0, "duration", MPV_FORMAT_DOUBLE)
        mpv_observe_property(ctx, 0, "pause", MPV_FORMAT_FLAG)

        // 6. Start event processing loop
        isRunning = true
        startEventLoop()
    }

    override func destroy() {
        isRunning = false

        if let rc = renderContext {
            mpv_render_context_set_update_callback(rc, nil, nil)
            mpv_render_context_free(rc)
            renderContext = nil
        }

        if let ctx = mpv {
            mpv_destroy(ctx)
            mpv = nil
        }

        metalLayer = nil
        playerView = nil
    }

    override func getPlayerView() -> UIView {
        if let existing = playerView {
            return existing
        }

        let view = UIView()
        view.backgroundColor = .black

        let layer = CAMetalLayer()
        layer.device = MTLCreateSystemDefaultDevice()
        layer.framebufferOnly = true
        layer.pixelFormat = .bgra8Unorm
        layer.contentsScale = UIScreen.main.scale
        view.layer.addSublayer(layer)

        // Auto-resize metal layer with view
        layer.frame = view.bounds
        view.layoutSubviews()

        metalLayer = layer
        playerView = view

        return view
    }

    // MARK: - Metal Rendering Setup

    private func setupMetalRendering(_ ctx: OpaquePointer) {
        guard let layer = metalLayer else {
            print("[MPVKit] Metal layer not available, call getPlayerView() first")
            return
        }

        // Get the MoltenVK surface from the CAMetalLayer
        // mpv with gpu-context=moltenvk needs the CAMetalLayer pointer
        var params: [mpv_render_param] = []

        // For MoltenVK, we pass the CAMetalLayer as the wl_display-equivalent
        let layerPtr = Unmanaged.passUnretained(layer).toOpaque()

        var apiType = MPV_RENDER_API_TYPE_OPENGL
        // Note: For MoltenVK/Vulkan, the render context setup is different.
        // mpv handles VkInstance/VkSurface creation internally when gpu-context=moltenvk
        // We just need to ensure the CAMetalLayer is the view's layer.

        // With gpu-context=moltenvk, mpv will find the Metal layer automatically
        // from the view hierarchy. No explicit render context needed for display.
        // mpv creates its own Vulkan instance and renders to the CAMetalLayer.

        // Set the wakeup callback so mpv can signal us to redraw
        let pointer = Unmanaged.passUnretained(self).toOpaque()
        mpv_set_wakeup_callback(ctx, { (ctx) in
            // This is called from mpv's thread - we don't need to do anything here
            // since we have our own event loop
        }, pointer)
    }

    // MARK: - Event Loop

    private func startEventLoop() {
        eventQueue.async { [weak self] in
            while self?.isRunning == true {
                guard let ctx = self?.mpv else { break }

                let event = mpv_wait_event(ctx, 0.5)
                guard let pointee = event?.pointee else { continue }

                switch pointee.event_id {
                case MPV_EVENT_PROPERTY_CHANGE:
                    guard let prop = pointee.data?.assumingMemoryBound(to: mpv_event_property.self).pointee else { break }
                    let name = String(cString: prop.name)

                    var value: Any? = nil
                    switch prop.format {
                    case MPV_FORMAT_DOUBLE:
                        if let ptr = prop.data {
                            value = ptr.assumingMemoryBound(to: Double.self).pointee
                        }
                    case MPV_FORMAT_FLAG:
                        if let ptr = prop.data {
                            let flag = ptr.assumingMemoryBound(to: Int32.self).pointee
                            value = (flag != 0)
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
                    default:
                        break
                    }

                    DispatchQueue.main.async {
                        self?.onPropertyChange?(name, value)
                    }

                case MPV_EVENT_FILE_LOADED:
                    DispatchQueue.main.async {
                        self?.onEvent?("file-loaded")
                    }

                case MPV_EVENT_END_FILE:
                    DispatchQueue.main.async {
                        self?.onEvent?("end-file")
                    }

                case MPV_EVENT_SHUTDOWN:
                    self?.isRunning = false

                default:
                    break
                }
            }
        }
    }

    // MARK: - Media Loading

    override func loadFile(path: String) {
        guard let ctx = mpv else { return }
        command(["loadfile", path, "replace"])
    }

    override func loadURL(url: String) {
        guard let ctx = mpv else { return }
        command(["loadfile", url, "replace"])
    }

    // MARK: - Playback Control

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

    // MARK: - Track Management

    override func getTrackCount() -> Int32 {
        guard let ctx = mpv else { return 0 }
        var count: Int64 = 0
        mpv_get_property(ctx, "track-list/count", MPV_FORMAT_INT64, &count)
        return Int32(count)
    }

    override func getTrackType(index: Int32) -> String? {
        return getPropertyString("track-list/\(index)/type")
    }

    override func getTrackId(index: Int32) -> Int32 {
        guard let ctx = mpv else { return 0 }
        var id: Int64 = 0
        mpv_get_property(ctx, "track-list/\(index)/id", MPV_FORMAT_INT64, &id)
        return Int32(id)
    }

    override func getTrackLang(index: Int32) -> String? {
        return getPropertyString("track-list/\(index)/lang")
    }

    override func getTrackTitle(index: Int32) -> String? {
        return getPropertyString("track-list/\(index)/title")
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
            var val64 = Int64(id)
            mpv_set_property(ctx, "aid", MPV_FORMAT_INT64, &val64)
        }
    }

    override func setSubtitleTrack(id: Int32) {
        guard let ctx = mpv else { return }
        if id < 0 {
            mpv_set_property_string(ctx, "sid", "no")
        } else {
            var val64 = Int64(id)
            mpv_set_property(ctx, "sid", MPV_FORMAT_INT64, &val64)
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
        return getPropertyString("chapter-list/\(index)/title")
    }

    override func getChapterTime(index: Int32) -> Double {
        guard let ctx = mpv else { return 0 }
        var time: Double = 0
        mpv_get_property(ctx, "chapter-list/\(index)/time", MPV_FORMAT_DOUBLE, &time)
        return time
    }

    override func setChapter(index: Int32) {
        guard let ctx = mpv else { return }
        var idx = Int64(index)
        mpv_set_property(ctx, "chapter", MPV_FORMAT_INT64, &idx)
    }

    // MARK: - Aspect Ratio

    override func getAspectOverride() -> String? {
        return getPropertyString("video-aspect-override")
    }

    override func setAspectOverride(aspect: String) {
        guard let ctx = mpv else { return }
        mpv_set_property_string(ctx, "video-aspect-override", aspect)
    }

    override func getPanscan() -> Double {
        guard let ctx = mpv else { return 0 }
        var val_: Double = 0
        mpv_get_property(ctx, "panscan", MPV_FORMAT_DOUBLE, &val_)
        return val_
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

    override func getVolume() -> Int32 {
        guard let ctx = mpv else { return 0 }
        var vol: Int64 = 0
        mpv_get_property(ctx, "volume", MPV_FORMAT_INT64, &vol)
        return Int32(vol)
    }

    override func setVolume(volume: Int32) {
        guard let ctx = mpv else { return }
        var vol = Int64(volume)
        mpv_set_property(ctx, "volume", MPV_FORMAT_INT64, &vol)
    }

    override func getMaxVolume() -> Int32 {
        return 100
    }

    // MARK: - Helpers

    /// Execute an mpv command from a string array.
    private func command(_ args: [String]) {
        guard let ctx = mpv else { return }
        // Build a null-terminated array of C strings
        var cargs: [UnsafeMutablePointer<CChar>?] = args.map { strdup($0) }
        cargs.append(nil)
        // mpv_command expects UnsafePointer<UnsafePointer<CChar>?> — bridge via withUnsafeBufferPointer
        cargs.withUnsafeMutableBufferPointer { buffer in
            buffer.baseAddress?.withMemoryRebound(to: UnsafePointer<CChar>?.self, capacity: buffer.count) { ptr in
                mpv_command(ctx, ptr)
            }
        }
        for ptr in cargs { free(ptr) }
    }

    /// Get a string property from mpv. Returns nil if unavailable.
    private func getPropertyString(_ name: String) -> String? {
        guard let ctx = mpv else { return nil }
        guard let cstr = mpv_get_property_string(ctx, name) else { return nil }
        let str = String(cString: cstr)
        mpv_free(cstr)
        return str
    }
}
