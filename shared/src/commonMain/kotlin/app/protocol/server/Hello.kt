package app.protocol.server

import app.protocol.ProtocolManager
import app.protocol.event.ClientMessage
import app.protocol.models.RoomFeatures
import app.protocol.network.NetworkManager
import app.room.RoomViewmodel
import app.protocol.event.RoomCallback
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `Hello` packet — handshake exchanged in both directions:
 * - Server→client: confirms identity, room assignment, supported features, MOTD.
 * - Client→server: identifies the client with username, password (md5), room, version, features.
 *
 * The single data class supports both shapes — direction-specific fields are nullable.
 */
@Serializable
data class Hello(
    @SerialName("Hello")
    val hello: HelloData
) : ServerMessage {

    /** Updates session info, sends join/list requests, and triggers [RoomCallback.onConnected]. */
    override suspend fun handle(
        protocol: ProtocolManager,
        viewmodel: RoomViewmodel,
        dispatcher: NetworkManager,
        callback: RoomCallback
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
        /** Pre-hashed (MD5) server password. Only set on client→server. */
        val password: String? = null,
        val room: Room? = null,
        val version: String? = null,
        val realversion: String? = null,
        val features: RoomFeatures = RoomFeatures(),
        /** Server-only field. */
        val motd: String = ""
    )
}
