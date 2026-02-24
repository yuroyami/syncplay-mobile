package com.yuroyami.syncplay.managers.player

import com.yuroyami.syncplay.managers.player.PlayerOptions.Companion.get
import com.yuroyami.syncplay.managers.preferences.Preferences.AUDIO_LANG
import com.yuroyami.syncplay.managers.preferences.Preferences.CC_LANG
import com.yuroyami.syncplay.managers.preferences.Preferences.EXO_MAX_BUFFER
import com.yuroyami.syncplay.managers.preferences.Preferences.EXO_MIN_BUFFER
import com.yuroyami.syncplay.managers.preferences.Preferences.EXO_SEEK_BUFFER
import com.yuroyami.syncplay.managers.preferences.value

/**
 * Configuration options for media player behavior and preferences.
 *
 * Encapsulates user-configurable settings that affect playback behavior such as
 * buffering parameters and default track selections. Values are loaded from
 * persistent storage (DataStore) and are immutable once retrieved.
 *
 * Use [get] for synchronous access.
 */
class PlayerOptions private constructor() {

    /**
     * Maximum buffer size in milliseconds before playback starts.
     * Larger values improve stability on slow connections but increase startup delay.
     * Default: 30 seconds (30000ms)
     */
    var maxBuffer = 30000
        private set

    /**
     * Minimum buffer size in milliseconds to maintain during playback.
     * Playback pauses to rebuffer if buffer drops below this threshold.
     * Default: 15 seconds (15000ms)
     */
    var minBuffer = 15000
        private set

    /**
     * Buffer size in milliseconds to maintain after seeking.
     * Affects how quickly playback resumes after a seek operation.
     * Default: 2 seconds (2000ms)
     *
     * This means the player will buffer for 2 seconds after seeking in order to resume playback.,
     */
    var playbackBuffer = 2000
        private set

    /**
     * Preferred audio track language as an ISO 639-2/3 language code.
     * "und" (undefined) means no specific preference - use default track.
     * Default: "und"
     */
    var audioPreference = "und"
        private set

    /**
     * Preferred subtitle/closed caption language as an ISO 639-2/3 language code.
     * Default: "eng" (English)
     */
    var ccPreference = "eng"
        private set

    companion object {
        /**
         * Retrieves player options synchronously from persistent storage.
         *
         * @return PlayerOptions instance populated with user preferences
         */
        fun get(): PlayerOptions {
            val options = PlayerOptions()
            options.maxBuffer = EXO_MAX_BUFFER.value() * 1000
            options.minBuffer = EXO_MIN_BUFFER.value() * 1000
            options.playbackBuffer = EXO_SEEK_BUFFER.value()

            options.ccPreference = CC_LANG.value()
            options.audioPreference = AUDIO_LANG.value()
            return options
        }
    }
}