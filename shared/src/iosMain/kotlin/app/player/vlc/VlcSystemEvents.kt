package app.player.vlc

import app.utils.loggy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionOptionKey
import platform.AVFAudio.AVAudioSessionInterruptionOptionShouldResume
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeEnded
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioSessionModeMoviePlayback
import platform.AVFAudio.AVAudioSessionRouteChangeNotification
import platform.AVFAudio.setActive
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification

/**
 * Keeping iOS audio and the render surface alive across system events: interruptions (Siri, a
 * call, an alarm), route changes (headphones in and out) and coming back to the foreground.
 *
 * None of this is about playing video, which is why it lives beside [VlcKitImpl] rather than
 * inside it. VLCKit configures none of it on our behalf.
 */

/**
 * Configures the shared AVAudioSession for video playback.
 *
 * Must use category `.playback` with mode `.moviePlayback` so audio continues in the
 * background (PiP, lock screen) and mixes correctly with the system. VLCKit does NOT
 * configure this on our behalf — without this call, audio may drop after the first
 * interruption or route change.
 */
internal fun VlcKitImpl.configureAudioSession() {
    try {
        val session = AVAudioSession.sharedInstance()
        // Positional args: K/N's Obj-C interop exposes overloaded `setCategory:*:` /
        // `setActive:*:` variants that share a base name, so named-parameter resolution
        // can fail — positional keeps us on the shortest matching overload.
        session.setCategory(AVAudioSessionCategoryPlayback, AVAudioSessionModeMoviePlayback, 0uL, null)
        session.setActive(true, null)
    } catch (e: Exception) {
        loggy("AVAudioSession configure failed: ${e.message}")
    }
}

/**
 * Registers NSNotificationCenter observers to recover audio after system interruptions
 * (Siri, incoming FaceTime, alarm, any other AVAudioSession grab) and route changes
 * (headphones plugged/unplugged, AirPods reconnect). On interruption-end we re-activate
 * the session and nudge VLC back into sync with a pause/play cycle; on route change we
 * only re-activate the session. This mirrors Apple's "AVAudioSession best practices"
 * sample and is the only reliable way to keep audio playing on VLCKit after Siri.
 */
internal fun VlcKitImpl.registerAudioSessionObservers() {
    val center = NSNotificationCenter.defaultCenter
    val queue = NSOperationQueue.mainQueue

    interruptionObserver = center.addObserverForName(
        name = AVAudioSessionInterruptionNotification,
        `object` = null,
        queue = queue
    ) { note ->
        val info = note?.userInfo ?: return@addObserverForName
        val type = (info[AVAudioSessionInterruptionTypeKey] as? NSNumber)?.unsignedLongValue
            ?: return@addObserverForName

        when (type) {
            AVAudioSessionInterruptionTypeBegan -> {
                // Nothing to do: iOS already paused us. We'll recover on "ended".
            }
            AVAudioSessionInterruptionTypeEnded -> {
                val options = (info[AVAudioSessionInterruptionOptionKey] as? NSNumber)
                    ?.unsignedLongValue ?: 0uL
                val shouldResume = (options and AVAudioSessionInterruptionOptionShouldResume) != 0uL

                try {
                    AVAudioSession.sharedInstance().setActive(true, error = null)
                } catch (e: Exception) {
                    loggy("AVAudioSession re-activate failed: ${e.message}")
                }

                // VLCKit sometimes gets stuck with a silent audio pipeline even though its
                // state says "playing". Toggling pause/play rewires the audio graph.
                if (shouldResume) {
                    playerScopeMain.launch(Dispatchers.Main.immediate) {
                        val wasPlaying = vlcPlayer?.isPlaying() == true
                        vlcPlayer?.pause()
                        delay(50)
                        if (wasPlaying) vlcPlayer?.play()
                    }
                }
            }
        }
    }

    routeChangeObserver = center.addObserverForName(
        name = AVAudioSessionRouteChangeNotification,
        `object` = null,
        queue = queue
    ) { _ ->
        try {
            AVAudioSession.sharedInstance().setActive(true, error = null)
        } catch (_: Exception) { }
    }

    // After backgrounding (app switch OR a screen lock/unlock with the player open),
    // VLCKit 4's render layer hosted on our drawable's containerView is sometimes left
    // disconnected — the app comes back showing a blank frame instead of resuming
    // video. [rebindDrawableAndRepaint] drops the stale render UIView and forces a
    // fresh one (plus a frame when paused). We listen on DidBecomeActive (rather than
    // WillEnterForeground) so the layout pass has settled and the containerView's
    // bounds are valid by the time we re-bind.
    didBecomeActiveObserver = center.addObserverForName(
        name = UIApplicationDidBecomeActiveNotification,
        `object` = null,
        queue = queue
    ) { _ ->
        playerScopeMain.launch { rebindDrawableAndRepaint() }
    }
}

/**
 * Re-attaches the render surface to [vlcDrawable] and, when paused, decodes a single
 * frame so the freshly-attached render view actually shows the current frame instead
 * of staying blank/black. Shared by the foreground-recovery (DidBecomeActive) and
 * PiP-stop recovery paths. Must run on the main thread (callers wrap in
 * `playerScopeMain.launch`).
 *
 * **Why the extra frame when paused:** setting `drawable` makes VLCKit hand us a brand
 * new (empty) render UIView via [VlcDrawable.addSubview]. VLC only paints into it when
 * it produces a frame. While playing, the next decoded frame fills it within ~1 frame,
 * so the rebind alone is enough. While PAUSED — the usual state when someone locks
 * their phone mid-session — no new frame is ever produced, so the view would stay
 * black until the user seeks/plays. [VLCMediaPlayer.gotoNextFrame] decodes and presents
 * exactly one frame (and leaves the player paused), repainting the recovered surface.
 * The ~1-frame advance (~20-40 ms) is far below the protocol's 1 s seek threshold, so
 * the room can't mistake it for a user seek.
 */
internal suspend fun VlcKitImpl.rebindDrawableAndRepaint() {
    val player = vlcPlayer ?: return
    val drawable = vlcDrawable ?: return
    // Skip if there's no media — nothing to render anyway, and rebinding an empty
    // player can confuse VLCKit's PiP setup.
    if (player.media == null) return
    player.drawable = null
    player.drawable = drawable
    if (!player.isPlaying()) {
        // iOS often tears down libvlc's video output (vout) along with the GPU surface
        // while backgrounded. A single gotoNextFrame() can NOT rebuild a destroyed vout;
        // only a real play() transition stands the decoder->vout pipeline back up. So
        // briefly play to force vout re-creation, wait for playback to repaint, then
        // restore the paused frame. primingFirstFrame swallows the transient Playing so
        // the room never sees a phantom "unpaused" broadcast.
        primingFirstFrame = true
        player.play()
        // Give libvlc time to rebuild the vout via the real play() transition (a single
        // gotoNextFrame can't recreate a vout iOS destroyed while backgrounded). ~400 ms
        // of actual playback reliably stands the decoder->vout pipeline back up; the tiny
        // advance is far under the 1 s seek threshold so the room can't see it as a seek.
        delay(400)
        val p = vlcPlayer
        if (p == null) {
            primingFirstFrame = false
            return
        }
        p.pause()
        if (p.media != null && !p.isPlaying()) p.gotoNextFrame()
    }
}

internal fun VlcKitImpl.removeAudioSessionObservers() {
    val center = NSNotificationCenter.defaultCenter
    interruptionObserver?.let { center.removeObserver(it) }
    routeChangeObserver?.let { center.removeObserver(it) }
    didBecomeActiveObserver?.let { center.removeObserver(it) }
    interruptionObserver = null
    routeChangeObserver = null
    didBecomeActiveObserver = null
}
