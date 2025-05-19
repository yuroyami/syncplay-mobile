package com.yuroyami.syncplay.screens.home

import com.yuroyami.syncplay.settings.DataStoreKeys
import com.yuroyami.syncplay.settings.valueBlockingly

data class JoinConfig(
    var user: String = "",
    var room: String = "",
    var ip: String = "",
    var port: Int = 8995,
    var pw: String = "",
) {
    companion object {
        fun savedConfig() = JoinConfig(
            user = valueBlockingly(DataStoreKeys.MISC_JOIN_USERNAME, "user" + (0..9999).random().toString()),
            room = valueBlockingly(DataStoreKeys.MISC_JOIN_ROOMNAME, "room" + (0..9999).random().toString()),
            ip = valueBlockingly(DataStoreKeys.MISC_JOIN_SERVER_ADDRESS, "syncplay.pl"),
            port = valueBlockingly(DataStoreKeys.MISC_JOIN_SERVER_PORT, 8997),
            pw = valueBlockingly(DataStoreKeys.MISC_JOIN_SERVER_PW, "")
        )
    }
}