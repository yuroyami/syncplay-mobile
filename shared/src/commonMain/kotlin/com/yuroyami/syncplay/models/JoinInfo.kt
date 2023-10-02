package com.yuroyami.syncplay.models

import com.yuroyami.syncplay.datastore.DataStoreKeys
import com.yuroyami.syncplay.datastore.ds
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
            val saveInfo = DataStoreKeys.DATASTORE_MISC_PREFS.obtainBoolean(DataStoreKeys.PREF_REMEMBER_INFO, true)

            if (saveInfo) {
                DataStoreKeys.DATASTORE_MISC_PREFS.ds().writeString(DataStoreKeys.MISC_JOIN_USERNAME, username)
                DataStoreKeys.DATASTORE_MISC_PREFS.ds().writeString(DataStoreKeys.MISC_JOIN_ROOMNAME, roomname)
                DataStoreKeys.DATASTORE_MISC_PREFS.ds().writeString(DataStoreKeys.MISC_JOIN_SERVER_ADDRESS, address)
                DataStoreKeys.DATASTORE_MISC_PREFS.ds().writeInt(DataStoreKeys.MISC_JOIN_SERVER_PORT, port)
                DataStoreKeys.DATASTORE_MISC_PREFS.ds().writeString(DataStoreKeys.MISC_JOIN_SERVER_PW, password)
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