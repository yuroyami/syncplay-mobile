package app.protocol.server

import app.protocol.ProtocolManager
import app.protocol.models.ClientMessage
import app.protocol.models.RoomFeatures
import app.protocol.network.NetworkManager
import app.room.RoomViewmodel
import app.protocol.event.RoomEventHandler
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Incoming server "Hello" packet, confirming identity, room assignment, and supported features.
 * Receiving this marks the connection as established.
 */
@Serializable
data class Hello(
    @SerialName("Hello")
    val hello: HelloData
) : ServerMessage {

    /** Updates session info, sends join/list requests, and triggers [RoomEventHandler.onConnected]. */
    override suspend fun handle(
        protocol: ProtocolManager,
        viewmodel: RoomViewmodel,
        dispatcher: NetworkManager,
        callback: RoomEventHandler
    ) {
        hello.username?.let { username ->
            protocol.session.currentUsername = username
        }

        protocol.session.roomFeatures = hello.features

        dispatcher.send<ClientMessage.Joined> {
            roomname = protocol.session.currentRoom
        }

        dispatcher.send<ClientMessage.EmptyList>()
        callback.onConnected()
    }

    @Serializable
    data class HelloData(
        val username: String? = null,
        val room: Room,
        val version: String? = null,
        val realversion: String? = null,
        val features: RoomFeatures = RoomFeatures(),
        val motd: String = ""
    )

    @Serializable
    data class Room(
        val name: String
    )
}