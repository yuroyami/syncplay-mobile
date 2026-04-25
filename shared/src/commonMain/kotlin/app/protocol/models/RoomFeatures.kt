package app.protocol.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Feature flags exchanged in `Hello.features` (both directions). The default values are
 * sensible client-side claims; the server's response overwrites them at handshake time.
 *
 * Both client and server populate a different (overlapping) subset of these fields, so
 * every property has a default — the encoder's `explicitNulls = false` keeps absent
 * fields off the wire and matches the python protocol's behaviour.
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
    val maxChatMessageLength: Int = 150,
    val maxUsernameLength: Int = 16,
    val maxRoomNameLength: Int = 35,
    val maxFilenameLength: Int = 250
)
