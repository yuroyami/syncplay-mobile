package com.yuroyami.syncplay.home

import com.yuroyami.syncplay.settings.DataStoreKeys
import com.yuroyami.syncplay.settings.valueBlockingly

class HomeConfig {

    var savedUser: String = ""
    var savedRoom: String = ""
    var savedIP: String = ""
    var savedPort: Int = 8995
    var savedPassword: String = ""


    init {
        savedUser = valueBlockingly(DataStoreKeys.MISC_JOIN_USERNAME, "user" + (0..9999).random().toString())
        savedRoom = valueBlockingly(DataStoreKeys.MISC_JOIN_ROOMNAME, "room" + (0..9999).random().toString())
        savedIP = valueBlockingly(DataStoreKeys.MISC_JOIN_SERVER_ADDRESS, "syncplay.pl")
        savedPort = valueBlockingly(DataStoreKeys.MISC_JOIN_SERVER_PORT, 8997)
        savedPassword = valueBlockingly(DataStoreKeys.MISC_JOIN_SERVER_PW, "")
    }

}