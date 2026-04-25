package app.protocol.wire

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
 * value isn't meaningful — only presence is). [version] is included alongside [joined]
 * when the server reports a remote join.
 */
@Serializable
data class UserEvent(
    val joined: JsonElement? = null,
    val left: JsonElement? = null,
    val version: String? = null
)
