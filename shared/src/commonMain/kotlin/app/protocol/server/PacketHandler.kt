package app.protocol.server

import SyncplayMobile.shared.BuildConfig
import app.protocol.ProtocolManager
import app.room.RoomViewmodel
import app.utils.loggy
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlin.time.Instant

/**
 * Parses and dispatches incoming Syncplay protocol packets.
 *
 * Deserializes JSON into typed [ServerMessage] objects via polymorphic deserialization,
 * routing by top-level JSON key (e.g. `{"Hello": {...}}`, `{"State": {...}}`).
 */
class PacketHandler(
    val viewmodel: RoomViewmodel,
    val protocol: ProtocolManager
) {
    val callback = viewmodel.roomIn
    val sender = viewmodel.networkManager

    val handlerJson = Json {
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

    /** Timestamp of the last received global state update, used for sync timing. */
    var lastGlobalUpdate: Instant? = null

    companion object {
        /** Playback drift threshold in seconds before a corrective seek is triggered. */
        const val SEEK_THRESHOLD = 1L
    }

    /** Parses a raw JSON packet and delegates to the appropriate [ServerMessage.handle]. */
    suspend fun parse(jsonString: String) {
        if (BuildConfig.DEBUG_SYNCPLAY_PROTOCOL) loggy("**SERVER** $jsonString")

        try {
            handlerJson.decodeFromString<ServerMessage>(
                deserializer = ServerMessageDeserializer,
                string = jsonString
            ).handle()
        } catch (e: SerializationException) {
            loggy("Problematic Json: $jsonString")
            loggy("Serialization error: ${e.message}")
            throw e
        }
    }

    /** Selects the [ServerMessage] deserializer by inspecting the top-level JSON key. */
    object ServerMessageDeserializer : JsonContentPolymorphicSerializer<ServerMessage>(ServerMessage::class) {
        override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ServerMessage> {
            return when (val key = element.jsonObject.keys.first()) {
                "Hello" -> Hello.serializer()
                "Chat" -> Chat.serializer()
                "Set" -> Set.serializer()
                "List" -> ListResponse.serializer()
                "State" -> State.serializer()
                "TLS" -> TLS.serializer()
                "Error" -> Error.serializer()
                else -> throw SerializationException("Unknown message type: $key")
            }
        }
    }
}