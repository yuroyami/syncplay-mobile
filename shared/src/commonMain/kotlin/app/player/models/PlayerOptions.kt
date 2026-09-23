package app.player.models

import app.preferences.Preferences
import app.preferences.value

class PlayerOptions private constructor() {

    var maxBuffer = 30000
        private set
    var minBuffer = 15000
        private set

    /** The buffer (ms) to fill after a seek before playback resumes. */
    var playbackBuffer = 2000
        private set

    /** The preferred audio language as an ISO 639 code. "und" means no preference. */
    var audioPreference = "und"
        private set

    /** The preferred subtitle language as an ISO 639 code. */
    var ccPreference = "eng"
        private set

    companion object {
        fun get(): PlayerOptions {
            val options = PlayerOptions()
            options.maxBuffer = Preferences.EXO_MAX_BUFFER.value() * 1000
            options.minBuffer = Preferences.EXO_MIN_BUFFER.value() * 1000
            options.playbackBuffer = Preferences.EXO_SEEK_BUFFER.value()
            options.ccPreference = Preferences.CC_LANG.value()
            options.audioPreference = Preferences.AUDIO_LANG.value()
            return options
        }
    }
}