package app.protocol

import app.AbstractManager
import app.room.RoomViewmodel
import app.utils.ProtocolDsl
import app.protocol.models.ClientMessage
import app.protocol.models.PingService
import app.protocol.network.NetworkManager
import app.protocol.server.PacketHandler

class ProtocolManager(val viewmodel: RoomViewmodel) : AbstractManager(viewmodel) {

    var globalPaused: Boolean = true
    var globalPositionMs: Double = 0.0

    /** Tracks conflicting state updates during rapid changes. */
    var serverIgnFly: Int = 0

    /** Prevents responding to our own state changes until the server acknowledges them. */
    var clientIgnFly: Int = 0

    /** Position drift threshold for hard seek. 12s per Syncplay spec — do not change. */
    val rewindThreshold = 12L

    var pingService = PingService()

    @ProtocolDsl
    val packetHandler = PacketHandler(viewmodel, this)

    /** Set during room transitions to ignore stale packets from the previous room. */
    var isRoomChanging = false

    override fun invalidate() {
        globalPaused = true
        globalPositionMs = 0.0
        serverIgnFly = 0
        clientIgnFly = 0
        pingService = PingService()
    }

    companion object {
        @ProtocolDsl
        inline fun <reified T : ClientMessage> NetworkManager.createPacketInstance(protocolManager: ProtocolManager): T {
            return when (T::class) {
                ClientMessage.Hello::class -> ClientMessage.Hello() as T
                ClientMessage.Joined::class -> ClientMessage.Joined() as T
                ClientMessage.EmptyList::class -> ClientMessage.EmptyList() as T
                ClientMessage.Readiness::class -> ClientMessage.Readiness() as T
                ClientMessage.File::class -> ClientMessage.File() as T
                ClientMessage.Chat::class -> ClientMessage.Chat() as T
                ClientMessage.State::class -> ClientMessage.State(protocolManager) as T
                ClientMessage.PlaylistChange::class -> ClientMessage.PlaylistChange() as T
                ClientMessage.PlaylistIndex::class -> ClientMessage.PlaylistIndex() as T
                ClientMessage.ControllerAuth::class -> ClientMessage.ControllerAuth() as T
                ClientMessage.RoomChange::class -> ClientMessage.RoomChange() as T
                ClientMessage.TLS::class -> ClientMessage.TLS() as T
                else -> throw IllegalArgumentException("Unknown packet type: ${T::class.simpleName}")
            }
        }
    }
}