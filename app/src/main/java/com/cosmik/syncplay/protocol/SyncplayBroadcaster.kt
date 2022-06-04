package com.cosmik.syncplay.protocol

interface SyncplayBroadcaster {

    /** Interface that is used as an intermediate (middle-man) bridge between the protocol and the
     * client itself. This interface broadcasts multiple events to the UI client (RoomActivity) **/

    fun onSomeonePaused(pauser: String)

    fun onSomeonePlayed(player: String)

    fun onChatReceived(chatter: String, chatmessage: String)

    fun onSomeoneJoined(joiner: String)

    fun onSomeoneLeft(leaver: String)

    fun onSomeoneSeeked(seeker: String, toPosition: Double)

    fun onSomeoneBehind(behinder: String, toPosition: Double)

    fun onReceivedList()

    fun onSomeoneLoadedFile(
        person: String,
        file: String,
        fileduration: String,
        filesize: String
    )

    fun onDisconnected()

    fun onReconnected()

    fun onJoined()

    fun onConnectionAttempt(port: String)

    fun onConnectionFailed()
}