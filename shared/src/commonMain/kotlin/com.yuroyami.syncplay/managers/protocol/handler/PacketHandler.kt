package com.yuroyami.syncplay.managers.protocol.handler

import com.yuroyami.syncplay.managers.protocol.ProtocolManager
import com.yuroyami.syncplay.utils.loggy
import com.yuroyami.syncplay.viewmodels.RoomViewmodel
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
 * Handles parsing and processing of incoming Syncplay protocol packets from the server.
 *
 * Deserializes JSON packets into typed message objects and delegates handling to the
 * appropriate message type. Uses Kotlinx Serialization with polymorphic deserialization
 * to automatically route messages based on their top-level JSON key.
 *
 * ## Supported Message Types
 * - **Hello**: Server greeting and room information
 * - **Chat**: Chat messages from other users
 * - **Set**: Various state updates (user list, file info, playlist, etc.)
 * - **List**: User list updates
 * - **State**: Playback state synchronization
 * - **TLS**: TLS negotiation response
 * - **Error**: Server error messages
 *
 * @property viewmodel The RoomViewModel managing the current session
 * @property protocol The ProtocolManager maintaining protocol state
 */
class PacketHandler(
    val viewmodel: RoomViewmodel,
    val protocol: ProtocolManager
) {
    /**
     * Quick access to the callback interface for protocol events.
     * Used to notify UI and trigger actions in response to server messages.
     */
    val callback = viewmodel.callbackManager

    /**
     * Quick access to the Network manager for sending responses to the server.
     */
    val sender = viewmodel.networkManager

    /**
     * JSON parser configured for Syncplay protocol deserialization.
     *
     * - Ignores unknown keys for forward compatibility
     * - Coerces input values to handle type mismatches gracefully
     * - Registers all Syncplay message types for polymorphic deserialization
     */
    val handlerJson = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true

        serializersModule = SerializersModule {
            polymorphic(SyncplayMessage::class) {
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

    /**
     * Timestamp of the last global state update received from the server.
     * Used to track synchronization timing and prevent redundant updates.
     */
    var lastGlobalUpdate: Instant? = null

    companion object {
        /**
         * Position difference threshold in seconds to trigger a seek operation.
         * If playback drift exceeds this threshold, a corrective seek is performed.
         */
        const val SEEK_THRESHOLD = 1L
    }

    /**
     * Parses a JSON packet from the server and delegates handling to the appropriate message type.
     *
     * Automatically determines message type from the JSON structure, deserializes it,
     * and calls the message's [SyncplayMessage.handle] method to process it.
     *
     * @param jsonString The raw JSON packet received from the server
     * @throws SerializationException if the JSON is malformed or contains unknown message types
     */
    suspend fun parse(jsonString: String) {
        loggy("**SERVER** $jsonString")

        try {
            // Parse the JSON to determine message type
            val elementDecoded = handlerJson.decodeFromString<SyncplayMessage>(
                deserializer = SyncplayMessageSerializer,
                string = jsonString
            )
            elementDecoded.handle()

        } catch (e: SerializationException) {
            loggy("Problematic Json: $jsonString")
            loggy("Serialization error: ${e.message}")
            throw e
        }
    }

    /**
     * Custom polymorphic serializer for Syncplay messages.
     *
     * Determines the concrete message type by examining the top-level JSON key.
     * Each Syncplay message is wrapped in a JSON object with a single key indicating
     * its type (e.g., `{"Hello": {...}}`, `{"State": {...}}`).
     */
    object SyncplayMessageSerializer : JsonContentPolymorphicSerializer<SyncplayMessage>(SyncplayMessage::class) {
        /**
         * Selects the appropriate deserializer based on the JSON structure.
         *
         * @param element The JSON element to deserialize
         * @return Deserializer for the specific message type
         * @throws SerializationException if the message type is unknown
         */
        override fun selectDeserializer(element: JsonElement): DeserializationStrategy<SyncplayMessage> {
            val key = element.jsonObject.keys.first()
            return when (key) {
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