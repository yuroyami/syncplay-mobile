package com.yuroyami.syncplay.models

import com.yuroyami.syncplay.logic.datastore.DataStoreKeys
import com.yuroyami.syncplay.logic.datastore.valueSusQuick
import com.yuroyami.syncplay.logic.datastore.valueSuspendingly
import com.yuroyami.syncplay.logic.datastore.writeValue
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
            return if (cfg != null) Json.Default.decodeFromString<JoinConfig>(cfg) else JoinConfig()
        }
    }

    suspend fun save() {
        val saveInfo = valueSuspendingly(DataStoreKeys.PREF_REMEMBER_INFO, true)

        if (saveInfo) {
            writeValue(DataStoreKeys.MISC_JOIN_CONFIG, Json.Default.encodeToString(this))
        }
    }
}