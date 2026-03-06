package app.protocol.server

import app.room.event.RoomEventHandler
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Incoming server "Chat" packet. Forwards username and message to [RoomEventHandler.onChatReceived]. */
@Serializable
data class Chat(
    @SerialName("Chat") val chat: ChatData
) : ServerMessage {

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