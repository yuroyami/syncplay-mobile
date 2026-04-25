package app.protocol

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

/** Selects the [ServerMessage] subtype to decode by inspecting the top-level JSON key. */
object ServerMessageDeserializer : JsonContentPolymorphicSerializer<ServerMessage>(ServerMessage::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ServerMessage> {
        return when (val key = element.jsonObject.keys.firstOrNull()) {
            "Hello" -> ServerMessage.Hello.serializer()
            "State" -> ServerMessage.State.serializer()
            "Set" -> ServerMessage.Set.serializer()
            "List" -> ServerMessage.List.serializer()
            "Chat" -> ServerMessage.Chat.serializer()
            "TLS" -> ServerMessage.TLS.serializer()
            "Error" -> ServerMessage.Error.serializer()
            else -> throw SerializationException("Unknown server→client message type: $key")
        }
    }
}
