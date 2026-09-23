package app.protocol.models

import kotlinx.serialization.SerializationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject

/**
 * Feature flags exchanged in `Hello.features` (both directions). The default values are
 * sensible client-side claims; the server's response overwrites them at handshake time.
 *
 * Client and server each send a different, overlapping subset of these fields, so every
 * property has a default: a field that the peer leaves out decodes to its default.
 *
 * On the wire `features` is *supposed* to be a JSON object, but it is not always one. The
 * inbound `features` fields are decoded through [LenientRoomFeaturesSerializer], which
 * explains when a non-object shape arrives.
 */
@Serializable
data class RoomFeatures(
    val isolateRooms: Boolean = true,
    @SerialName("readiness") val supportsReadiness: Boolean = true,
    @SerialName("managedRooms") val supportsManagedRooms: Boolean = true,
    val persistentRooms: Boolean = true,
    @SerialName("chat") val supportsChat: Boolean = true,
    @SerialName("sharedPlaylists") val supportsSharedPlaylists: Boolean = true,
    val featureList: Boolean = true,
    /** Controllers may set other users' readiness (PC sets it in both client.py and server.py). */
    val setOthersReadiness: Boolean = true,
    val maxChatMessageLength: Int = 150,
    val maxUsernameLength: Int = 16,
    val maxRoomNameLength: Int = 35,
    val maxFilenameLength: Int = 250
)

/**
 * Tolerant decoder for inbound `features` values.
 *
 * The reference Python server sends an object for a real user, but its placeholder rows for
 * empty persistent rooms carry `"features": []` (`_addDummyUserOnList` in protocols.py). Other
 * servers can send `[]` or `null` for a user that reported no features. The strict generated
 * [RoomFeatures] serializer rejects an array (*"Expected object, but had array"*), and one bad
 * sub-field fails the whole `List` or `Set` line. The inbound path then skips that line, and
 * the user list misses the update (issue #152).
 *
 * Decode: a JSON object is parsed normally. Anything else (array, primitive; JSON `null` is
 * already handled by the nullable wrapper) falls back to a default [RoomFeatures]. The
 * reference server has the same rule for a client that reports no features
 * (`SyncServerProtocol.getFeatures` in protocols.py: `if not self._features:`).
 *
 * Encode: always writes the normal object form through the generated serializer, so the
 * outbound wire bytes stay the same as the Python protocol's.
 *
 * Apply it only to fields that decode untrusted inbound JSON ([app.protocol.wire.ListUserData],
 * [app.protocol.wire.SetData], [app.protocol.wire.HelloData], [app.protocol.wire.UserEvent]).
 * The class itself stays a plain `@Serializable`, so `RoomFeatures.serializer()` keeps
 * returning the generated serializer that this one delegates to (no recursion).
 */
internal object LenientRoomFeaturesSerializer : KSerializer<RoomFeatures> {
    override val descriptor: SerialDescriptor = RoomFeatures.serializer().descriptor

    override fun deserialize(decoder: Decoder): RoomFeatures {
        if (decoder !is JsonDecoder) {
            // SerializationException, not require(): it is the one type that both inbound paths
            // catch (the client skips the line, the server drops the peer with an error).
            throw SerializationException("LenientRoomFeaturesSerializer requires a JSON decoder")
        }
        val element = decoder.decodeJsonElement()
        return if (element is JsonObject) {
            decoder.json.decodeFromJsonElement(RoomFeatures.serializer(), element)
        } else {
            RoomFeatures()
        }
    }

    override fun serialize(encoder: Encoder, value: RoomFeatures) {
        encoder.encodeSerializableValue(RoomFeatures.serializer(), value)
    }
}
