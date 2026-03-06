package app.protocol.network

import app.protocol.server.Chat
import app.protocol.server.Error
import app.protocol.server.Hello
import app.protocol.server.ListResponse
import app.protocol.server.ServerMessage
import app.protocol.server.Set
import app.protocol.server.State
import app.protocol.server.TLS
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

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