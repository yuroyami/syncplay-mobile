package app.protocol.wire

import app.protocol.models.RoomFeatures
import kotlinx.serialization.Serializable

/**
 * Inner payload of a `Set` message — multi-purpose envelope. Each direction populates a
 * different subset of these fields:
 *
 * - Server→client: [user] broadcasts of joins/leaves/file changes,
 *   [playlistChange]/[playlistIndex], [newControlledRoom], [controllerAuth] response,
 *   [ready] state, [features].
 * - Client→server: [room] for room change, [file] for setting own file,
 *   [controllerAuth] auth attempt, [ready] for own readiness,
 *   [playlistChange]/[playlistIndex], [features].
 */
@Serializable
data class SetData(
    /** Server→client user broadcast: `{username -> UserSetData}`. */
    val user: Map<String, UserSetData>? = null,
    /** Client→server room change request. */
    val room: Room? = null,
    /** Client→server: own file metadata. */
    val file: FileData? = null,
    val controllerAuth: ControllerAuthData? = null,
    val newControlledRoom: NewControlledRoom? = null,
    val ready: ReadyData? = null,
    val playlistIndex: PlaylistIndexData? = null,
    val playlistChange: PlaylistChangeData? = null,
    val features: RoomFeatures? = null
)
