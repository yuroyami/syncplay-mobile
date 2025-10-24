package com.yuroyami.syncplay.managers.protocol

import androidx.lifecycle.viewModelScope
import com.yuroyami.syncplay.managers.ProtocolManager
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.models.User
import com.yuroyami.syncplay.utils.loggy
import com.yuroyami.syncplay.viewmodels.RoomViewmodel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
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

@Serializable
sealed interface SyncplayMessage


class PacketHandler(
    val viewmodel: RoomViewmodel,
    val protocol: ProtocolManager
) {
    val callback = viewmodel.callbackManager
    val sender = viewmodel.networkManager

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true

//        serializersModule = SerializersModule {
//            polymorphic(SyncplayMessage::class) {
//                //subclass(HelloMessage::class)
//                //subclass(SetMessage::class)
//                // ... etc
//            }
//        }
    }

    suspend fun parse(jsonString: String) {
            loggy("**SERVER** $jsonString")

            try {
                // Parse the JSON to determine message type
                val parsed = Json.parseToJsonElement(jsonString)
                val element = parsed as JsonObject

                when {
                    element.containsKey("Hello") -> {
                        val message = json.decodeFromString<HelloMessage>(jsonString)
                        handleHello(message.Hello)
                    }
                    element.containsKey("Set") -> {
                        val message = json.decodeFromString<SetMessage>(jsonString)
                        handleSet(message.Set)
                    }
                    element.containsKey("List") -> {
                        val message = json.decodeFromString<ListMessage>(jsonString)
                        handleList(message.List)
                    }
                    element.containsKey("State") -> {
                        val message = json.decodeFromString<StateMessage>(jsonString)
                        withContext(Dispatchers.Main) {
                            handleState(message.State)
                        }
                    }
                    element.containsKey("Chat") -> {
                        val message = json.decodeFromString<ChatMessage>(jsonString)
                        handleChat(message.Chat)
                    }
                    element.containsKey("Error") -> {
                        val message = json.decodeFromString<ErrorMessage>(jsonString)
                        handleError(message.Error)
                    }
                    element.containsKey("TLS") -> {
                        val message = json.decodeFromString<TLSMessage>(jsonString)
                        handleTLS(message.TLS)
                    }
                    else -> loggy("Dropped error: unknown-command-server-error")
                }
            } catch (e: SerializationException) {
                loggy("Serialization error: ${e.message}")
                throw e
            }
    }

    private suspend fun handleHello(hello: HelloData) {
        hello.username?.let { username ->
            viewmodel.session.currentUsername = username
        }

       sender.send<PacketCreator.Joined> {
            roomname = viewmodel.session.currentRoom
       }

        sender.send<PacketCreator.EmptyList>()
        callback.onConnected()
    }

    private suspend fun handleSet(set: SetData) {
        when {
            set.user != null -> handleUserSet(set.user)
            set.playlistIndex != null -> handlePlaylistIndex(set.playlistIndex)
            set.playlistChange != null -> handlePlaylistChange(set.playlistChange)
            // Handle other set types as needed
        }

        // Fetch a list of users anyway
        sender.send<PacketCreator.EmptyList>()
    }

    private fun handleUserSet(userObject: JsonObject) {
        val userName = userObject.keys.firstOrNull() ?: return

        try {
            val userData = json.decodeFromString<UserEventData>(userObject[userName].toString())

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

    private fun handlePlaylistIndex(playlistIndex: PlaylistIndexData) {
        val user = playlistIndex.user ?: return
        val index = playlistIndex.index ?: return

        callback.onPlaylistIndexChanged(user, index)
        viewmodel.session.spIndex.intValue = index
    }

    private fun handlePlaylistChange(playlistChange: PlaylistChangeData) {
        val user = playlistChange.user ?: ""
        val files = playlistChange.files ?: return

        viewmodel.session.sharedPlaylist.clear()
        viewmodel.session.sharedPlaylist.addAll(files)
        callback.onPlaylistUpdated(user)
    }

    private fun handleList(list: JsonObject) {
        val userlist = list[viewmodel.session.currentRoom] as? JsonObject ?: return
        val newList = mutableListOf<User>()

        var indexer = 1

        for ((userName, userDataElement) in userlist) {
            try {
                val userData = json.decodeFromString<UserData>(userDataElement.toString())

                val user = User(
                    name = userName,
                    index = if (userName != viewmodel.session.currentUsername) indexer++ else 0,
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

        viewmodel.viewModelScope.launch {
            viewmodel.session.userList.emit(newList)
            callback.onReceivedList()
        }
    }

    var lastGlobalUpdate: Instant? = null
    val SEEK_THRESHOLD = 1L
    private suspend fun handleState(state: StateData) {
        var position: Double? = null
        var paused: Boolean? = null
        var doSeek: Boolean? = null
        var setBy: String? = null

        var messageAge = 0.0
        var latencyCalculation: Double? = null

        state.ignoringOnTheFly?.let { ignoringOnTheFly ->
            if (ignoringOnTheFly.server != null) {
                viewmodel.protocolManager.serverIgnFly = ignoringOnTheFly.server
                viewmodel.protocolManager.clientIgnFly = 0
            } else if (ignoringOnTheFly.client != null) {
                if (viewmodel.protocolManager.clientIgnFly == ignoringOnTheFly.client) {
                    viewmodel.protocolManager.clientIgnFly = 0
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

                viewmodel.protocolManager.pingService.receiveMessage(timestamp.roundToLong(), serverRtt)
            }
            messageAge = viewmodel.protocolManager.pingService.forwardDelay
        }

        if (position != null && paused != null && viewmodel.protocolManager.clientIgnFly == 0 && viewmodel.player != null) {
            val pausedChanged = viewmodel.protocolManager.globalPaused != paused || paused == viewmodel.player!!.isPlaying()
            val diff = viewmodel.player!!.currentPositionMs() / 1000.0 - position

            /* Updating Global State */
            viewmodel.protocolManager.globalPaused = paused
            protocol.globalPositionMs = position * 1000L
            if (!paused) protocol.globalPositionMs += messageAge //Account for network drift


            if (lastGlobalUpdate == null) {
                if (protocol.viewmodel.playerManager.media.value != null) {
                    viewmodel.player?.seekTo(position.toLong())
                    if (paused) viewmodel.player?.pause() else viewmodel.player?.play()
                }
            }

            lastGlobalUpdate = Clock.System.now()



            if (doSeek == true && setBy != null) {
                callback.onSomeoneSeeked(setBy, position)
            }

            /* Rewind check if someone is behind */
            if (diff > protocol.rewindThreshold && doSeek != true /* && rewindOnDesync pref */) {
                callback.onSomeoneBehind(setBy ?: "", position)
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
                if (!paused) callback.onSomeonePlayed(setBy ?: "")
                if (paused) callback.onSomeonePaused(setBy ?: "")
            }
        }

        if (lastGlobalUpdate != null && viewmodel.player != null && position != null) {
            val playerDiff = abs(viewmodel.player!!.currentPositionMs() / 1000.0 - position)
            val globalDiff = abs(protocol.globalPositionMs / 1000.0 - position)
            val surelyPausedChanged = protocol.globalPaused != paused && paused == viewmodel.player!!.isPlaying()
            val seeked = playerDiff > SEEK_THRESHOLD && globalDiff > SEEK_THRESHOLD

            sender.send<PacketCreator.State> {
                serverTime = latencyCalculation
                this.doSeek = seeked
                this.position = viewmodel.player!!.currentPositionMs().div(1000L) // if dontSlowDownWithMe useGlobalPosition or else usePlayerPosition
                changeState = if (surelyPausedChanged) 1 else 0
                play = viewmodel.player!!.isPlaying()
            }
        } else {
            sender.send<PacketCreator.State> {
                serverTime = latencyCalculation
                this.doSeek = null
                this.position = null
                changeState = 0
                play = null
            }
        }

    }

    private fun handleChat(chat: ChatData) {
        val sender = chat.username ?: return
        val message = chat.message ?: return
        callback.onChatReceived(sender, message)
    }

    private suspend fun handleTLS(tls: TLSData) {
        tls.startTLS?.let { startTLS ->
            callback.onReceivedTLS(startTLS)
        }
    }

    private fun handleError(error: ErrorData) {
        // Implement error handling as needed
        error.message?.let { message ->
            loggy("Server error: $message")
        }
    }
}