package app.protocol.wire

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
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
        return (element as? JsonPrimitive)?.content
            ?: error("Expected JSON primitive for FileData.size, got: $element")
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}
