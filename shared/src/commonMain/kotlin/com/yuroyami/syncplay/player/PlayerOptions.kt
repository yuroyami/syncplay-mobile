package com.yuroyami.syncplay.player

import com.yuroyami.syncplay.datastore.DataStoreKeys
import com.yuroyami.syncplay.datastore.obtainInt
import com.yuroyami.syncplay.datastore.obtainString
import kotlinx.coroutines.runBlocking

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
            options.maxBuffer = runBlocking { obtainInt(DataStoreKeys.PREF_MAX_BUFFER, 30000) }
            options.minBuffer = runBlocking { obtainInt(DataStoreKeys.PREF_MIN_BUFFER, 15000) }
            options.playbackBuffer = runBlocking { obtainInt(DataStoreKeys.PREF_SEEK_BUFFER, 2000) }

            options.ccPreference = runBlocking { obtainString(DataStoreKeys.PREF_CC_LANG, "eng") }
            options.audioPreference = runBlocking { obtainString(DataStoreKeys.PREF_AUDIO_LANG, "und") }
            return options
        }
    }
}