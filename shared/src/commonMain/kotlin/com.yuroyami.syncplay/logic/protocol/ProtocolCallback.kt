package com.yuroyami.syncplay.logic.protocol

interface ProtocolCallback {
    fun onSomeonePaused(pauser: String)

    fun onSomeonePlayed(player: String)

    fun onChatReceived(chatter: String, chatmessage: String)

    fun onSomeoneJoined(joiner: String)

    fun onSomeoneLeft(leaver: String)

    fun onSomeoneSeeked(seeker: String, toPosition: Double)

    fun onSomeoneBehind(behinder: String, toPosition: Double)

    fun onReceivedList()

    fun onSomeoneLoadedFile(person: String, file: String?, fileduration: Double?)

    fun onPlaylistUpdated(user: String)

    fun onPlaylistIndexChanged(user: String, index: Int)

    suspend fun onConnected()

    fun onConnectionAttempt()

    fun onConnectionFailed()

    fun onDisconnected()

    fun onTLSCheck()

    suspend fun onReceivedTLS(supported: Boolean)
}