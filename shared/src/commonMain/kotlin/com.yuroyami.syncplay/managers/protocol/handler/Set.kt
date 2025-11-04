package com.yuroyami.syncplay.managers.protocol.handler

import com.yuroyami.syncplay.managers.protocol.creator.PacketCreator
import com.yuroyami.syncplay.utils.loggy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class Set(
    @SerialName("Set") val set: SetData
): SyncplayMessage {

    context(packetHandler: PacketHandler)
    override suspend fun handle() {
        with(packetHandler) {
            when {
                set.user != null -> handleUserSet(set.user)
                set.playlistIndex != null -> handlePlaylistIndex(set.playlistIndex)
                set.playlistChange != null -> handlePlaylistChange(set.playlistChange)
                // Handle other set types as needed
            }
        }

        // Fetch a list of users anyway
        packetHandler.sender.send<PacketCreator.EmptyList>()
    }

    @Serializable
    data class SetData(
        val user: JsonObject? = null,
        val room: JsonElement? = null,
        val controllerAuth: JsonElement? = null,
        val newControlledRoom: JsonElement? = null,
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
        val files: kotlin.collections.List<String>? = null
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


    private fun PacketHandler.handleUserSet(userObject: JsonObject) {
        val userName = userObject.keys.firstOrNull() ?: return

        try {
            val userData = handlerJson.decodeFromString<UserEventData>(userObject[userName].toString())

            userData.event?.let { event ->
                when {
                    event.left != null -> callback.onSomeoneLeft(userName)
                    event.joined != null -> callback.onSomeoneJoined(userName)
                    else -> {}
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

    private fun PacketHandler.handlePlaylistIndex(playlistIndex: PlaylistIndexData) {
        val user = playlistIndex.user ?: return
        val index = playlistIndex.index ?: return

        callback.onPlaylistIndexChanged(user, index)
        viewmodel.session.spIndex.intValue = index
    }

    private fun PacketHandler.handlePlaylistChange(playlistChange: PlaylistChangeData) {
        val user = playlistChange.user ?: ""
        val files = playlistChange.files ?: return

        viewmodel.session.sharedPlaylist.clear()
        viewmodel.session.sharedPlaylist.addAll(files)
        callback.onPlaylistUpdated(user)
    }
}