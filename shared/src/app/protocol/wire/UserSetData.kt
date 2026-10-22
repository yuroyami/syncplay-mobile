package app.protocol.wire

import app.protocol.models.LenientRoomFeaturesSerializer
import app.protocol.models.RoomFeatures
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Per-user payload inside a `Set.user` server broadcast.
 *
 * The server sends `{"Set": {"user": {"<username>": UserSetData, ...}}}`, and this class models
 * the inner per-user object.
 */
@Serializable
data class UserSetData(
    val room: Room? = null,
    val file: FileData? = null,
    val event: UserEvent? = null
)

/**
 * Event flags inside [UserSetData]. Either [joined] or [left] is non-null (only their
 * presence matters, not their JSON value). [version] and [features] come with [joined] when
 * the server reports a remote join (PC's `sendJoinMessage` in server.py sends
 * `{"joined": True, "version": ..., "features": ...}`).
 */
@Serializable
data class UserEvent(
    val joined: JsonElement? = null,
    val left: JsonElement? = null,
    val version: String? = null,
    @Serializable(with = LenientRoomFeaturesSerializer::class)
    val features: RoomFeatures? = null
)
