package com.yuroyami.syncplay.home

import com.yuroyami.syncplay.settings.DataStoreKeys
import com.yuroyami.syncplay.settings.obtainInt
import com.yuroyami.syncplay.settings.obtainString
import kotlinx.coroutines.runBlocking

class HomeConfig {

    var savedUser: String = ""
    var savedRoom: String = ""
    var savedIP: String = ""
    var savedPort: Int = 8995
    var savedPassword: String = ""


    init {
        savedUser = runBlocking { obtainString(DataStoreKeys.MISC_JOIN_USERNAME, "user" + (0..9999).random().toString()) }
        savedRoom = runBlocking { obtainString(DataStoreKeys.MISC_JOIN_ROOMNAME, "room" + (0..9999).random().toString()) }
        savedIP = runBlocking { obtainString(DataStoreKeys.MISC_JOIN_SERVER_ADDRESS, "syncplay.pl") }
        savedPort = runBlocking { obtainInt(DataStoreKeys.MISC_JOIN_SERVER_PORT, 8997) }
        savedPassword = runBlocking { obtainString(DataStoreKeys.MISC_JOIN_SERVER_PW, "") }
    }

}