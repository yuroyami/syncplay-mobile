package app.player.vlc

import cocoapods.VLCKit.VLCDrawableProtocol
import cocoapods.VLCKit.VLCPictureInPictureDrawableProtocol
import cocoapods.VLCKit.VLCPictureInPictureMediaControllingProtocol
import cocoapods.VLCKit.VLCPictureInPictureWindowControllingProtocol
import kotlinx.cinterop.CValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import platform.CoreGraphics.CGRect
import platform.UIKit.UIView
import platform.darwin.NSObject

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
 * The media-controlling callbacks are invoked by VLCKit on whichever thread it pleases, so we
 * always hop to the main thread for any actual VLC API calls and read state via fast snapshot
 * lookups (length / time / isPlaying are cheap, main-thread-safe getters in VLCKit 4).
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
        if (view != null) containerView.addSubview(view)
    }

    override fun bounds(): CValue<CGRect> = containerView.bounds

    // ────────────────────────────────────────────────────────────────────────
    // VLCPictureInPictureDrawable
    // ────────────────────────────────────────────────────────────────────────

    override fun mediaController(): VLCPictureInPictureMediaControllingProtocol = this

    override fun pictureInPictureReady(): (VLCPictureInPictureWindowControllingProtocol?) -> Unit =
        { controller ->
            pipController = controller
            controller?.setStateChangeEventHandler { isStarted ->
                onPipStateChanged?.invoke(isStarted)
            }
        }

    // ────────────────────────────────────────────────────────────────────────
    // VLCPictureInPictureMediaControlling
    //
    // VLCKit invokes these from the system's PiP overlay (its play/pause/seek buttons). We
    // route playback commands through VlcKitImpl's main-thread-suspending wrappers — they
    // already serialize against our own UI-driven calls and update [PlayerManager] state.
    // ────────────────────────────────────────────────────────────────────────

    override fun play() {
        impl.playerScopeMain.launch(Dispatchers.Main.immediate) { impl.play() }
    }

    override fun pause() {
        impl.playerScopeMain.launch(Dispatchers.Main.immediate) { impl.pause() }
    }

    override fun seekBy(offset: Long, completion: (() -> Unit)?) {
        // Match the official VLCKit PiP example: forward the offset (in ms) to libvlc's native
        // jumpWithOffset, which handles its own seek + completion-on-main-thread dispatch.
        // Both `completion` parameters are nullable, so we can pass it through unchanged.
        impl.vlcPlayer?.jumpWithOffset(offset.toInt(), completion = completion)
    }

    override fun mediaLength(): Long =
        impl.vlcMedia?.length?.value()?.longValue ?: 0L

    override fun mediaTime(): Long =
        impl.vlcPlayer?.time?.value()?.longValue ?: 0L

    override fun isMediaSeekable(): Boolean =
        impl.vlcPlayer?.isSeekable() == true

    override fun isMediaPlaying(): Boolean =
        impl.vlcPlayer?.isPlaying() == true
}
