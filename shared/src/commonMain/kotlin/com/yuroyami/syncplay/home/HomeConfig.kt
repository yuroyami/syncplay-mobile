package com.yuroyami.syncplay.home

import com.yuroyami.syncplay.datastore.DataStoreKeys
import com.yuroyami.syncplay.datastore.obtainInt
import com.yuroyami.syncplay.datastore.obtainString
import kotlinx.coroutines.runBlocking

class HomeConfig {

    var savedUser: String = ""
    var savedRoom: String = ""
    var savedIP: String = ""
    var savedPort: Int = 8995
    var savedPassword: String = ""


    init {
        savedUser = runBlocking { DataStoreKeys.DATASTORE_MISC_PREFS.obtainString(DataStoreKeys.MISC_JOIN_USERNAME, "user" + (0..9999).random().toString()) }
        savedRoom = runBlocking { DataStoreKeys.DATASTORE_MISC_PREFS.obtainString(DataStoreKeys.MISC_JOIN_ROOMNAME, "room" + (0..9999).random().toString()) }
        savedIP = runBlocking { DataStoreKeys.DATASTORE_MISC_PREFS.obtainString(DataStoreKeys.MISC_JOIN_SERVER_ADDRESS, "syncplay.pl") }
        savedPort = runBlocking { DataStoreKeys.DATASTORE_MISC_PREFS.obtainInt(DataStoreKeys.MISC_JOIN_SERVER_PORT, 8997) }
        savedPassword = runBlocking { DataStoreKeys.DATASTORE_MISC_PREFS.obtainString(DataStoreKeys.MISC_JOIN_SERVER_PW, "") }
    }

}