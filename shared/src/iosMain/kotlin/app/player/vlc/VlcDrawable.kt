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
 * Makes a [UIView] both a VLCKit 4 video output and a Picture-in-Picture (PiP) source.
 *
 * VLCKit 4 has an opt-in PiP protocol set. When the object set as `VLCMediaPlayer.drawable` also
 * conforms to [VLCPictureInPictureDrawableProtocol] (on top of the basic [VLCDrawableProtocol]),
 * VLCKit passes it a [VLCPictureInPictureWindowControllingProtocol] controller through the
 * [pictureInPictureReady] block once PiP is ready. The same object is also the
 * [VLCPictureInPictureMediaControllingProtocol] delegate, which gives the system PiP overlay its
 * playback commands and media info.
 *
 * A [UIView] subclass cannot conform directly: UIView already has `addSubview:`, and its
 * Kotlin/Native overload does not match the protocol's. So this class wraps a plain UIView
 * (`containerView`) and forwards to it. The VLCMediaPlayer drawable is this object, not the view.
 *
 * Apple's PiP calls the synchronous media getters on the main thread. The commands enter the
 * player's main scope explicitly. [mediaTime] reads [VlcKitImpl]'s native clock through its seek
 * guard, and must never return VLCKit's separately cached `time` property.
 *
 * @param containerView The UIView that VLCKit renders into. [addSubview] and [bounds] forward to
 *   this view.
 * @param impl The [VlcKitImpl] this drawable belongs to. The reference is strong because the
 *   drawable never outlives the impl: [VlcKitImpl.destroy] clears the player's drawable reference
 *   before this object is released.
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

    /**
     * Breaks the native retain cycle (PiP controller -> drawable -> controller) on the main
     * thread. It also cancels a pending PiP seek and stops PiP.
     */
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
     * The PiP window controller that VLCKit passes to [pictureInPictureReady]. It becomes
     * non-null once VLCKit has set up PiP (usually shortly after the first video frame).
     * [VlcKitImpl] uses it to start and stop PiP, and to invalidate the playback state so the
     * overlay's play and pause button matches the [VLCMediaPlayer].
     */
    var pipController: VLCPictureInPictureWindowControllingProtocol? = null
        private set

    /**
     * Called when PiP starts (`true`) or stops (`false`). [VlcKitImpl] connects it to
     * `hasEnteredPipMode` in RoomUiStateManager, so the room UI (the screen of a group of people
     * watching together) can hide its HUD while the PiP window is up.
     */
    var onPipStateChanged: ((Boolean) -> Unit)? = null

    // ────────────────────────────────────────────────────────────────────────
    // VLCDrawable
    // ────────────────────────────────────────────────────────────────────────

    // Kotlin/Native's generated VLCDrawableProtocol makes the view parameter nullable
    // (`UIView?`), and the override signature must match exactly. VLCKit never passes nil
    // here in practice, so null is ignored.
    override fun addSubview(view: UIView?) {
        if (view == null || !isCurrentDrawable()) return
        // A newly created native output can attach another view here. The container belongs
        // to VLC alone, so old views go first. Setting the drawable alone does not promise a
        // new view.
        containerView.subviews.forEach { (it as? UIView)?.removeFromSuperview() }
        // Size the render view to fill the container, and pin it with an autoresizing mask
        // so later rotations and layout passes keep it filled. Without this, VLCKit's render
        // view can end up zero-sized (mostly on the first attach, before containerView's
        // bounds are laid out), and the container's background shows instead of the video.
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
    // VLCKit calls these from the system PiP overlay (its play, pause and seek buttons). They go
    // through the room dispatcher, not straight to the engine. A viewer in PiP is still in the
    // room, so a play must pass the readiness gate and a seek must be announced. Driving the
    // engine directly would move only this viewer and tell nobody.
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
                // Keep the millisecond offset, and wait until the seek is really submitted
                // locally, not only until a dispatcher coroutine is scheduled. Native completion
                // is awaited separately below.
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
                        // While a seek still waits for startup, the old input's clock can be
                        // near the target by chance, and that is not completion. A timeout only
                        // releases PiP's callback. It does not cancel the room's deferred seek.
                        if (impl.hasPendingSeek) null
                        else SyncplayVlcCurrentTimeMs(player).takeIf { it >= 0L }
                    }
                )
            } finally {
                request.finish(VlcSeekCompletion.Result.CANCELLED)
            }
        }
        pendingSeekJob = job
        // The scope can be cancelled before the body starts, so the finally block is not enough.
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
