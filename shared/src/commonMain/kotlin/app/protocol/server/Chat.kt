package app.protocol.server

import app.protocol.ProtocolManager
import app.protocol.network.NetworkManager
import app.room.RoomViewmodel
import app.protocol.event.RoomCallback
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Incoming server "Chat" packet. Forwards username and message to [RoomCallback.onChatReceived]. */
@Serializable
data class Chat(
    @SerialName("Chat") val chat: ChatData
) : ServerMessage {

    override suspend fun handle(
        protocol: ProtocolManager,
        viewmodel: RoomViewmodel,
        dispatcher: NetworkManager,
        callback: RoomCallback
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