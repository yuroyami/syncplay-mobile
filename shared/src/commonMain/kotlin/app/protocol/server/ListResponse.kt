package app.protocol.server

import androidx.lifecycle.viewModelScope
import app.player.models.MediaFile
import app.protocol.ProtocolManager
import app.protocol.models.RoomFeatures
import app.protocol.models.User
import app.protocol.network.NetworkManager
import app.room.RoomViewmodel
import app.protocol.event.RoomCallback
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `List` packet — server's response to a `List` request, containing every room and its watchers.
 *
 * Wire shape: `{"List": {"<roomName>": {"<userName>": UserData, ...}, ...}}`.
 */
@Serializable
data class ListResponse(
    @SerialName("List")
    val list: Map<String, Map<String, UserData>>
) : ServerMessage {

    /**
     * Parses the user list and updates [session.userList] for the current room.
     * Other rooms are ignored.
     */
    override suspend fun handle(
        protocol: ProtocolManager,
        viewmodel: RoomViewmodel,
        dispatcher: NetworkManager,
        callback: RoomCallback
    ) {
        val userlist = list[protocol.session.currentRoom] ?: return
        val newList = mutableListOf<User>()

        var indexer = 1

        for ((userName, userData) in userlist) {
            val user = User(
                name = userName,
                index = if (userName != protocol.session.currentUsername) indexer++ else 0,
                readiness = userData.isReady ?: false,
                file = userData.file?.let { fileData ->
                    if (fileData.name != null) {
                        MediaFile().apply {
                            fileName = fileData.name
                            fileDuration = fileData.duration ?: 0.0
                            fileSize = fileData.size ?: ""
                        }
                    } else null
                },
                isController = userData.controller
            )

            newList.add(user)
        }

        viewmodel.viewModelScope.launch {
            protocol.session.userList.emit(newList)
            callback.onReceivedList()
        }
    }

    /**
     * Per-user payload inside a `List` response.
     *
     * @property position Last reported playback position (server-side, may be 0 for clients without media).
     * @property isReady Whether the user is marked as ready (null if readiness is disabled on the server).
     * @property file File metadata if the user has a media loaded.
     * @property controller Whether the user has controller privileges in a managed room.
     * @property features Feature flags reported by that user.
     */
    @Serializable
    data class UserData(
        val position: Double? = null,
        val isReady: Boolean? = null,
        val file: FileData? = null,
        val controller: Boolean = false,
        val features: RoomFeatures? = null
    )
}
