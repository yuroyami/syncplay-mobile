package com.yuroyami.syncplay.managers

import com.yuroyami.syncplay.logic.AbstractManager
import com.yuroyami.syncplay.logic.SyncplayViewmodel
import com.yuroyami.syncplay.logic.protocol.Packet
import com.yuroyami.syncplay.logic.protocol.Packet.Chat
import com.yuroyami.syncplay.logic.protocol.Packet.EmptyList
import com.yuroyami.syncplay.logic.protocol.Packet.File
import com.yuroyami.syncplay.logic.protocol.Packet.Hello
import com.yuroyami.syncplay.logic.protocol.Packet.Joined
import com.yuroyami.syncplay.logic.protocol.Packet.PlaylistChange
import com.yuroyami.syncplay.logic.protocol.Packet.PlaylistIndex
import com.yuroyami.syncplay.logic.protocol.Packet.Readiness
import com.yuroyami.syncplay.logic.protocol.Packet.State
import com.yuroyami.syncplay.logic.protocol.Packet.TLS
import com.yuroyami.syncplay.logic.protocol.PingService

class ProtocolManager(viewmodel: SyncplayViewmodel) : AbstractManager(viewmodel) {
    var globalPaused: Boolean = true //Latest server paused state
    var globalPositionMs: Double = 0.0 //Latest server video position

    var serverIgnFly: Int = 0
    var clientIgnFly: Int = 0
    val rewindThreshold = 12L /* This is as per official Syncplay, shouldn't be subject to change */
    val pingService = PingService() //TODO

    companion object {
        inline fun <reified T : Packet> NetworkManager.createPacketInstance(protocolManager: ProtocolManager): T {
            return when (T::class) {
                Hello::class -> Hello() as T
                Joined::class -> Joined() as T
                EmptyList::class -> EmptyList() as T
                Readiness::class -> Readiness() as T
                File::class -> File() as T
                Chat::class -> Chat() as T
                State::class -> State(protocolManager) as T
                PlaylistChange::class -> PlaylistChange() as T
                PlaylistIndex::class -> PlaylistIndex() as T
                TLS::class -> TLS() as T
                else -> throw IllegalArgumentException("Unknown packet type: ${T::class.simpleName}")
            }
        }
    }
}