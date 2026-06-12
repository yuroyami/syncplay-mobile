package app.protocol.wire

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive

/**
 * File metadata exchanged in `Set.file` (client→server) and inside [UserSetData] broadcasts
 * (server→client).
 *
 * `size` is polymorphic on the wire: the reference Python client emits a JSON **number** at
 * default privacy (raw byte count), a **string** when hashed, and `0` (number) when hidden.
 * [FileSizeSerializer] normalizes both shapes to [String] on decode and always emits a string
 * on encode.
 */
@Serializable
data class FileData(
    val name: String? = null,
    val duration: Double? = null,
    @Serializable(with = FileSizeSerializer::class)
    val size: String? = null
)

/**
 * Accepts a JSON number or string for `size` and normalizes to [String]. See [FileData].
 */
internal object FileSizeSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FileSize", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        require(decoder is JsonDecoder) { "FileSizeSerializer requires a JSON decoder" }
        val element = decoder.decodeJsonElement()
        // Must be SerializationException, not error()/IllegalStateException: file dicts are
        // relayed verbatim from other clients by the server, and NetworkManager.handlePacket's
        // skip-a-poisoned-line catch only covers SerializationException — anything else
        // escapes the catch and kills the process instead of skipping the message.
        return (element as? JsonPrimitive)?.content
            ?: throw SerializationException("Expected JSON primitive for FileData.size, got: $element")
    }

    override fun serialize(encoder: Encoder, value: String) {
        // Match the python wire shape: a raw byte count (and the hidden-size sentinel 0)
        // goes out as a JSON number, only the 12-char privacy hash is a string. PC's
        // comparisons survive a stringified number via the hash path, but its UI parses
        // the size as an int — and exact parity costs nothing here.
        val asLong = value.toLongOrNull()
        if (encoder is JsonEncoder && asLong != null) {
            encoder.encodeJsonElement(JsonPrimitive(asLong))
        } else {
            encoder.encodeString(value)
        }
    }
}
