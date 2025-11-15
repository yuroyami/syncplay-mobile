package com.yuroyami.syncplay.managers.protocol.handler

import androidx.lifecycle.viewModelScope
import com.yuroyami.syncplay.managers.protocol.creator.PacketOut
import com.yuroyami.syncplay.utils.loggy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Incoming server "Set" packet.
 *
 * Used to update user states, playlists, and controller/room info.
 */
@Serializable
data class Set(
    @SerialName("Set")
    val set: SetData
) : SyncplayMessage {

    /**
     * Routes the received "Set" packet to the appropriate handler
     * based on which field is present.
     */
    context(packetHandler: PacketHandler)
    override suspend fun handle() {
        with(packetHandler) {
            when {
                set.user != null -> handleUserSet(set.user)
                set.playlistIndex != null -> handlePlaylistIndex(set.playlistIndex)
                set.playlistChange != null -> handlePlaylistChange(set.playlistChange)
                set.newControlledRoom != null -> handleNewControlledRoom(set.newControlledRoom)
                set.controllerAuth != null -> callback.onHandleControllerAuth(set.controllerAuth)
            }
        }
    }

    /**
     * Root payload for "Set" packet.
     */
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

    /**
     * Playlist index update.
     *
     * @property user User who changed the index.
     * @property index New selected index.
     */
    @Serializable
    data class PlaylistIndexData(
        val user: String? = null,
        val index: Int? = null
    )

    /**
     * Playlist content update.
     *
     * @property user User who made the change.
     * @property files New playlist file list.
     */
    @Serializable
    data class PlaylistChangeData(
        val user: String? = null,
        val files: kotlin.collections.List<String>? = null
    )

    /**
     * Handles a user join/leave/file event.
     */
    private fun PacketHandler.handleUserSet(userObject: JsonObject) {
        val userName = userObject.keys.firstOrNull() ?: return

        try {
            val userData = handlerJson.decodeFromString<UserEventData>(userObject[userName].toString())

            userData.event?.let { event ->
                when {
                    event.left != null -> callback.onSomeoneLeft(userName)
                    event.joined != null -> callback.onSomeoneJoined(userName)
                }
            }

            userData.file?.let { file ->
                callback.onSomeoneLoadedFile(
                    userName,
                    file.name ?: "",
                    file.duration ?: 0.0
                )
            }
        } catch (e: SerializationException) {
            loggy("Error parsing user data: ${e.message}")
            throw e
        }
    }

    /**
     * User event payload.
     */
    @Serializable
    data class UserEventData(
        val event: UserEvent? = null,
        val file: FileData? = null
    )

    /**
     * User join/leave markers.
     */
    @Serializable
    data class UserEvent(
        val left: JsonElement? = null,
        val joined: JsonElement? = null
    )

    /**
     * Handles a playlist index change.
     */
    private fun PacketHandler.handlePlaylistIndex(playlistIndex: PlaylistIndexData) {
        val user = playlistIndex.user ?: return
        val index = playlistIndex.index ?: return

        callback.onPlaylistIndexChanged(user, index)
        viewmodel.session.spIndex.intValue = index
    }

    /**
     * Handles a playlist content change.
     */
    private fun PacketHandler.handlePlaylistChange(playlistChange: PlaylistChangeData) {
        val user = playlistChange.user ?: ""
        val files = playlistChange.files ?: return

        viewmodel.session.sharedPlaylist.clear()
        viewmodel.session.sharedPlaylist.addAll(files)
        callback.onPlaylistUpdated(user)
    }

    /**
     * Response for newly created controlled room.
     */
    @Serializable
    @SerialName("newControlledRoom")
    data class NewControlledRoom(
        val password: String,
        val roomName: String
    )

    /**
     * Controller authentication response.
     */
    @Serializable
    @SerialName("controllerAuth")
    data class ControllerAuthResponse(
        val user: String? = null,
        val room: String,
        val password: String = "",
        val success: Boolean
    )

    /**
     * Handles creation of a new controlled room and follows up
     * with room change, list fetch, and authentication sequence.
     */
    private suspend fun PacketHandler.handleNewControlledRoom(data: NewControlledRoom) {
        try {
            callback.onNewControlledRoom(data)

            viewmodel.networkManager.send<PacketOut.RoomChange> {
                room = data.roomName
            }

            viewmodel.networkManager.sendAsync<PacketOut.EmptyList>()

            viewmodel.networkManager.send<PacketOut.ControllerAuth> {
                room = data.roomName
                password = data.password
            }
        } finally {
            viewmodel.viewModelScope.launch {
                delay(1000)
                viewmodel.protocolManager.isRoomChanging = false
            }
        }
    }
}
