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

@Serializable
data class List(
    // Keep as JsonObject since structure is dynamic
    @SerialName("List") val list: JsonObject
): SyncplayMessage {

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
                    }
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


    @Serializable
    data class UserData(
        val isReady: Boolean? = null,
        val file: FileData? = null
    )
}
