package com.yuroyami.syncplay.protocol.parsing

import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.models.User
import com.yuroyami.syncplay.protocol.JsonSender
import com.yuroyami.syncplay.protocol.SyncplayProtocol
import com.yuroyami.syncplay.utils.generateTimestampMillis
import com.yuroyami.syncplay.utils.loggy
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// Top-level message containers
@Serializable
data class HelloMessage(val Hello: HelloData)

@Serializable
data class SetMessage(val Set: SetData)

@Serializable
data class ListMessage(val List: JsonObject) // Keep as JsonObject since structure is dynamic

@Serializable
data class StateMessage(val State: StateData)

@Serializable
data class ChatMessage(val Chat: ChatData)

@Serializable
data class ErrorMessage(val Error: ErrorData)

@Serializable
data class TLSMessage(val TLS: TLSData)

// Data structures for each message type
@Serializable
data class HelloData(
    val username: String? = null
)

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
data class FileData(
    val name: String? = null,
    val duration: Double? = null,
    val size: String? = null
)

@Serializable
data class StateData(
    val ping: PingData? = null,
    val ignoringOnTheFly: IgnoringOnTheFlyData? = null,
    val playstate: PlaystateData? = null
)

@Serializable
data class PingData(
    val latencyCalculation: Double? = null
)

@Serializable
data class IgnoringOnTheFlyData(
    val server: Int? = null,
    val client: Int? = null
)

@Serializable
data class PlaystateData(
    val doSeek: Boolean? = null,
    val position: Double? = null,
    val setBy: String? = null,
    val paused: Boolean? = null
)

@Serializable
data class UserData(
    val isReady: Boolean? = null,
    val file: FileData? = null
)

@Serializable
data class ChatData(
    val username: String? = null,
    val message: String? = null
)

@Serializable
data class TLSData(
    val startTLS: Boolean? = null
)

@Serializable
data class ErrorData(
    // Define error structure as needed
    val message: String? = null
)


object JsonHandler {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    suspend fun parse(protocol: SyncplayProtocol, jsonString: String) {
            loggy("**SERVER** $jsonString")

            try {
                // Parse the JSON to determine message type
                val parsed = Json.parseToJsonElement(jsonString)
                val element = parsed as JsonObject

                when {
                    element.containsKey("Hello") -> {
                        val message = json.decodeFromString<HelloMessage>(jsonString)
                        handleHello(message.Hello, protocol)
                    }
                    element.containsKey("Set") -> {
                        val message = json.decodeFromString<SetMessage>(jsonString)
                        handleSet(message.Set, protocol)
                    }
                    element.containsKey("List") -> {
                        val message = json.decodeFromString<ListMessage>(jsonString)
                        handleList(message.List, protocol)
                    }
                    element.containsKey("State") -> {
                        val message = json.decodeFromString<StateMessage>(jsonString)
                        handleState(message.State, protocol, jsonString)
                    }
                    element.containsKey("Chat") -> {
                        val message = json.decodeFromString<ChatMessage>(jsonString)
                        handleChat(message.Chat, protocol)
                    }
                    element.containsKey("Error") -> {
                        val message = json.decodeFromString<ErrorMessage>(jsonString)
                        handleError(message.Error, protocol)
                    }
                    element.containsKey("TLS") -> {
                        val message = json.decodeFromString<TLSMessage>(jsonString)
                        handleTLS(message.TLS, protocol)
                    }
                    else -> loggy("Dropped error: unknown-command-server-error")
                }
            } catch (e: SerializationException) {
                loggy("Serialization error: ${e.message}", 1)
            } catch (e: Exception) {
                loggy(e.stackTraceToString(), 1)
            }
    }

    private suspend fun handleHello(hello: HelloData, p: SyncplayProtocol) {
        hello.username?.let { username ->
            p.session.currentUsername = username
        }

        p.sendPacket(JsonSender.sendJoined(p.session.currentRoom))
        p.sendPacket(JsonSender.sendEmptyList())
        p.syncplayCallback?.onConnected()
    }

    private suspend fun handleSet(set: SetData, p: SyncplayProtocol) {
        when {
            set.user != null -> handleUserSet(set.user, p)
            set.playlistIndex != null -> handlePlaylistIndex(set.playlistIndex, p)
            set.playlistChange != null -> handlePlaylistChange(set.playlistChange, p)
            // Handle other set types as needed
        }

        // Fetch a list of users anyway
        p.sendPacket(JsonSender.sendEmptyList())
    }

    private fun handleUserSet(userObject: JsonObject, p: SyncplayProtocol) {
        val userName = userObject.keys.firstOrNull() ?: return

        try {
            val userData = json.decodeFromString<UserEventData>(userObject[userName].toString())

            userData.event?.let { event ->
                when {
                    event.left != null -> p.syncplayCallback?.onSomeoneLeft(userName)
                    event.joined != null -> p.syncplayCallback?.onSomeoneJoined(userName)
                }
            }

            userData.file?.let { file ->
                p.syncplayCallback?.onSomeoneLoadedFile(
                    userName,
                    file.name ?: "",
                    file.duration ?: 0.0
                )
            }
        } catch (e: SerializationException) {
            loggy("Error parsing user data: ${e.message}", 1)
        }
    }

    private fun handlePlaylistIndex(playlistIndex: PlaylistIndexData, p: SyncplayProtocol) {
        val user = playlistIndex.user ?: return
        val index = playlistIndex.index ?: return

        p.syncplayCallback?.onPlaylistIndexChanged(user, index)
        p.session.spIndex.intValue = index
    }

    private fun handlePlaylistChange(playlistChange: PlaylistChangeData, p: SyncplayProtocol) {
        val user = playlistChange.user ?: ""
        val files = playlistChange.files ?: return

        p.session.sharedPlaylist.clear()
        p.session.sharedPlaylist.addAll(files)
        p.syncplayCallback?.onPlaylistUpdated(user)
    }

    private fun handleList(list: JsonObject, p: SyncplayProtocol) {
        val userlist = list[p.session.currentRoom] as? JsonObject ?: return
        val newList = mutableListOf<User>()

        var indexer = 1

        for ((userName, userDataElement) in userlist) {
            try {
                val userData = json.decodeFromString<UserData>(userDataElement.toString())

                val user = User(
                    name = userName,
                    index = if (userName != p.session.currentUsername) indexer++ else 0,
                    readiness = userData.isReady ?: false,
                    file = userData.file?.let { fileData ->
                        if (fileData.name != null) {
                            MediaFile().apply {
                                fileName = fileData.name
                                fileDuration = fileData.duration ?: 0.0
                                fileSize = fileData.size ?: ""
                            }
                        } else null
                    }
                )

                newList.add(user)
            } catch (e: SerializationException) {
                loggy("Error parsing user list data: ${e.message}", 1)
            }
        }

        p.protoScope.launch {
            p.session.userList.emit(newList)
            p.syncplayCallback?.onReceivedList()
        }
    }

    private suspend fun handleState(state: StateData, protocol: SyncplayProtocol, jsonString: String) {
        val clientTime = generateTimestampMillis() / 1000.0
        val latency = state.ping?.latencyCalculation

        state.ignoringOnTheFly?.let { ignoringOnTheFly ->
            if (jsonString.contains("server\":")) {
                val playstate = state.playstate ?: return
                val doSeek = playstate.doSeek
                val position = playstate.position ?: return
                val setBy = playstate.setBy ?: ""

                when (doSeek) {
                    true -> protocol.syncplayCallback?.onSomeoneSeeked(setBy, position)
                    false, null -> {
                        val isPaused = playstate.paused ?: jsonString.contains("\"paused\": true", true)
                        protocol.paused = isPaused

                        if (!protocol.paused) {
                            protocol.syncplayCallback?.onSomeonePlayed(setBy)
                        } else {
                            protocol.syncplayCallback?.onSomeonePaused(setBy)
                        }
                    }
                }

                protocol.serverIgnFly = ignoringOnTheFly.server ?: 0
                protocol.clientIgnFly = 0

                if (!jsonString.contains("client\":")) {
                    protocol.sendPacket(
                        JsonSender.sendState(
                            latency, clientTime, null, 0,
                            iChangeState = 0, play = null
                        )
                    )
                } else {
                    protocol.sendPacket(
                        JsonSender.sendState(
                            latency, clientTime, doSeek, 0, 0, null
                        )
                    )
                }
            }
        } ?: run {
            // Handle case without ignoringOnTheFly
            val playstate = state.playstate
            val position = playstate?.position
            val positionOf = playstate?.setBy?.takeIf { it.isNotEmpty() }

            // Rewind check if someone is behind
            if (positionOf != null && positionOf != protocol.session.currentUsername && position != null) {
                if (position < (protocol.currentVideoPosition - protocol.rewindThreshold)) {
                    protocol.syncplayCallback?.onSomeoneBehind(positionOf, position)
                }
            }

            // Constant traditional pinging
            protocol.sendPacket(
                JsonSender.sendState(
                    servertime = latency,
                    clienttime = clientTime,
                    doSeek = false,
                    seekPosition = 0,
                    iChangeState = 0,
                    play = null
                )
            )
        }
    }

    private fun handleChat(chat: ChatData, p: SyncplayProtocol) {
        val sender = chat.username ?: return
        val message = chat.message ?: return
        p.syncplayCallback?.onChatReceived(sender, message)
    }

    private fun handleTLS(tls: TLSData, p: SyncplayProtocol) {
        tls.startTLS?.let { startTLS ->
            p.syncplayCallback?.onReceivedTLS(startTLS)
        }
    }

    private fun handleError(error: ErrorData, p: SyncplayProtocol) {
        // Implement error handling as needed
        error.message?.let { message ->
            loggy("Server error: $message", 1)
        }
    }
}