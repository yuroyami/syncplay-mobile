package app.player.models

import app.preferences.Preferences
import app.preferences.value

class PlayerOptions private constructor() {

    var maxBuffer = 30000
        private set
    var minBuffer = 15000
        private set

    /** Buffer in ms to fill after seeking before resuming playback. */
    var playbackBuffer = 2000
        private set

    /** ISO 639 language code. "und" = no preference. */
    var audioPreference = "und"
        private set

    /** ISO 639 language code. */
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