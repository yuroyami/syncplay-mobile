package com.yuroyami.syncplay.watchroom

import com.yuroyami.syncplay.models.JoinInfo
import com.yuroyami.syncplay.protocol.ProtocolCallback
import com.yuroyami.syncplay.protocol.SyncplayProtocol

lateinit var p: SyncplayProtocol //If it is not initialized, it means we're in Solo Mode

/** Returns whether we're in Solo Mode, by checking if our protocol is initialized */
fun isSoloMode(): Boolean {
    return !::p.isInitialized
}

fun setupCallback(): ProtocolCallback {
    return object: ProtocolCallback {
        override fun onSomeonePaused(pauser: String) {
        }

        override fun onSomeonePlayed(player: String) {
        }

        override fun onChatReceived(chatter: String, chatmessage: String) {
        }

        override fun onSomeoneJoined(joiner: String) {
        }

        override fun onSomeoneLeft(leaver: String) {
        }

        override fun onSomeoneSeeked(seeker: String, toPosition: Double) {
        }

        override fun onSomeoneBehind(behinder: String, toPosition: Double) {
        }

        override fun onReceivedList() {
        }

        override fun onSomeoneLoadedFile(person: String, file: String?, fileduration: Double?) {
        }

        override fun onPlaylistUpdated(user: String) {
        }

        override fun onPlaylistIndexChanged(user: String, index: Int) {
        }

        override fun onConnected() {
        }

        override fun onConnectionAttempt() {
        }

        override fun onConnectionFailed() {
        }

        override fun onDisconnected() {
        }

        override fun onTLSCheck() {
        }

        override fun onReceivedTLS(supported: Boolean) {
        }

    }
}

fun prepareProtocol(joinInfo: JoinInfo) {
    if (!joinInfo.soloMode) {
        /** Initializing our ViewModel, which is our protocol at the same time **/
        p = SyncplayProtocol()
        p.syncplayCallback = setupCallback()

        /** Getting information from intent **/
        p.session.serverHost = joinInfo.address
        p.session.serverPort = joinInfo.port
        p.session.currentUsername = joinInfo.username
        p.session.currentRoom = joinInfo.roomname
        p.session.currentPassword = joinInfo.password
    }
}