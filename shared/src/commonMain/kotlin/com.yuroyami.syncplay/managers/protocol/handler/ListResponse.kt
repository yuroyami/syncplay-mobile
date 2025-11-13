package com.yuroyami.syncplay.managers.protocol.handler

import androidx.lifecycle.viewModelScope
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.models.User
import com.yuroyami.syncplay.utils.loggy
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject

/**
 * Incoming server "List" packet.
 * To ask the server for this, we simply send an empty list and it returns with the full list of users and their details.
 */
@Serializable
data class ListResponse(
    /** Dynamic JSON map of users per room. */
    @SerialName("List")
    val list: JsonObject
) : SyncplayMessage {

    /**
     * Parses the user list and updates [session.userList].
     *
     * For each user, builds a [User] object with readiness, controller state,
     * and file info if available.
     */
    context(packetHandler: PacketHandler)
    override suspend fun handle() {
        val userlist = list[packetHandler.viewmodel.session.currentRoom] as? JsonObject ?: return
        val newList = mutableListOf<User>()

        var indexer = 1

        for ((userName, userDataElement) in userlist) {
            try {
                val userData = packetHandler.handlerJson.decodeFromString<UserData>(userDataElement.toString())

                val user = User(
                    name = userName,
                    index = if (userName != packetHandler.viewmodel.session.currentUsername) indexer++ else 0,
                    readiness = userData.isReady ?: false,
                    file = userData.file?.let { fileData ->
                        if (fileData.name != null) {
                            MediaFile().apply {
                                fileName = fileData.name
                                fileDuration = fileData.duration ?: 0.0
                                fileSize = fileData.size?.toString() ?: ""
                            }
                        } else null
                    },
                    isController = userData.controller
                )

                newList.add(user)
            } catch (e: SerializationException) {
                loggy("Error parsing user list data: ${e.message}")
                throw e
            }
        }

        packetHandler.viewmodel.viewModelScope.launch {
            packetHandler.viewmodel.session.userList.emit(newList)
            packetHandler.callback.onReceivedList()
        }
    }

    /**
     * User data payload from the list packet.
     *
     * @property isReady Whether the user is marked as ready.
     * @property file File metadata if the user has a media loaded.
     * @property controller Whether the user has controller privileges.
     */
    @Serializable
    data class UserData(
        val isReady: Boolean? = null,
        val file: FileData? = null,
        val controller: Boolean = false
    )
}
