package com.yuroyami.syncplay.player

import com.yuroyami.syncplay.settings.DataStoreKeys
import com.yuroyami.syncplay.settings.valueBlockingly
import com.yuroyami.syncplay.settings.valueSuspendingly

class PlayerOptions private constructor() {

    var maxBuffer = 30000
        private set

    var minBuffer = 15000
        private set

    var playbackBuffer = 2000
        private set

    var audioPreference = "und"
        private set

    var ccPreference = "eng"
        private set

    companion object {
        fun get(): PlayerOptions {
            val options = PlayerOptions()
            options.maxBuffer = valueBlockingly(DataStoreKeys.PREF_MAX_BUFFER, 30) * 1000
            options.minBuffer = valueBlockingly(DataStoreKeys.PREF_MIN_BUFFER, 15) * 1000
            options.playbackBuffer = valueBlockingly(DataStoreKeys.PREF_SEEK_BUFFER, 2000)

            options.ccPreference = valueBlockingly(DataStoreKeys.PREF_CC_LANG, "eng")
            options.audioPreference = valueBlockingly(DataStoreKeys.PREF_AUDIO_LANG, "und")
            return options
        }

        suspend fun getSuspendingly(): PlayerOptions {
            val options = PlayerOptions()
            options.maxBuffer = valueSuspendingly(DataStoreKeys.PREF_MAX_BUFFER, 30) * 1000
            options.minBuffer = valueSuspendingly(DataStoreKeys.PREF_MIN_BUFFER, 15) * 1000
            options.playbackBuffer = valueSuspendingly(DataStoreKeys.PREF_SEEK_BUFFER, 2000)

            options.ccPreference =  valueSuspendingly(DataStoreKeys.PREF_CC_LANG, "eng")
            options.audioPreference = valueSuspendingly(DataStoreKeys.PREF_AUDIO_LANG, "und")
            return options
        }
    }
}