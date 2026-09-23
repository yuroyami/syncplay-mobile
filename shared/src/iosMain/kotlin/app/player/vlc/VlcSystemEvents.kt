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
 * Keeps iOS audio and the VLC render surface working across system events: interruptions (Siri,
 * a call, an alarm), route changes (headphones in and out) and the return to the foreground.
 *
 * These recovery requests share one cancellable job (`recoveryJob`), so a delayed native command
 * cannot override a later playback command or act on replaced media.
 */

/**
 * Sets up the shared AVAudioSession before VLC's audio output exists.
 *
 * VLC's Apple audio outputs also set `.playback` with `.moviePlayback`, activate the session,
 * and handle interruption and route notifications. This early setup uses the same category and
 * mode, for video playback and PiP.
 */
internal fun VlcKitImpl.configureAudioSession() {
    try {
        val session = AVAudioSession.sharedInstance()
        // Positional arguments on purpose. Kotlin/Native's Objective-C interop exposes several
        // `setCategory:*:` and `setActive:*:` overloads with the same base name, so named
        // arguments can fail to resolve. Positional ones pick the shortest matching overload.
        session.setCategory(AVAudioSessionCategoryPlayback, AVAudioSessionModeMoviePlayback, 0uL, null)
        session.setActive(true, null)
    } catch (e: Exception) {
        loggy("AVAudioSession configure failed: ${e.message}")
    }
}

/**
 * Registers NSNotificationCenter observers that recover audio after system interruptions (Siri,
 * a FaceTime call, an alarm, any other app that takes the AVAudioSession) and route changes
 * (headphones in or out, AirPods reconnecting). It also registers the foreground observer that
 * repaints the video.
 *
 * When an interruption ends, the session is activated again. If the system allows a resume and
 * no newer command arrived, a player that still reports playing gets a short pause and play.
 * A route change only activates the session again. The pause and play is a workaround, found by
 * testing, for silent playback after an interruption. No API asks for it.
 */
internal fun VlcKitImpl.registerAudioSessionObservers() {
    val center = NSNotificationCenter.defaultCenter
    val queue = NSOperationQueue.mainQueue
    // Only the temporary pause of the audio recovery below creates this intent. It lives
    // inside these observers, so it ends with this player's registration.
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
                // A new interruption cancels any pending repaint or audio recovery.
                val player = vlcPlayer
                val media = player?.media
                val wasPriming = recoveryJob?.isActive == true && primingFirstFrame
                val interruptedAudioPause = audioPauseIntent?.takeIf { it.matches(this) }
                supersedeRecovery()
                interruptionRevision = commandRevision
                // The cancelled 50 ms job must not leave media paused that was playing before.
                // Bind its intent to this interruption's revision. A later user command still
                // invalidates the intent, and the old job cannot clear this copy.
                audioPauseIntent = interruptedAudioPause?.copy(
                    revision = commandRevision,
                    interrupted = true
                )
                if (wasPriming && isInitialized && player != null && media != null &&
                    vlcPlayer === player && player.media?.compare(media) == NSOrderedSame
                ) {
                    // The cancelled repaint was briefly playing a room that is paused on
                    // purpose. Restore the pause, and hide any queued Playing event until
                    // Paused arrives. Interruption recovery must not resume this media.
                    primingFirstFrame = true
                    player.pause()
                }
            }
            AVAudioSessionInterruptionTypeEnded -> {
                // Native pause and play commands are queued, so isPlaying can still show the
                // old state after a command sent during the interruption. Compare revisions.
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
                        // Undo only the recovery's own temporary pause, whose job the
                        // interruption cancelled. This does not allow resuming other paused
                        // media after an interruption.
                        supersedeRecovery()
                        primingFirstFrame = false
                        interruptedAudioPause.player.play()
                    } else {
                        // Otherwise run the pause and play recovery, which only acts on a
                        // player that still reports playing.
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

    // The video can show blank frames after the app returns to the foreground. The workaround,
    // found by testing: set the drawable again and briefly play a paused player. Setting the
    // drawable alone changes the native output setup, but does not promise a new render view.
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
 * Runs the repaint workaround for the foreground and PiP on the main thread. It sets the drawable
 * again. A paused player then plays briefly to produce frames, and pauses again if the media and
 * the playback command are still current. A request while a recovery job runs is dropped, because
 * the running job covers it.
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
                // Hide this temporary Playing state from room sync. Keep the flag until the
                // Paused event arrives, because VLCKit's pause() queues native work.
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
