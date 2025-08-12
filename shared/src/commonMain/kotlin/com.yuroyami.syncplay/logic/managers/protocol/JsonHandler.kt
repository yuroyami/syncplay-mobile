package com.yuroyami.syncplay.logic.managers.protocol

import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.models.User
import com.yuroyami.syncplay.protocol.SyncplayProtocol
import com.yuroyami.syncplay.logic.managers.protocol.PingService
import com.yuroyami.syncplay.utils.loggy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.collections.iterator
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.Instant

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
    val size: Long? = null
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
data class StateData(
    val ping: PingData? = null,
    val ignoringOnTheFly: IgnoringOnTheFlyData? = null,
    val playstate: PlaystateData? = null
)

@Serializable
data class PingData(
    val latencyCalculation: Double? = null,
    val clientLatencyCalculation: Double? = null,
    val serverRtt: Double? = null
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
data class TLSData(
    val startTLS: Boolean? = null
)

@Serializable
data class ErrorData(
    // Define error structure as needed
    val message: String? = null
)


object JsonHandler {

    val pingService = PingService()

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
                        withContext(Dispatchers.Main) {
                            handleState(message.State, protocol, jsonString)
                        }
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
                loggy("Serialization error: ${e.message}")
                throw e
            }
    }

    private suspend fun handleHello(hello: HelloData, p: SyncplayProtocol) {
        hello.username?.let { username ->
            p.session.currentUsername = username
        }
        p.send<Packet.Joined> {
            roomname = p.session.currentRoom
        }.await()
        p.send<Packet.EmptyList>().await()
        p.syncplayCallback.onConnected()
    }

    private suspend fun handleSet(set: SetData, p: SyncplayProtocol) {
        when {
            set.user != null -> handleUserSet(set.user, p)
            set.playlistIndex != null -> handlePlaylistIndex(set.playlistIndex, p)
            set.playlistChange != null -> handlePlaylistChange(set.playlistChange, p)
            // Handle other set types as needed
        }

        // Fetch a list of users anyway
        p.send<Packet.EmptyList>().await()
    }

    private fun handleUserSet(userObject: JsonObject, p: SyncplayProtocol) {
        val userName = userObject.keys.firstOrNull() ?: return

        try {
            val userData = json.decodeFromString<UserEventData>(userObject[userName].toString())

            userData.event?.let { event ->
                when {
                    event.left != null -> p.syncplayCallback.onSomeoneLeft(userName)
                    event.joined != null -> p.syncplayCallback.onSomeoneJoined(userName)
                    else -> {}
                }
            }

            userData.file?.let { file ->
                p.syncplayCallback.onSomeoneLoadedFile(
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

    private fun handlePlaylistIndex(playlistIndex: PlaylistIndexData, p: SyncplayProtocol) {
        val user = playlistIndex.user ?: return
        val index = playlistIndex.index ?: return

        p.syncplayCallback.onPlaylistIndexChanged(user, index)
        p.session.spIndex.intValue = index
    }

    private fun handlePlaylistChange(playlistChange: PlaylistChangeData, p: SyncplayProtocol) {
        val user = playlistChange.user ?: ""
        val files = playlistChange.files ?: return

        p.session.sharedPlaylist.clear()
        p.session.sharedPlaylist.addAll(files)
        p.syncplayCallback.onPlaylistUpdated(user)
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

        p.protoScope.launch {
            p.session.userList.emit(newList)
            p.syncplayCallback.onReceivedList()
        }
    }

    var lastGlobalUpdate: Instant? = null
    const val SEEK_THRESHOLD = 1L
    private suspend fun handleState(state: StateData, protocol: SyncplayProtocol, jsonString: String) {
        var position: Double? = null
        var paused: Boolean? = null
        var doSeek: Boolean? = null
        var setBy: String? = null

        var messageAge = 0.0
        var latencyCalculation: Double? = null

        state.ignoringOnTheFly?.let { ignoringOnTheFly ->
            if (ignoringOnTheFly.server != null) {
                protocol.serverIgnFly = ignoringOnTheFly.server
                protocol.clientIgnFly = 0
            } else if (ignoringOnTheFly.client != null) {
                if (protocol.clientIgnFly == ignoringOnTheFly.client) {
                    protocol.clientIgnFly = 0
                }
            }
        }

        state.playstate?.let { playstate ->
            position = playstate.position ?: 0.0
            paused = playstate.paused
            doSeek = playstate.doSeek
            setBy = playstate.setBy
        }

        state.ping?.let { ping ->
            latencyCalculation = ping.latencyCalculation

            ping.clientLatencyCalculation?.let { timestamp ->
                val serverRtt = ping.serverRtt ?: return@let

                pingService.receiveMessage(timestamp.roundToLong(), serverRtt)
            }
            messageAge = pingService.forwardDelay
        }

        val player = protocol.viewmodel.player

        if (position != null && paused != null && protocol.clientIgnFly == 0 && player != null) {
            val pausedChanged = protocol.globalPaused != paused || paused == player.isPlaying()
            val diff = player.currentPositionMs() / 1000.0 - position

            /* Updating Global State */
            protocol.globalPaused = paused
            protocol.globalPositionMs = position * 1000L
            if (!paused) protocol.globalPositionMs += messageAge //Account for network drift


            if (lastGlobalUpdate == null) {
                if (protocol.viewmodel.media != null) {
                    player.seekTo(position.toLong())
                    if (paused) player.pause() else player.play()
                }
            }

            lastGlobalUpdate = Clock.System.now()



            if (doSeek == true && setBy != null) {
                protocol.syncplayCallback.onSomeoneSeeked(setBy, position)
            }

            /* Rewind check if someone is behind */
            if (diff > protocol.rewindThreshold && doSeek != true /* && rewindOnDesync pref */) {
                protocol.syncplayCallback.onSomeoneBehind(setBy ?: "", position)
            }

            //if (fastforwardOnDesyncPref && (currentUser.canControl() == false or dontSlowDownWithMe == true)
            //      if (diff < (constants.FASTFORWARD_BEHIND_THRESHOLD * -1)  && doSeek != true
            //          if (behindFirstDetected == true)
            //              behindFirstDetected = now()
            //          else
            //              durationBehind = now() - behindFirstDetected
            //              if (durationBehind > if (durationBehind > (self._config['fastforwardThreshold']-constants.FASTFORWARD_BEHIND_THRESHOLD))\ and (diff < (self._config['fastforwardThreshold'] * -1))
            //                  madeChangeOnPlayer = fastforwardPlayerDueToTimeDifference(position, setBy)
            //                  behindFirstDetected = now() + constants.FASTFORWARD_RESET_THRESHOLD
            //      else behindFirstDetected = null

            //if self._player.speedSupported and not doSeek and not paused and  not self._config['slowOnDesync'] == False:
            //madeChangeOnPlayer = self._slowDownToCoverTimeDifference(diff, setBy)


            if (pausedChanged) {
                if (!paused) protocol.syncplayCallback.onSomeonePlayed(setBy ?: "")
                if (paused) protocol.syncplayCallback.onSomeonePaused(setBy ?: "")
            }
        }

        if (lastGlobalUpdate != null && player != null && position != null) {
            val playerDiff = abs(player.currentPositionMs() / 1000.0 - position)
            val globalDiff = abs(protocol.globalPositionMs / 1000.0 - position)
            val surelyPausedChanged = protocol.globalPaused != paused && paused == player.isPlaying()
            val seeked = playerDiff > SEEK_THRESHOLD && globalDiff > SEEK_THRESHOLD

            protocol.send<Packet.State> {
                serverTime = latencyCalculation
                this.doSeek = seeked
                this.position = player.currentPositionMs().div(1000L) // if dontSlowDownWithMe useGlobalPosition or else usePlayerPosition
                changeState = if (surelyPausedChanged) 1 else 0
                play = player.isPlaying()
            }
        } else {
            protocol.send<Packet.State> {
                serverTime = latencyCalculation
                this.doSeek = null
                this.position = null
                changeState = 0
                play = null
            }
        }

    }

    private fun handleChat(chat: ChatData, p: SyncplayProtocol) {
        val sender = chat.username ?: return
        val message = chat.message ?: return
        p.syncplayCallback.onChatReceived(sender, message)
    }

    private suspend fun handleTLS(tls: TLSData, p: SyncplayProtocol) {
        tls.startTLS?.let { startTLS ->
            p.syncplayCallback.onReceivedTLS(startTLS)
        }
    }

    private fun handleError(error: ErrorData, p: SyncplayProtocol) {
        // Implement error handling as needed
        error.message?.let { message ->
            loggy("Server error: $message")
        }
    }
}