package app.protocol.wire

import app.protocol.models.LenientRoomFeaturesSerializer
import app.protocol.models.RoomFeatures
import kotlinx.serialization.Serializable

/**
 * Per-user payload inside a server `List` response.
 *
 * @property position The user's playback position in seconds. The reference server always sends
 *   0 here; the app's own server sends the last reported position, carried forward while the
 *   room plays (0 when unknown).
 * @property isReady Whether the user is marked as ready (null if readiness is disabled).
 * @property file File metadata if the user has a file loaded.
 * @property controller Whether the user has controller privileges in a managed room.
 * @property features Feature flags reported by that user.
 */
@Serializable
data class ListUserData(
    val position: Double? = null,
    val isReady: Boolean? = null,
    val file: FileData? = null,
    val controller: Boolean = false,
    @Serializable(with = LenientRoomFeaturesSerializer::class)
    val features: RoomFeatures? = null
)
