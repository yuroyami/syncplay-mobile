package app.protocol.wire

import app.protocol.models.LenientRoomFeaturesSerializer
import app.protocol.models.RoomFeatures
import kotlinx.serialization.Serializable

/**
 * Inner payload of a `Set` message: a multi-purpose envelope. Each direction fills a
 * different subset of these fields:
 *
 * - Server to client: [user] broadcasts of joins, leaves and file changes,
 *   [playlistChange]/[playlistIndex], [newControlledRoom], the [controllerAuth] response,
 *   [ready] state, [features].
 * - Client to server: [room] for a room change, [file] for our own file,
 *   [controllerAuth] for an auth attempt, [ready] for our own readiness,
 *   [playlistChange]/[playlistIndex], [features].
 */
@Serializable
data class SetData(
    /** Server-to-client user broadcast: `{username -> UserSetData}`. */
    val user: Map<String, UserSetData>? = null,
    /** Client-to-server room change request. */
    val room: Room? = null,
    /** Client to server: our own file metadata. */
    val file: FileData? = null,
    val controllerAuth: ControllerAuthData? = null,
    val newControlledRoom: NewControlledRoom? = null,
    val ready: ReadyData? = null,
    val playlistIndex: PlaylistIndexData? = null,
    val playlistChange: PlaylistChangeData? = null,
    /**
     * Inbound only. The app's own server reads this; nothing in this app may ever *send* it.
     *
     * The reference server's `handleSet` routes the `features` command to
     * `Watcher.setFeatures`, a method `server.py` does not define, so a
     * `Set{"features": ...}` raises AttributeError inside its reactor and drops the
     * connection. `WireMessage` deliberately offers no builder for it, and
     * `SetFeaturesIsInboundOnlyTest` keeps it that way.
     */
    @Serializable(with = LenientRoomFeaturesSerializer::class)
    val features: RoomFeatures? = null
)
