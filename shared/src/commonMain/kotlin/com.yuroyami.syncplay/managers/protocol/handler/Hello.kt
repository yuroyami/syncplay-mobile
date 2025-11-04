package com.yuroyami.syncplay.managers.protocol.handler

import com.yuroyami.syncplay.managers.protocol.creator.PacketCreator
import com.yuroyami.syncplay.models.RoomFeatures
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Hello(@SerialName("Hello") val hello: HelloData): SyncplayMessage {

    context(packetHandler: PacketHandler)
    override suspend fun handle() {
        hello.username?.let { username ->
            packetHandler.viewmodel.session.currentUsername = username
        }

        packetHandler.viewmodel.session.roomFeatures = hello.features
        packetHandler.sender.send<PacketCreator.Joined> {
            roomname = packetHandler.viewmodel.session.currentRoom
        }

        packetHandler.sender.send<PacketCreator.EmptyList>()
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