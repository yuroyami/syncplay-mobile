package app.protocol.server

import androidx.lifecycle.viewModelScope
import app.protocol.ProtocolManager
import app.protocol.ProtocolManager.Companion.serverJson
import app.protocol.models.ClientMessage
import app.protocol.network.NetworkManager
import app.room.RoomViewmodel
import app.protocol.event.RoomEventHandler
import app.utils.loggy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Incoming server "Set" packet. Updates user states, playlists, and controller/room info. */
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
        callback: RoomEventHandler
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
        val user: JsonObject? = null,
        val room: JsonElement? = null,
        val controllerAuth: ControllerAuthResponse? = null,
        val newControlledRoom: NewControlledRoom? = null,
        val ready: JsonElement? = null,
        val playlistIndex: PlaylistIndexData? = null,
        val playlistChange: PlaylistChangeData? = null,
        val features: JsonElement? = null
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

    @Serializable
    data class UserEventData(
        val event: UserEvent? = null,
        val file: FileData? = null
    )

    @Serializable
    data class UserEvent(
        val left: JsonElement? = null,
        val joined: JsonElement? = null
    )

    @Serializable
    @SerialName("newControlledRoom")
    data class NewControlledRoom(
        val password: String,
        val roomName: String
    )

    @Serializable
    @SerialName("controllerAuth")
    data class ControllerAuthResponse(
        val user: String? = null,
        val room: String,
        val password: String = "",
        val success: Boolean
    )

    private fun RoomEventHandler.handleUserSet(userObject: JsonObject) {
        val userName = userObject.keys.firstOrNull() ?: return

        try {
            val userData = serverJson.decodeFromString<UserEventData>(userObject[userName].toString())

            userData.event?.let { event ->
                when {
                    event.left != null -> onSomeoneLeft(userName)
                    event.joined != null -> onSomeoneJoined(userName)
                }
            }

            userData.file?.let { file ->
                onSomeoneLoadedFile(userName, file.name ?: "", file.duration ?: 0.0)
            }
        } catch (e: SerializationException) {
            loggy("Error parsing user data: ${e.message}")
            throw e
        }
    }

    private fun RoomEventHandler.handlePlaylistIndex(playlistIndex: PlaylistIndexData) {
        val user = playlistIndex.user ?: return
        val index = playlistIndex.index ?: return
        onPlaylistIndexChanged(user, index)
        viewmodel.session.spIndex.intValue = index
    }

    private fun RoomEventHandler.handlePlaylistChange(playlistChange: PlaylistChangeData) {
        val user = playlistChange.user ?: ""
        val files = playlistChange.files ?: return
        viewmodel.session.sharedPlaylist.clear()
        viewmodel.session.sharedPlaylist.addAll(files)
        onPlaylistUpdated(user)
    }

    private suspend fun RoomEventHandler.handleNewControlledRoom(data: NewControlledRoom) {
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