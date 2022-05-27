package com.cosmik.syncplay.room

interface SyncplayBroadcaster {
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