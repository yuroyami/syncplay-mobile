package app.server.protocol.incoming

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

/**
 * Selects the [IncomingMessage] deserializer by inspecting the top-level JSON key.
 *
 * Counterpart to [app.protocol.network.ServerMessageDeserializer] on the client side —
 * same dispatch pattern, just routing client-originated messages to their server-side handlers.
 */
object IncomingMessageDeserializer : JsonContentPolymorphicSerializer<IncomingMessage>(IncomingMessage::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<IncomingMessage> {
        return when (val key = element.jsonObject.keys.firstOrNull()) {
            "Hello" -> IncomingHello.serializer()
            "State" -> IncomingState.serializer()
            "Set" -> IncomingSet.serializer()
            "List" -> IncomingList.serializer()
            "Chat" -> IncomingChat.serializer()
            "TLS" -> IncomingTLS.serializer()
            "Error" -> IncomingError.serializer()
            else -> throw SerializationException("Unknown incoming message type: $key")
        }
    }
}
