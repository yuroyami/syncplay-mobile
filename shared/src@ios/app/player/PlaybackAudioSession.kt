package app.player

import app.utils.loggy
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionModeMoviePlayback
import platform.AVFAudio.setActive

/**
 * Sets the shared audio session to video playback. Picture-in-picture needs this category, and
 * without it the small window does not open. Every iOS engine calls it before it plays.
 */
internal fun configurePlaybackAudioSession() {
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
