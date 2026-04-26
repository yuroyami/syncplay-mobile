package app.protocol

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Picks the [WireMessage] subtype to decode by inspecting the top-level JSON key — and,
 * for the two direction-asymmetric keys, the payload shape underneath:
 *
 *  - `Chat` payload is a string → [WireMessage.ChatRequest] (client→server).
 *  - `Chat` payload is anything else (object) → [WireMessage.ChatBroadcast] (server→client).
 *  - `List` payload is a JSON object (even empty) → [WireMessage.ListResponse] (server→client).
 *  - `List` payload is anything else (`null`, array, primitive) →
 *    [WireMessage.ListRequest] (client→server).
 *
 * The same deserializer works on both sides — the wire shapes don't collide.
 */
object WireMessageDeserializer : JsonContentPolymorphicSerializer<WireMessage>(WireMessage::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<WireMessage> {
        // `element.jsonObject` would throw IllegalArgumentException for non-object inputs
        // (literals, arrays, garbage). Translate into a SerializationException so callers
        // (NetworkManager.handlePacket / ClientConnection.handlePacket) catch and recover.
        if (element !is JsonObject) {
            throw SerializationException("Wire message must be a JSON object, got ${element::class.simpleName}")
        }
        val obj: JsonObject = element
        return when (val key = obj.keys.firstOrNull()) {
            "Hello" -> WireMessage.Hello.serializer()
            "State" -> WireMessage.State.serializer()
            "Set" -> WireMessage.Set.serializer()
            "TLS" -> WireMessage.TLS.serializer()
            "Error" -> WireMessage.Error.serializer()
            "List" -> if (obj["List"] is JsonObject) {
                WireMessage.ListResponse.serializer()
            } else {
                WireMessage.ListRequest.serializer()
            }
            "Chat" -> {
                val payload = obj["Chat"]
                if (payload is JsonPrimitive && payload.isString) WireMessage.ChatRequest.serializer()
                else WireMessage.ChatBroadcast.serializer()
            }
            else -> throw SerializationException("Unknown wire message type: $key")
        }
    }
}
