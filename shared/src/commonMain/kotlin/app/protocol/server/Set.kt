package app.protocol.server

import androidx.lifecycle.viewModelScope
import app.protocol.ProtocolManager
import app.protocol.event.ClientMessage
import app.protocol.models.RoomFeatures
import app.protocol.network.NetworkManager
import app.room.RoomViewmodel
import app.protocol.event.RoomCallback
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * `Set` packet — multi-purpose message for user/room/playlist/readiness/controller events.
 *
 * Each direction populates a different subset of [SetData]'s fields:
 * - Server→client: [SetData.user] broadcasts of joins/leaves/file changes,
 *   [SetData.playlistChange]/[SetData.playlistIndex], [SetData.newControlledRoom],
 *   [SetData.controllerAuth] response, [SetData.ready] state, [SetData.features].
 * - Client→server: [SetData.room] for room change, [SetData.file] for setting own file,
 *   [SetData.controllerAuth] auth attempt, [SetData.ready] for own readiness,
 *   [SetData.playlistChange]/[SetData.playlistIndex], [SetData.features].
 *
 * All fields are nullable to accommodate both shapes with a single data class.
 */
@Serializable
data class Set(
    @SerialName("Set")
    val set: SetData
) : ServerMessage {

    /** Routes to the appropriate handler based on which field is present. */
    override suspend fun handle(
        protocol: ProtocolManager,
        viewmodel: RoomViewmodel,
        dispatcher: NetworkManager,
        callback: RoomCallback
    ) {
        when {
            set.user != null -> callback.handleUserSet(set.user)
            set.playlistIndex != null -> callback.handlePlaylistIndex(set.playlistIndex)
            set.playlistChange != null -> callback.handlePlaylistChange(set.playlistChange)
            set.newControlledRoom != null -> callback.handleNewControlledRoom(set.newControlledRoom)
            set.controllerAuth != null -> callback.onHandleControllerAuth(set.controllerAuth)
        }

        dispatcher.sendAsync<ClientMessage.EmptyList>()
    }

    @Serializable
    data class SetData(
        /** Server→client user broadcast: `{username -> UserSetData}`. */
        val user: Map<String, UserSetData>? = null,
        /** Client→server room change request. */
        val room: Room? = null,
        /** Client→server: own file metadata. */
        val file: FileData? = null,
        val controllerAuth: ControllerAuthResponse? = null,
        val newControlledRoom: NewControlledRoom? = null,
        val ready: ReadyData? = null,
        val playlistIndex: PlaylistIndexData? = null,
        val playlistChange: PlaylistChangeData? = null,
        val features: RoomFeatures? = null
    )

    /** Per-user payload inside a `Set.user` server broadcast. */
    @Serializable
    data class UserSetData(
        val room: Room? = null,
        val file: FileData? = null,
        val event: UserEvent? = null
    )

    /**
     * User event flags inside [UserSetData]. Either [joined] or [left] is non-null
     * (their actual JSON value isn't meaningful — only presence is). [version] is
     * included alongside [joined] when the server reports a remote join.
     */
    @Serializable
    data class UserEvent(
        val joined: JsonElement? = null,
        val left: JsonElement? = null,
        val version: String? = null
    )

    @Serializable
    data class PlaylistIndexData(
        val user: String? = null,
        val index: Int? = null
    )

    @Serializable
    data class PlaylistChangeData(
        val user: String? = null,
        val files: List<String>? = null
    )

    /**
     * Readiness payload, used in both directions:
     * - Server→client: includes [username] (and optional [setBy] when a controller forced it).
     * - Client→server: typically just [isReady] + [manuallyInitiated]; controllers may
     *   also send a target [username].
     */
    @Serializable
    data class ReadyData(
        val username: String? = null,
        val isReady: Boolean? = null,
        val manuallyInitiated: Boolean? = null,
        val setBy: String? = null
    )

    @Serializable
    @SerialName("newControlledRoom")
    data class NewControlledRoom(
        val password: String,
        val roomName: String
    )

    /**
     * Controller-auth payload — same model for the request (client→server: `password` + `room`)
     * and the response (server→client: `user` + `room` + `success`).
     *
     * `password` is nullable so the server's response omits it on the wire (with
     * `explicitNulls = false`), matching the python reference protocol which only sends
     * `password` on the request.
     */
    @Serializable
    @SerialName("controllerAuth")
    data class ControllerAuthResponse(
        val user: String? = null,
        val room: String? = null,
        val password: String? = null,
        val success: Boolean = false
    )

    private fun RoomCallback.handleUserSet(userMap: Map<String, UserSetData>) {
        val (userName, userData) = userMap.entries.firstOrNull() ?: return

        userData.event?.let { event ->
            when {
                event.left != null -> onSomeoneLeft(userName)
                event.joined != null -> onSomeoneJoined(userName)
            }
        }

        userData.file?.let { file ->
            onSomeoneLoadedFile(userName, file.name ?: "", file.duration ?: 0.0)
        }
    }

    private fun RoomCallback.handlePlaylistIndex(playlistIndex: PlaylistIndexData) {
        val user = playlistIndex.user ?: return
        val index = playlistIndex.index ?: return
        onPlaylistIndexChanged(user, index)
        viewmodel.session.spIndex.intValue = index
    }

    private fun RoomCallback.handlePlaylistChange(playlistChange: PlaylistChangeData) {
        val user = playlistChange.user ?: ""
        val files = playlistChange.files ?: return
        viewmodel.session.sharedPlaylist.clear()
        viewmodel.session.sharedPlaylist.addAll(files)
        onPlaylistUpdated(user)
    }

    private suspend fun RoomCallback.handleNewControlledRoom(data: NewControlledRoom) {
        try {
            onNewControlledRoom(data)
            network.send<ClientMessage.RoomChange> { room = data.roomName }
            network.sendAsync<ClientMessage.EmptyList>()
            network.send<ClientMessage.ControllerAuth> {
                room = data.roomName
                password = data.password
            }
        } finally {
            viewmodel.viewModelScope.launch {
                delay(1000)
                viewmodel.protocol.isRoomChanging = false
            }
        }
    }
}
