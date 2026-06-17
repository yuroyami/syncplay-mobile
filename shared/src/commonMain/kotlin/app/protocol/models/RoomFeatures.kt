package app.protocol.models

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
 * Both client and server populate a different (overlapping) subset of these fields, so
 * every property has a default — the encoder's `explicitNulls = false` keeps absent
 * fields off the wire and matches the python protocol's behaviour.
 *
 * On the wire `features` is *supposed* to be a JSON object, but it isn't always: the
 * inbound `features` fields are decoded through [LenientRoomFeaturesSerializer] because
 * some servers emit a non-object shape — see that serializer for the why.
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
    /** Controllers may set other users' readiness (PC: client.py:746, server.py:100). */
    val setOthersReadiness: Boolean = true,
    val maxChatMessageLength: Int = 150,
    val maxUsernameLength: Int = 16,
    val maxRoomNameLength: Int = 35,
    val maxFilenameLength: Int = 250
)

/**
 * Tolerant decoder for inbound `features` values.
 *
 * The reference python server always sends an object, but it is not the only server out
 * there: minimal / older / third-party server implementations send an empty array `[]`
 * (or `null`) for a user that reported no features. The strict generated [RoomFeatures]
 * serializer rejects an array (*"Expected object, but had array"*), and one bad sub-field
 * aborts the whole `List`/`Set` decode, which would blank the user-info tab and spin the
 * reconnect loop (issue #152).
 *
 * Decode: a JSON object is parsed normally; anything else (array, primitive; JSON `null`
 * is already handled by the nullable wrapper) falls back to default [RoomFeatures] — which
 * mirrors the python client's own "no features reported → computed defaults" behaviour
 * (`protocols.py`: `if not self._features: self._features = {…}`).
 *
 * Encode: always emits the normal object form via the generated serializer, so outbound
 * wire bytes stay identical to the python protocol.
 *
 * Apply only to fields that decode untrusted inbound JSON ([app.protocol.wire.ListUserData],
 * [app.protocol.wire.SetData], [app.protocol.wire.HelloData]). The class itself stays a
 * plain `@Serializable`, so `RoomFeatures.serializer()` keeps returning the generated
 * serializer this one delegates to (no recursion).
 */
internal object LenientRoomFeaturesSerializer : KSerializer<RoomFeatures> {
    override val descriptor: SerialDescriptor = RoomFeatures.serializer().descriptor

    override fun deserialize(decoder: Decoder): RoomFeatures {
        require(decoder is JsonDecoder) { "LenientRoomFeaturesSerializer requires a JSON decoder" }
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
