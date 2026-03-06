package app.protocol

import app.AbstractManager
import app.protocol.event.ClientMessage
import app.protocol.models.PingService
import app.protocol.network.NetworkManager
import app.protocol.server.Chat
import app.protocol.server.Error
import app.protocol.server.Hello
import app.protocol.server.ListResponse
import app.protocol.server.ServerMessage
import app.protocol.server.Set
import app.protocol.server.State
import app.protocol.server.TLS
import app.room.RoomViewmodel
import app.utils.ProtocolApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlin.time.Instant

class ProtocolManager(val viewmodel: RoomViewmodel) : AbstractManager(viewmodel) {

    var session: Session = Session(this)

    var globalPaused: Boolean = true
    var globalPositionMs: Double = 0.0

    /** Tracks conflicting state updates during rapid changes. */
    var serverIgnFly: Int = 0

    /** Prevents responding to our own state changes until the server acknowledges them. */
    var clientIgnFly: Int = 0

    /** Position drift threshold for hard seek. 12s per Syncplay spec — do not change. */
    val rewindThreshold = 12L

    /** Timestamp of the last received global state update, used for sync timing. */
    var lastGlobalUpdate: Instant? = null

    var pingService = PingService()

    /** Set during room transitions to ignore stale packets from the previous room. */
    var isRoomChanging = false

    val supportsChat = MutableStateFlow(true)
    val supportsManagedRooms = MutableStateFlow(false)

    val isManagedRoom = MutableStateFlow(false)

    override fun invalidate() {
        session = Session(this)
        globalPaused = true
        globalPositionMs = 0.0
        serverIgnFly = 0
        clientIgnFly = 0
        pingService = PingService()
    }

    companion object {
        @ProtocolApi
        val serverJson = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true

            serializersModule = SerializersModule {
                polymorphic(ServerMessage::class) {
                    subclass(Hello::class)
                    subclass(Chat::class)
                    subclass(Set::class)
                    subclass(ListResponse::class)
                    subclass(State::class)
                    subclass(TLS::class)
                    subclass(Error::class)
                }
            }
        }

        @ProtocolApi
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

        /** Playback drift threshold in seconds before a corrective seek is triggered. */
        const val SEEK_THRESHOLD = 1L
    }
}