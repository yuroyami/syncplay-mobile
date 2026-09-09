package app.player.vlc

import app.player.Playback
import cocoapods.VLCKit.SyncplayVlcCurrentTimeMs
import cocoapods.VLCKit.VLCDrawableProtocol
import cocoapods.VLCKit.VLCPictureInPictureDrawableProtocol
import cocoapods.VLCKit.VLCPictureInPictureMediaControllingProtocol
import cocoapods.VLCKit.VLCPictureInPictureWindowControllingProtocol
import kotlinx.cinterop.CValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import platform.CoreGraphics.CGRect
import platform.Foundation.NSOrderedSame
import platform.UIKit.UIView
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Bridge object that wires a [UIView] up as a VLCKit 4 video output AND a Picture-in-Picture
 * source.
 *
 * VLCKit 4 introduced an opt-in PiP protocol stack: any object set as
 * `VLCMediaPlayer.drawable` that conforms to [VLCPictureInPictureDrawableProtocol] (in addition
 * to the basic [VLCDrawableProtocol]) is offered a [VLCPictureInPictureWindowControllingProtocol]
 * controller via the [pictureInPictureReady] block once the framework is ready. The same object
 * also acts as the [VLCPictureInPictureMediaControllingProtocol] delegate, providing playback
 * commands and metadata to the system's PiP overlay.
 *
 * We can't simply make a [UIView] subclass conform — `addSubview:` on UIView already exists with
 * incompatible Kotlin/Native overload semantics, so we wrap a plain UIView (`containerView`) and
 * forward into it. The VLCMediaPlayer drawable is THIS object, not the underlying view.
 *
 * The Apple PiP integration invokes its synchronous media getters on Main. Commands explicitly
 * enter the player's Main scope, while mediaTime uses the adapter's native clock and seek guard.
 * It must not return VLCKit's independently cached `time` property.
 *
 * @param containerView The UIView VLCKit will render into via [addSubview]. We forward
 *                      [VLCDrawableProtocol]'s addSubview/bounds calls to this view.
 * @param impl The [VlcKitImpl] this drawable is bound to. We hold it strongly because the
 *             drawable's lifetime is bracketed by the impl's: [VlcKitImpl.destroy] clears the
 *             player's drawable reference before we get released.
 */
internal class VlcDrawable(
    private val containerView: UIView,
    private val impl: VlcKitImpl,
) : NSObject(),
    VLCDrawableProtocol,
    VLCPictureInPictureDrawableProtocol,
    VLCPictureInPictureMediaControllingProtocol {

    private var disposed = false
    private var pendingSeek: VlcSeekCompletion? = null
    private var pendingSeekJob: Job? = null

    private fun isCurrentDrawable(): Boolean = !disposed && impl.vlcDrawable === this

    /** Break the native PiP controller -> drawable -> controller retain cycle on Main. */
    fun dispose() {
        if (disposed) return
        disposed = true
        finishPendingSeek(VlcSeekCompletion.Result.CANCELLED)
        onPipStateChanged = null
        val controller = pipController
        pipController = null
        controller?.setStateChangeEventHandler(null)
        controller?.stopPictureInPicture()
    }

    private fun finishPendingSeek(result: VlcSeekCompletion.Result) {
        val job = pendingSeekJob
        val completion = pendingSeek
        pendingSeekJob = null
        pendingSeek = null
        job?.cancel()
        completion?.finish(result)
    }

    /**
     * The PiP window controller VLCKit hands us via [pictureInPictureReady]. Becomes non-null
     * once the framework finishes setting up its PiP machinery (typically shortly after the
     * first video frame is rendered). Used to start/stop PiP and to invalidate playback state
     * so the system overlay's pause/play button stays in sync with our [VLCMediaPlayer].
     */
    var pipController: VLCPictureInPictureWindowControllingProtocol? = null
        private set

    /**
     * Notified by VLCKit when PiP starts (`true`) or stops (`false`). Wired up to the
     * RoomUiStateManager's `hasEnteredPipMode` flow so the room UI can hide its HUD while the
     * floating window is up.
     */
    var onPipStateChanged: ((Boolean) -> Unit)? = null

    // ────────────────────────────────────────────────────────────────────────
    // VLCDrawable
    // ────────────────────────────────────────────────────────────────────────

    // K/N's generated VLCDrawableProtocol exposes the view parameter as nullable
    // (`UIView?`) — the override signature must match exactly. We just no-op on null,
    // since VLCKit will never actually pass nil here in practice.
    override fun addSubview(view: UIView?) {
        if (view == null || !isCurrentDrawable()) return
        // A newly created native output can attach another window here. The container
        // belongs exclusively to VLC. Drawable assignment alone does not promise a new view.
        containerView.subviews.forEach { (it as? UIView)?.removeFromSuperview() }
        // Size the render view to fill the container, and pin it there with an
        // autoresizing mask so subsequent rotations / layout passes keep it stretched.
        // Without this, VLCKit's render view sometimes lands at zero-size (especially
        // on first attach when containerView's bounds haven't fully laid out yet) and
        // we see the container's background bleed through instead of video.
        view.setFrame(containerView.bounds)
        view.setAutoresizingMask(
            UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
        )
        containerView.addSubview(view)
    }

    override fun bounds(): CValue<CGRect> = containerView.bounds

    // ────────────────────────────────────────────────────────────────────────
    // VLCPictureInPictureDrawable
    // ────────────────────────────────────────────────────────────────────────

    override fun mediaController(): VLCPictureInPictureMediaControllingProtocol = this

    override fun pictureInPictureReady(): (VLCPictureInPictureWindowControllingProtocol?) -> Unit =
        { controller ->
            if (isCurrentDrawable()) {
                if (pipController !== controller) pipController?.setStateChangeEventHandler(null)
                pipController = controller
                controller?.setStateChangeEventHandler { isStarted ->
                    if (isCurrentDrawable() && pipController === controller) {
                        onPipStateChanged?.invoke(isStarted)
                    }
                }
            }
        }

    // ────────────────────────────────────────────────────────────────────────
    // VLCPictureInPictureMediaControlling
    //
    // VLCKit invokes these from the system's PiP overlay (its play/pause/seek buttons). They go
    // through the room dispatcher, not straight to the engine: a PiP window is still a person in
    // a room, so a play has to pass the readiness gate and a seek has to be announced. Driving
    // the engine directly moved this viewer alone and told nobody.
    // ────────────────────────────────────────────────────────────────────────

    override fun play() {
        impl.playerScopeMain.launch(Dispatchers.Main.immediate) {
            if (!isCurrentDrawable() || !impl.isInitialized) return@launch
            impl.viewmodel.dispatcher.controlPlayback(Playback.PLAY, tellServer = true)
        }
    }

    override fun pause() {
        impl.playerScopeMain.launch(Dispatchers.Main.immediate) {
            if (!isCurrentDrawable() || !impl.isInitialized) return@launch
            impl.viewmodel.dispatcher.controlPlayback(Playback.PAUSE, tellServer = true)
        }
    }

    override fun seekBy(offset: Long, completion: (() -> Unit)?) {
        finishPendingSeek(VlcSeekCompletion.Result.SUPERSEDED)
        lateinit var request: VlcSeekCompletion
        request = VlcSeekCompletion {
            if (pendingSeek === request) pendingSeek = null
            completion?.invoke()
        }
        pendingSeek = request
        val player = impl.vlcPlayer
        val media = player?.media
        if (!isCurrentDrawable() || !impl.isInitialized || player == null || media == null) {
            request.finish(VlcSeekCompletion.Result.UNAVAILABLE)
            return
        }

        val job = impl.playerScopeMain.launch {
            try {
                if (!isCurrentDrawable() || !impl.isInitialized || impl.vlcPlayer !== player ||
                    player.media?.compare(media) != NSOrderedSame
                ) {
                    request.finish(VlcSeekCompletion.Result.UNAVAILABLE)
                    return@launch
                }
                // Preserve millisecond offsets and await actual local submission, not a
                // dispatcher coroutine merely being scheduled. Native completion is separate.
                if (impl.viewmodel.dispatcher.seekByMillis(offset) == null) {
                    request.finish(VlcSeekCompletion.Result.UNAVAILABLE)
                    return@launch
                }
                val seekRevision = impl.seekRevision
                val commandRevision = impl.commandRevision
                request.await(
                    isCurrent = {
                        isCurrentDrawable() && impl.isInitialized && impl.vlcPlayer === player &&
                            player.media?.compare(media) == NSOrderedSame &&
                            impl.seekRevision == seekRevision && impl.commandRevision == commandRevision
                    },
                    targetMs = { impl.lastSeekRequestTargetMs },
                    nativePositionMs = {
                        // An old input's coincidentally close clock is not completion of
                        // a command still waiting for startup. Timeout only releases PiP's
                        // callback; it does not cancel the room's deferred seek intent.
                        if (impl.hasPendingSeek) null
                        else SyncplayVlcCurrentTimeMs(player).takeIf { it >= 0L }
                    }
                )
            } finally {
                request.finish(VlcSeekCompletion.Result.CANCELLED)
            }
        }
        pendingSeekJob = job
        // The scope can be canceled before the body starts, so its finally alone is insufficient.
        job.invokeOnCompletion {
            dispatch_async(dispatch_get_main_queue()) {
                request.finish(VlcSeekCompletion.Result.CANCELLED)
                if (pendingSeekJob === job) pendingSeekJob = null
            }
        }
    }

    override fun mediaLength(): Long =
        if (isCurrentDrawable()) impl.playerManager.timeFullMillis.value.coerceAtLeast(0L) else 0L

    override fun mediaTime(): Long =
        if (isCurrentDrawable()) impl.currentPositionMs() else 0L

    override fun isMediaSeekable(): Boolean =
        isCurrentDrawable() && impl.vlcPlayer?.isSeekable() == true

    override fun isMediaPlaying(): Boolean =
        isCurrentDrawable() && impl.vlcPlayer?.isPlaying() == true
}
