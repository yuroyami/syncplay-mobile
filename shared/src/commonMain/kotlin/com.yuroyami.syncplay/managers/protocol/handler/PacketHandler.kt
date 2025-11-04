package com.yuroyami.syncplay.managers.protocol.handler

import com.yuroyami.syncplay.managers.ProtocolManager
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

class PacketHandler(
    val viewmodel: RoomViewmodel,
    val protocol: ProtocolManager
) {
    val callback = viewmodel.callbackManager
    val sender = viewmodel.networkManager

    val handlerJson = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true

        serializersModule = SerializersModule {
            polymorphic(SyncplayMessage::class) {
                subclass(Hello::class)
                subclass(Chat::class)
                subclass(Set::class)
                subclass(List::class)
                subclass(State::class)
                subclass(TLS::class)
                subclass(Error::class)
            }
        }
    }

    var lastGlobalUpdate: Instant? = null
    val SEEK_THRESHOLD = 1L

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
                loggy("Serialization error: ${e.message}")
                throw e
            }
    }


    object SyncplayMessageSerializer : JsonContentPolymorphicSerializer<SyncplayMessage>(SyncplayMessage::class) {
        override fun selectDeserializer(element: JsonElement): DeserializationStrategy<SyncplayMessage> {
            val key = element.jsonObject.keys.first()
            return when (key) {
                "Hello" -> Hello.serializer()
                "Chat" -> Chat.serializer()
                "Set" -> Set.serializer()
                "List" -> List.serializer()
                "State" -> State.serializer()
                "TLS" -> TLS.serializer()
                "Error" -> Error.serializer()
                else -> throw SerializationException("Unknown message type: $key")
            }
        }
    }
}