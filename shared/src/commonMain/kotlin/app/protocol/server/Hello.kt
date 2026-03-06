package app.protocol.server

import app.room.event.RoomEventHandler
import app.protocol.models.ClientMessage
import app.protocol.models.RoomFeatures
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
    context(packetHandler: PacketHandler)
    override suspend fun handle() {
        hello.username?.let { username ->
            packetHandler.viewmodel.session.currentUsername = username
        }

        packetHandler.viewmodel.session.roomFeatures = hello.features

        packetHandler.sender.send<ClientMessage.Joined> {
            roomname = packetHandler.viewmodel.session.currentRoom
        }

        packetHandler.sender.send<ClientMessage.EmptyList>()
        packetHandler.callback.onConnected()
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