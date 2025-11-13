package com.yuroyami.syncplay.managers.protocol.handler

import com.yuroyami.syncplay.managers.OnRoomEventManager
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Incoming server "Chat" packet.
 *
 * The `handle` method extracts `username` and `message` and forwards them
 * to [OnRoomEventManager.onChatReceived].
 */
@Serializable
data class Chat(
    @SerialName("Chat") val chat: ChatData
) : SyncplayMessage {

    /**
     * Process the chat packet.
     *
     * If either `username` or `message` is missing, the packet is ignored.
     * Otherwise forwards `(username, message)` to our room callback.
     */
    context(packetHandler: PacketHandler)
    override suspend fun handle() {
        val sender = chat.username ?: return
        val message = chat.message ?: return
        packetHandler.callback.onChatReceived(sender, message)
    }

    /**
     * Chat payload fields.
     *
     * @property username sender's display name (nullable in raw data; ignored if null)
     * @property message  chat text (nullable in raw data; ignored if null)
     */
    @Serializable
    data class ChatData(
        val username: String? = null,
        val message: String? = null
    )
}
