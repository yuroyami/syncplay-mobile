package com.yuroyami.syncplay.screens.home

import com.yuroyami.syncplay.settings.DataStoreKeys
import com.yuroyami.syncplay.settings.valueBlockingly
import com.yuroyami.syncplay.settings.valueSuspendingly
import com.yuroyami.syncplay.settings.writeValue

data class JoinConfig(
    var user: String = "",
    var room: String = "",
    var ip: String = "",
    var port: Int = 8995,
    var pw: String = "",
) {
    companion object {
        fun String.turnIntoOfficialIpIfApplicable(): String {
            return if (this == "syncplay.pl") "151.80.32.178" else this
        }

        fun savedConfig() = JoinConfig(
            user = valueBlockingly(DataStoreKeys.MISC_JOIN_USERNAME, "user" + (0..9999).random().toString()),
            room = valueBlockingly(DataStoreKeys.MISC_JOIN_ROOMNAME, "room" + (0..9999).random().toString()),
            ip = valueBlockingly(DataStoreKeys.MISC_JOIN_SERVER_ADDRESS, "syncplay.pl"),
            port = valueBlockingly(DataStoreKeys.MISC_JOIN_SERVER_PORT, 8997),
            pw = valueBlockingly(DataStoreKeys.MISC_JOIN_SERVER_PW, "")
        )
    }

    suspend fun save() {
        val saveInfo = valueSuspendingly(DataStoreKeys.PREF_REMEMBER_INFO, true)

        if (saveInfo) {
            writeValue(DataStoreKeys.MISC_JOIN_USERNAME, user)
            writeValue(DataStoreKeys.MISC_JOIN_ROOMNAME, room)
            writeValue(DataStoreKeys.MISC_JOIN_SERVER_ADDRESS, ip)
            writeValue(DataStoreKeys.MISC_JOIN_SERVER_PORT, port)
            writeValue(DataStoreKeys.MISC_JOIN_SERVER_PW, pw)
        }
    }
}