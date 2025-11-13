package com.yuroyami.syncplay.managers.protocol.handler

import com.yuroyami.syncplay.managers.OnRoomEventManager
import com.yuroyami.syncplay.managers.protocol.creator.PacketOut
import com.yuroyami.syncplay.models.RoomFeatures
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Incoming server "Hello" packet.
 *
 * Sent by the server upon connection to confirm identity,
 * room assignment, and supported feature set.
 * only after this do we consider the connection established ("connected")
 */
@Serializable
data class Hello(
    @SerialName("Hello")
    val hello: HelloData
) : SyncplayMessage {

    /**
     * Handles server handshake and session initialization.
     *
     * Updates session info, sends join/list requests, and triggers [OnRoomEventManager.onConnected].
     */
    context(packetHandler: PacketHandler)
    override suspend fun handle() {
        hello.username?.let { username ->
            packetHandler.viewmodel.session.currentUsername = username
        }

        packetHandler.viewmodel.session.roomFeatures = hello.features

        packetHandler.sender.send<PacketOut.Joined> {
            roomname = packetHandler.viewmodel.session.currentRoom
        }

        packetHandler.sender.send<PacketOut.EmptyList>()
        packetHandler.callback.onConnected()
    }

    /**
     * Server handshake payload.
     *
     * @property username Assigned username after connection.
     * @property room Current joined room info.
     * @property version Declared Syncplay protocol version.
     * @property realversion Server's actual version string.
     * @property features Supported feature set for the current room.
     * @property motd Message of the day (if provided by the server).
     */
    @Serializable
    data class HelloData(
        val username: String? = null,
        val room: Room,
        val version: String? = null,
        val realversion: String? = null,
        val features: RoomFeatures = RoomFeatures(),
        val motd: String = ""
    )

    /**
     * Room information.
     *
     * @property name Room name.
     */
    @Serializable
    data class Room(
        val name: String
    )
}
