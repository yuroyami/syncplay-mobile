package com.yuroyami.syncplay.screens.home

import com.yuroyami.syncplay.settings.DataStoreKeys
import com.yuroyami.syncplay.settings.valueSusQuick
import com.yuroyami.syncplay.settings.valueSuspendingly
import com.yuroyami.syncplay.settings.writeValue
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class JoinConfig(
    val user: String = "user" + (0..9999).random().toString(),
    val room: String = "room" + (0..9999).random().toString(),
    val ip: String = "syncplay.pl",
    val port: Int = 8997,
    val pw: String = "",
) {
    companion object {
        suspend fun savedConfig(): JoinConfig {
            val cfg = withTimeoutOrNull(250) { valueSusQuick<String?>(DataStoreKeys.MISC_JOIN_CONFIG, null) }
            return if (cfg != null) Json.decodeFromString<JoinConfig>(cfg) else JoinConfig()
        }
    }

    suspend fun save() {
        val saveInfo = valueSuspendingly(DataStoreKeys.PREF_REMEMBER_INFO, true)

        if (saveInfo) {
            writeValue(DataStoreKeys.MISC_JOIN_CONFIG, Json.encodeToString(this))
        }
    }
}