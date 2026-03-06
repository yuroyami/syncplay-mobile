package app.protocol.server

import app.protocol.ProtocolManager
import app.protocol.network.NetworkManager
import app.room.RoomViewmodel
import app.room.event.RoomEventHandler
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Incoming server "Chat" packet. Forwards username and message to [RoomEventHandler.onChatReceived]. */
@Serializable
data class Chat(
    @SerialName("Chat") val chat: ChatData
) : ServerMessage {

    override suspend fun handle(
        protocol: ProtocolManager,
        viewmodel: RoomViewmodel,
        dispatcher: NetworkManager,
        callback: RoomEventHandler
    ) {
        val sender = chat.username ?: return
        val message = chat.message ?: return
        callback.onChatReceived(sender, message)
    }

    @Serializable
    data class ChatData(
        val username: String? = null,
        val message: String? = null
    )
}