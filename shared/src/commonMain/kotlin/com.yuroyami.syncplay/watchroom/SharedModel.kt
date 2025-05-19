package com.yuroyami.syncplay.watchroom

import com.yuroyami.syncplay.models.Constants
import com.yuroyami.syncplay.models.JoinInfo
import com.yuroyami.syncplay.viewmodel.PlatformCallback
import com.yuroyami.syncplay.settings.DataStoreKeys
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_TLS_ENABLE
import com.yuroyami.syncplay.settings.valueBlockingly
import kotlinx.coroutines.launch

class SpViewModel {

}

fun prepareProtocol(joinInfo: JoinInfo) {
    if (!joinInfo.soloMode) {
        viewmodel?.setReadyDirectly = valueBlockingly(DataStoreKeys.PREF_READY_FIRST_HAND, true)


            /** Getting information from joining info argument **/
            p.session.serverHost = joinInfo.address
            p.session.serverPort = joinInfo.port
            p.session.currentUsername = joinInfo.username
            p.session.currentRoom = joinInfo.roomname
            p.session.currentPassword = joinInfo.password

            /** Connecting */
            val tls = valueBlockingly(PREF_TLS_ENABLE, default = true)
            if (tls && p.supportsTLS()) {
                p.syncplayCallback?.onTLSCheck()
                p.tls = Constants.TLS.TLS_ASK
            }
            p.protoScope.launch {
                p.connect()
            }
    }
}
