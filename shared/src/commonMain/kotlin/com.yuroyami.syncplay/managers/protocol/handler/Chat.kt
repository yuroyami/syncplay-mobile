package com.yuroyami.syncplay.managers.protocol.handler

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Chat(
    @SerialName("Chat")
    val chat: ChatData
): SyncplayMessage {

    context(packetHandler: PacketHandler)
    override suspend fun handle() {
        val sender = chat.username ?: return
        val message = chat.message ?: return
        packetHandler.callback.onChatReceived(sender, message)
    }

    @Serializable
    data class ChatData(
        val username: String? = null,
        val message: String? = null
    )
}