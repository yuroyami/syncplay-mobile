package com.yuroyami.syncplay.models

import com.yuroyami.syncplay.datastore.DataStoreKeys
import com.yuroyami.syncplay.datastore.obtainBoolean
import com.yuroyami.syncplay.datastore.writeInt
import com.yuroyami.syncplay.datastore.writeString
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
    private fun remember(): JoinInfo {
        CoroutineScope(Dispatchers.IO).launch {
            val saveInfo = obtainBoolean(DataStoreKeys.PREF_REMEMBER_INFO, true)

            if (saveInfo) {
                writeString(DataStoreKeys.MISC_JOIN_USERNAME, username)
                writeString(DataStoreKeys.MISC_JOIN_ROOMNAME, roomname)
                writeString(DataStoreKeys.MISC_JOIN_SERVER_ADDRESS, address)
                writeInt(DataStoreKeys.MISC_JOIN_SERVER_PORT, port)
                writeString(DataStoreKeys.MISC_JOIN_SERVER_PW, password)
            }
        }
        return this
    }

    private fun finalize(): JoinInfo {
        if (address == "syncplay.pl") address = "151.80.32.178"
        return this
    }

    fun get() = this.remember().finalize()
}