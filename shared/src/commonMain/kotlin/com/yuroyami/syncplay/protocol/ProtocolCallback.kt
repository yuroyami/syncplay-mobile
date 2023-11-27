package com.yuroyami.syncplay.protocol

interface ProtocolCallback {

    /** Interface that is used as an intermediate (middle-man) bridge between the protocol and the
     * client itself. This interface broadcasts multiple events to the UI client **/

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

    fun onConnected()

    fun onConnectionAttempt()

    fun onConnectionFailed()

    fun onDisconnected()

    fun onTLSCheck()

    fun onReceivedTLS(supported: Boolean)
}