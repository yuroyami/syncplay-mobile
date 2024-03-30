package com.yuroyami.syncplay.models

import com.yuroyami.syncplay.settings.DataStoreKeys
import com.yuroyami.syncplay.settings.valueSuspendingly
import com.yuroyami.syncplay.settings.writeValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

data class JoinInfo(
    val username: String = "",
    val roomname: String = "",
    var address: String = "",
    val port: Int = 0,
    val password: String = "",
    val soloMode: Boolean = false
) {
    fun remember(): JoinInfo {
        CoroutineScope(Dispatchers.IO).launch {
            val saveInfo = valueSuspendingly(DataStoreKeys.PREF_REMEMBER_INFO, true)

            if (saveInfo) {
                writeValue(DataStoreKeys.MISC_JOIN_USERNAME, username)
                writeValue(DataStoreKeys.MISC_JOIN_ROOMNAME, roomname)
                writeValue(DataStoreKeys.MISC_JOIN_SERVER_ADDRESS, address)
                writeValue(DataStoreKeys.MISC_JOIN_SERVER_PORT, port)
                writeValue(DataStoreKeys.MISC_JOIN_SERVER_PW, password)
            }
        }
        return this
    }

    fun get(): JoinInfo {
        if (address == "syncplay.pl") address = "151.80.32.178"
        return this
    }
}