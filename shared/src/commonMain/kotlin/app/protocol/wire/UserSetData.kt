package app.protocol.wire

import app.protocol.models.LenientRoomFeaturesSerializer
import app.protocol.models.RoomFeatures
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Per-user payload inside a `Set.user` server broadcast.
 *
 * The server emits `{"Set": {"user": {"<username>": UserSetData, ...}}}` — this models
 * the inner per-user object.
 */
@Serializable
data class UserSetData(
    val room: Room? = null,
    val file: FileData? = null,
    val event: UserEvent? = null
)

/**
 * Event flags inside [UserSetData]. Either [joined] or [left] is non-null (their JSON
 * value isn't meaningful — only presence is). [version] and [features] are included
 * alongside [joined] when the server reports a remote join (PC server.py:167 sends
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
