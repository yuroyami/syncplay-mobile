package com.yuroyami.syncplay.managers

import com.yuroyami.syncplay.logic.AbstractManager
import com.yuroyami.syncplay.logic.SyncplayViewmodel
import com.yuroyami.syncplay.logic.protocol.Packet
import com.yuroyami.syncplay.logic.protocol.Packet.*
import com.yuroyami.syncplay.logic.protocol.PingService

class ProtocolManager(viewmodel: SyncplayViewmodel) : AbstractManager(viewmodel) {
    var globalPaused: Boolean = true //Latest server paused state
    var globalPositionMs: Double = 0.0 //Latest server video position

    var serverIgnFly: Int = 0
    var clientIgnFly: Int = 0
    val rewindThreshold = 12L /* This is as per official Syncplay, shouldn't be subject to change */
    val pingService = PingService() //TODO

    object JsonSender {
        typealias SendablePacket = String

        inline fun <reified T : Packet> SyncplayProtocol.createPacketInstance(): T {
            return when (T::class) {
                Hello::class -> Hello() as T
                Joined::class -> Joined() as T
                Packet.EmptyList::class -> Packet.EmptyList() as T
                Readiness::class -> Readiness() as T
                File::class -> File() as T
                Chat::class -> Chat() as T
                Packet.State::class -> State(this) as T
                PlaylistChange::class -> PlaylistChange() as T
                PlaylistIndex::class -> PlaylistIndex() as T
                TLS::class -> TLS() as T
                else -> throw IllegalArgumentException("Unknown packet type: ${T::class.simpleName}")
            }
        }
    }
}