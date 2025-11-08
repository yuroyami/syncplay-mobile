package com.yuroyami.syncplay.managers.protocol

import com.yuroyami.syncplay.AbstractManager
import com.yuroyami.syncplay.managers.network.NetworkManager
import com.yuroyami.syncplay.managers.protocol.creator.PacketCreator
import com.yuroyami.syncplay.managers.protocol.handler.PacketHandler
import com.yuroyami.syncplay.utils.ProtocolDsl
import com.yuroyami.syncplay.viewmodels.RoomViewmodel

class ProtocolManager(val viewmodel: RoomViewmodel) : AbstractManager(viewmodel) {
    var globalPaused: Boolean = true //Latest server paused state
    var globalPositionMs: Double = 0.0 //Latest server video position

    var serverIgnFly: Int = 0
    var clientIgnFly: Int = 0
    val rewindThreshold = 12L /* This is as per official Syncplay, shouldn't be subject to change */
    var pingService = PingService()

    val packetHandler = PacketHandler(viewmodel, this)

    override fun invalidate() {
        globalPaused = true
        globalPositionMs = 0.0
        serverIgnFly = 0
        clientIgnFly = 0
        pingService = PingService()
    }

    companion object {
        @ProtocolDsl
        inline fun <reified T : PacketCreator> NetworkManager.createPacketInstance(protocolManager: ProtocolManager): T {
            return when (T::class) {
                PacketCreator.Hello::class -> PacketCreator.Hello() as T
                PacketCreator.Joined::class -> PacketCreator.Joined() as T
                PacketCreator.EmptyList::class -> PacketCreator.EmptyList() as T
                PacketCreator.Readiness::class -> PacketCreator.Readiness() as T
                PacketCreator.File::class -> PacketCreator.File() as T
                PacketCreator.Chat::class -> PacketCreator.Chat() as T
                PacketCreator.State::class -> PacketCreator.State(protocolManager) as T
                PacketCreator.PlaylistChange::class -> PacketCreator.PlaylistChange() as T
                PacketCreator.PlaylistIndex::class -> PacketCreator.PlaylistIndex() as T
                PacketCreator.ControllerAuth::class -> PacketCreator.ControllerAuth() as T
                PacketCreator.RoomChange::class -> PacketCreator.RoomChange() as T
                PacketCreator.TLS::class -> PacketCreator.TLS() as T
                else -> throw IllegalArgumentException("Unknown packet type: ${T::class.simpleName}")
            }
        }
    }
}