package app.player.vlc

import app.utils.loggy
import cocoapods.VLCKit.VLCMedia
import cocoapods.VLCKit.VLCMediaPlayer
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
import platform.Foundation.NSOrderedSame
import platform.UIKit.UIApplicationDidBecomeActiveNotification

/**
 * Keeping iOS audio and the render surface alive across system events: interruptions (Siri, a
 * call, an alarm), route changes (headphones in and out) and coming back to the foreground.
 *
 * These app-level recovery requests share a cancellable job so delayed native commands
 * cannot overwrite a later playback command or act on replacement media.
 */

/**
 * Preconfigures the shared AVAudioSession before VLC's audio output exists.
 *
 * VLC's Apple audio outputs also configure `.playback` with `.moviePlayback`, activate
 * the session, and handle interruption/route notifications. This early app setup uses
 * the same category and mode for video playback and PiP.
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
 * only re-activate the session. The pause/play cycle is an empirical workaround for
 * silent playback after an interruption, not an API requirement for every audio route.
 */
internal fun VlcKitImpl.registerAudioSessionObservers() {
    val center = NSNotificationCenter.defaultCenter
    val queue = NSOperationQueue.mainQueue
    // Only an artificial pause started by our audio recovery can create this intent.
    // Keep it local to these observers so it expires with this player's registration.
    var audioPauseIntent: AudioRecoveryPauseIntent? = null
    var interruptionRevision: Long? = null

    interruptionObserver = center.addObserverForName(
        name = AVAudioSessionInterruptionNotification,
        `object` = null,
        queue = queue
    ) { note ->
        if (!isInitialized) return@addObserverForName
        val info = note?.userInfo ?: return@addObserverForName
        val type = (info[AVAudioSessionInterruptionTypeKey] as? NSNumber)?.unsignedLongValue
            ?: return@addObserverForName

        when (type) {
            AVAudioSessionInterruptionTypeBegan -> {
                // A new interruption supersedes any pending repaint or audio recovery.
                val player = vlcPlayer
                val media = player?.media
                val wasPriming = recoveryJob?.isActive == true && primingFirstFrame
                val interruptedAudioPause = audioPauseIntent?.takeIf { it.matches(this) }
                supersedeRecovery()
                interruptionRevision = commandRevision
                // The canceled 50 ms job must not leave formerly playing media paused.
                // Bind its intent to this interruption's revision; later user commands
                // still invalidate it, and the old job cannot clear this copied token.
                audioPauseIntent = interruptedAudioPause?.copy(
                    revision = commandRevision,
                    interrupted = true
                )
                if (wasPriming && isInitialized && player != null && media != null &&
                    vlcPlayer === player && player.media?.compare(media) == NSOrderedSame
                ) {
                    // The canceled repaint was temporarily playing an intentionally
                    // paused room. Restore that pause and hide any queued Playing event
                    // until Paused arrives; interruption recovery must not resume it.
                    primingFirstFrame = true
                    player.pause()
                }
            }
            AVAudioSessionInterruptionTypeEnded -> {
                // Native pause/play commands are queued: isPlaying alone can still show
                // the old state after an explicit command during the interruption.
                val canRecoverInterruption = interruptionRevision == commandRevision
                interruptionRevision = null
                val options = (info[AVAudioSessionInterruptionOptionKey] as? NSNumber)
                    ?.unsignedLongValue ?: 0uL
                val shouldResume = (options and AVAudioSessionInterruptionOptionShouldResume) != 0uL

                try {
                    AVAudioSession.sharedInstance().setActive(true, error = null)
                } catch (e: Exception) {
                    loggy("AVAudioSession re-activate failed: ${e.message}")
                }

                val interruptedAudioPause = audioPauseIntent?.takeIf { it.interrupted }
                if (interruptedAudioPause != null) audioPauseIntent = null
                if (shouldResume && canRecoverInterruption) {
                    if (interruptedAudioPause?.matches(this) == true) {
                        // Settle only our canceled temporary pause. This is not permission
                        // to resume arbitrary paused media after a system interruption.
                        supersedeRecovery()
                        primingFirstFrame = false
                        interruptedAudioPause.player.play()
                    } else {
                        // Otherwise only nudge a player that still reports playing.
                        requestAudioRecovery(
                            onTemporaryPause = { audioPauseIntent = it },
                            onFinished = {
                                if (audioPauseIntent === it) audioPauseIntent = null
                            }
                        )
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
        if (!isInitialized) return@addObserverForName
        try {
            AVAudioSession.sharedInstance().setActive(true, error = null)
        } catch (_: Exception) { }
    }

    // Blank frames have been observed after foregrounding. Reassert the drawable and
    // briefly prime a paused player as an empirical recovery; assigning drawable alone
    // changes native output configuration and does not guarantee a new render view.
    didBecomeActiveObserver = center.addObserverForName(
        name = UIApplicationDidBecomeActiveNotification,
        `object` = null,
        queue = queue
    ) { _ ->
        if (!isInitialized) return@addObserverForName
        requestDrawableRecovery()
    }
}

/**
 * Requests the existing foreground/PiP repaint workaround on the main thread. A paused
 * player briefly plays to produce frames, then returns to pause if the media and playback
 * command are still current. Duplicate recovery requests share the in-flight job.
 */
internal fun VlcKitImpl.requestDrawableRecovery() {
    if (!isInitialized || recoveryJob?.isActive == true) return
    val player = vlcPlayer ?: return
    val drawable = vlcDrawable ?: return
    val media = player.media ?: return
    val revision = commandRevision
    recoveryJob = playerScopeMain.launch {
        try {
            if (!isInitialized || vlcPlayer !== player || player.media?.compare(media) != NSOrderedSame ||
                vlcDrawable !== drawable || commandRevision != revision
            ) return@launch

            val wasPlaying = player.isPlaying()
            player.drawable = null
            player.drawable = drawable
            if (!wasPlaying) {
                // Hide this temporary Playing state from room synchronization. Keep the
                // flag until the Paused event arrives: VLCKit's pause() queues native work.
                primingFirstFrame = true
                player.play()
                delay(400)
                if (!isInitialized || vlcPlayer !== player || player.media?.compare(media) != NSOrderedSame ||
                    vlcDrawable !== drawable || commandRevision != revision
                ) return@launch
                player.pause()
            }
        } finally {
            if (commandRevision == revision) recoveryJob = null
        }
    }
}

private data class AudioRecoveryPauseIntent(
    val player: VLCMediaPlayer,
    val media: VLCMedia,
    val revision: Long,
    val interrupted: Boolean = false
) {
    fun matches(impl: VlcKitImpl): Boolean =
        impl.isInitialized && impl.vlcPlayer === player && player.media?.compare(media) == NSOrderedSame &&
            impl.commandRevision == revision
}

private fun VlcKitImpl.requestAudioRecovery(
    onTemporaryPause: (AudioRecoveryPauseIntent) -> Unit,
    onFinished: (AudioRecoveryPauseIntent) -> Unit
) {
    if (!isInitialized || recoveryJob?.isActive == true) return
    val player = vlcPlayer ?: return
    val media = player.media ?: return
    val drawable = vlcDrawable
    if (!player.isPlaying() || primingFirstFrame) return
    val revision = commandRevision
    val pauseIntent = AudioRecoveryPauseIntent(player, media, revision)
    recoveryJob = playerScopeMain.launch {
        try {
            if (!isInitialized || vlcPlayer !== player || player.media?.compare(media) != NSOrderedSame ||
                vlcDrawable !== drawable || commandRevision != revision ||
                !player.isPlaying() || primingFirstFrame
            ) return@launch

            onTemporaryPause(pauseIntent)
            player.pause()
            delay(50)
            if (!isInitialized || vlcPlayer !== player || player.media?.compare(media) != NSOrderedSame ||
                vlcDrawable !== drawable || commandRevision != revision
            ) return@launch
            player.play()
        } finally {
            onFinished(pauseIntent)
            if (commandRevision == revision) recoveryJob = null
        }
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
