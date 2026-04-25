package app.protocol

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

/** Selects the [ClientMessage] subtype to decode by inspecting the top-level JSON key. */
object ClientMessageDeserializer : JsonContentPolymorphicSerializer<ClientMessage>(ClientMessage::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ClientMessage> {
        return when (val key = element.jsonObject.keys.firstOrNull()) {
            "Hello" -> ClientMessage.Hello.serializer()
            "State" -> ClientMessage.State.serializer()
            "Set" -> ClientMessage.Set.serializer()
            "List" -> ClientMessage.List.serializer()
            "Chat" -> ClientMessage.Chat.serializer()
            "TLS" -> ClientMessage.TLS.serializer()
            "Error" -> ClientMessage.Error.serializer()
            else -> throw SerializationException("Unknown client→server message type: $key")
        }
    }
}
