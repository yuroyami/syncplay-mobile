package app.protocol

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Picks the [WireMessage] subtype to decode from the top-level JSON key and, for the two
 * direction-asymmetric keys, from the payload shape under it:
 *
 *  - `Chat` payload is a string → [WireMessage.ChatRequest] (client to server).
 *  - `Chat` payload is anything else (object) → [WireMessage.ChatBroadcast] (server to client).
 *  - `List` payload is a JSON object (even empty) → [WireMessage.ListResponse] (server to
 *    client).
 *  - `List` payload is anything else (`null`, array, primitive) →
 *    [WireMessage.ListRequest] (client to server).
 *
 * The same deserializer works on both sides, because the wire shapes do not collide.
 */
object WireMessageDeserializer : JsonContentPolymorphicSerializer<WireMessage>(WireMessage::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<WireMessage> {
        // `element.jsonObject` would throw IllegalArgumentException for non-object inputs
        // (literals, arrays, garbage). A SerializationException instead is what both inbound
        // paths (NetworkManager.processPacket, ClientConnection.handlePacket) catch.
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
            // The key came from a peer and can be as long as a whole frame; the message it
            // ends up in is logged twice and shown once.
            else -> throw SerializationException("Unknown wire message type: ${key?.take(UNKNOWN_KEY_MAX)}")
        }
    }
}

/** How much of a peer-supplied key a parse error may quote. Enough to identify, not to flood. */
private const val UNKNOWN_KEY_MAX = 40
