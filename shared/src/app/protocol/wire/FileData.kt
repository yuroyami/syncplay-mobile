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
 * File metadata exchanged in `Set.file` (client to server) and inside [UserSetData] broadcasts
 * (server to client).
 *
 * `size` is polymorphic on the wire: the reference Python client sends a JSON **number** at
 * default privacy (raw byte count), a **string** when hashed, and `0` (number) when hidden.
 * [FileSizeSerializer] normalizes both shapes to [String] on decode. On encode it writes a
 * number for a value that reads as a byte count, and a string otherwise.
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
        if (decoder !is JsonDecoder) {
            // SerializationException, not require(): it is the one type that both inbound paths
            // catch (the client skips the line, the server drops the peer with an error).
            throw SerializationException("FileSizeSerializer requires a JSON decoder")
        }
        val element = decoder.decodeJsonElement()
        // SerializationException again, not error() or IllegalStateException: the server relays
        // file dicts from other clients verbatim, so a malformed `size` from any peer must fail
        // as a parse error that both inbound paths handle.
        return (element as? JsonPrimitive)?.content
            ?: throw SerializationException("Expected JSON primitive for FileData.size, got: $element")
    }

    override fun serialize(encoder: Encoder, value: String) {
        /* Match the Python wire shape: a raw byte count (and the hidden-size value 0) goes out
         * as a JSON number, and only the 12-character privacy hash is a string. PC's comparisons
         * survive a stringified number through the hash path, but its UI parses the size as an
         * int, and exact parity costs nothing here.
         *
         * A privacy hash is twelve hex characters. When all twelve are decimal digits and the
         * first is a zero (about one hash in 2800), sending it as a number drops that zero, and
         * two people with the same file are told their files differ. A real byte count never
         * starts with a zero, which is how the two are told apart. */
        val asLong = value.toLongOrNull()
        val isByteCount = asLong != null && (value == "0" || !value.startsWith("0"))
        if (encoder is JsonEncoder && isByteCount) {
            encoder.encodeJsonElement(JsonPrimitive(asLong))
        } else {
            encoder.encodeString(value)
        }
    }
}
