package app.room

import androidx.lifecycle.viewModelScope
import app.player.models.MediaFile
import app.preferences.Preferences
import app.preferences.value
import app.protocol.ClientMessage
import app.protocol.ProtocolManager.Companion.FASTFORWARD_BEHIND_THRESHOLD
import app.protocol.ProtocolManager.Companion.FASTFORWARD_EXTRA_TIME
import app.protocol.ProtocolManager.Companion.FASTFORWARD_RESET_THRESHOLD
import app.protocol.ProtocolManager.Companion.FASTFORWARD_THRESHOLD
import app.protocol.ProtocolManager.Companion.SEEK_THRESHOLD
import app.protocol.ProtocolManager.Companion.SLOWDOWN_RATE
import app.protocol.ProtocolManager.Companion.SLOWDOWN_RESET_THRESHOLD
import app.protocol.ProtocolManager.Companion.SLOWDOWN_THRESHOLD
import app.protocol.ServerMessage
import app.protocol.ServerMessageHandler
import app.protocol.models.User
import app.protocol.wire.ControllerAuthData
import app.protocol.wire.NewControlledRoom
import app.protocol.wire.PlaylistChangeData
import app.protocol.wire.PlaylistIndexData
import app.protocol.wire.UserSetData
import app.utils.loggy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Client-side implementation of [ServerMessageHandler].
 *
 * Owns the room-level reactions to every incoming `ServerMessage` — playback
 * synchronization, user-list rendering, chat broadcasts, TLS upgrade, etc.
 *
 * This is the place where the protocol's typed payloads meet the room's domain logic.
 * The wire models live in `app.protocol.wire` and are deliberately free of any client
 * coupling; only this class knows about [RoomViewmodel], the player, preferences, etc.
 */
class RoomServerMessageHandler(private val viewmodel: RoomViewmodel) : ServerMessageHandler {

    private val protocol get() = viewmodel.protocol
    private val callback get() = viewmodel.callback
    private val dispatcher get() = viewmodel.dispatcher
    private val network get() = viewmodel.networkManager
    private val session get() = viewmodel.session

    override suspend fun onHello(message: ServerMessage.Hello) {
        val data = message.data
        data.username?.let { session.currentUsername = it }
        session.roomFeatures = data.features

        // Ask for the user list right away — the server will send a `List` reply.
        network.send(ClientMessage.listRequest())
        callback.onConnected()
    }

    override suspend fun onState(message: ServerMessage.State) {
        // Freshness stamp for the channel-health watchdog. Set before any other processing
        // so even a State we end up ignoring still resets the "no State received" timer —
        // the server is clearly alive.
        protocol.lastStateReceivedAt = Clock.System.now()

        val state = message.data
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
                protocol.pingService.receiveMessage(timestamp.roundToLong(), serverRtt)
            }
            messageAge = protocol.pingService.forwardDelay
        }

        if (position != null && paused != null && protocol.clientIgnFly == 0) {
            val pausedChanged = protocol.globalPaused != paused || paused == viewmodel.player.isPlaying()
            val diff = withContext(Dispatchers.Main.immediate) {
                (viewmodel.player.currentPositionMs() / 1000.0) - position
            }

            /* Updating Global State */
            protocol.globalPaused = paused
            protocol.globalPositionMs = position * 1000L
            if (!paused) protocol.globalPositionMs += messageAge // Account for network drift

            if (protocol.lastGlobalUpdate == null) {
                if (viewmodel.media != null) {
                    withContext(Dispatchers.Main) {
                        viewmodel.player.seekTo(position.toLong())
                        if (paused) viewmodel.player.pause() else viewmodel.player.play()
                    }
                }
            }

            protocol.lastGlobalUpdate = Clock.System.now()

            if (doSeek == true && setBy != null) {
                if (protocol.speedChanged) {
                    withContext(Dispatchers.Main) { viewmodel.player.setSpeed(1.0) }
                    protocol.speedChanged = false
                }
                callback.onSomeoneSeeked(setBy, position)
            }

            /* Rewind check if someone is behind */
            if (diff > protocol.rewindThreshold && doSeek != true && Preferences.SYNC_REWIND.value()) {
                if (protocol.speedChanged) {
                    withContext(Dispatchers.Main) { viewmodel.player.setSpeed(1.0) }
                    protocol.speedChanged = false
                }
                callback.onSomeoneBehind(setBy ?: "", position)
            }

            /* Fast-forward if persistently behind */
            if (diff < -FASTFORWARD_BEHIND_THRESHOLD && doSeek != true && Preferences.SYNC_FASTFORWARD.value()) {
                val now = Clock.System.now()
                if (protocol.behindFirstDetected == null) {
                    protocol.behindFirstDetected = now
                } else {
                    val durationBehind = (now - protocol.behindFirstDetected!!).inWholeMilliseconds / 1000.0
                    if (durationBehind > (FASTFORWARD_THRESHOLD - FASTFORWARD_BEHIND_THRESHOLD)
                        && diff < -FASTFORWARD_THRESHOLD
                    ) {
                        callback.onSomeoneFastForwarded(setBy ?: "", position + FASTFORWARD_EXTRA_TIME)
                        protocol.behindFirstDetected = now + FASTFORWARD_RESET_THRESHOLD.seconds
                    }
                }
            } else {
                protocol.behindFirstDetected = null
            }

            /* Slow down to cover time difference */
            if (doSeek != true && !paused) {
                if (Preferences.SYNC_SLOWDOWN.value()) {
                    if (diff > SLOWDOWN_THRESHOLD && !protocol.speedChanged) {
                        if (setBy != null && setBy != session.currentUsername) {
                            withContext(Dispatchers.Main) { viewmodel.player.setSpeed(SLOWDOWN_RATE) }
                            protocol.speedChanged = true
                        }
                    } else if (protocol.speedChanged && diff < SLOWDOWN_RESET_THRESHOLD) {
                        withContext(Dispatchers.Main) { viewmodel.player.setSpeed(1.0) }
                        protocol.speedChanged = false
                    }
                } else if (protocol.speedChanged) {
                    withContext(Dispatchers.Main) { viewmodel.player.setSpeed(1.0) }
                    protocol.speedChanged = false
                }
            }

            if (pausedChanged) {
                if (paused && protocol.speedChanged) {
                    withContext(Dispatchers.Main) { viewmodel.player.setSpeed(1.0) }
                    protocol.speedChanged = false
                }
                if (!paused) callback.onSomeonePlayed(setBy ?: "")
                if (paused) callback.onSomeonePaused(setBy ?: "")
            }
        }

        // Acknowledge with our own State packet.
        if (protocol.lastGlobalUpdate != null && position != null) {
            val playerDiff = withContext(Dispatchers.Main.immediate) {
                abs(viewmodel.player.currentPositionMs() / 1000.0 - position)
            }
            val globalDiff = abs(protocol.globalPositionMs / 1000.0 - position)
            val surelyPausedChanged = protocol.globalPaused != paused && paused == viewmodel.player.isPlaying()
            val seeked = playerDiff > SEEK_THRESHOLD && globalDiff > SEEK_THRESHOLD
            val ackPos = if (Preferences.SYNC_DONT_SLOW_WITH_ME.value()) {
                protocol.globalPositionMs.div(1000.0).toLong()
            } else {
                withContext(Dispatchers.Main.immediate) { viewmodel.player.currentPositionMs().div(1000L) }
            }

            network.sendAsync(
                protocol.buildStatePacket(
                    serverTime = latencyCalculation,
                    doSeek = seeked,
                    position = ackPos,
                    changeState = if (surelyPausedChanged) 1 else 0,
                    play = viewmodel.player.isPlaying()
                )
            )
        } else {
            network.sendAsync(
                protocol.buildStatePacket(
                    serverTime = latencyCalculation,
                    doSeek = null,
                    position = null,
                    changeState = 0,
                    play = null
                )
            )
        }
    }

    override suspend fun onSet(message: ServerMessage.Set) {
        val set = message.data
        when {
            set.user != null -> handleUserSet(set.user)
            set.playlistIndex != null -> handlePlaylistIndex(set.playlistIndex)
            set.playlistChange != null -> handlePlaylistChange(set.playlistChange)
            set.newControlledRoom != null -> handleNewControlledRoom(set.newControlledRoom)
            set.controllerAuth != null -> handleControllerAuth(set.controllerAuth)
        }

        network.sendAsync(ClientMessage.listRequest())
    }

    override suspend fun onList(message: ServerMessage.List) {
        val userlist = message.rooms[session.currentRoom] ?: return
        val newList = mutableListOf<User>()
        var indexer = 1

        for ((userName, userData) in userlist) {
            val user = User(
                name = userName,
                index = if (userName != session.currentUsername) indexer++ else 0,
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
            session.userList.emit(newList)
            callback.onReceivedList()
        }
    }

    override suspend fun onChat(message: ServerMessage.Chat) {
        val sender = message.data.username ?: return
        val text = message.data.message ?: return
        callback.onChatReceived(sender, text)
    }

    override suspend fun onTLS(message: ServerMessage.TLS) {
        val startTLS = message.data.startTLS ?: return
        // Match python client semantics: `"true" in answer` / `"false" in answer`.
        val supported = startTLS.contains("true", ignoreCase = true)
        callback.onReceivedTLS(supported)
    }

    override suspend fun onError(message: ServerMessage.Error) {
        message.data.message?.let { loggy("Server error: $it") }
    }

    // -----------------------------------------------------------
    // Set sub-routing
    // -----------------------------------------------------------

    private fun handleUserSet(userMap: Map<String, UserSetData>) {
        val (userName, userData) = userMap.entries.firstOrNull() ?: return

        userData.event?.let { event ->
            when {
                event.left != null -> callback.onSomeoneLeft(userName)
                event.joined != null -> callback.onSomeoneJoined(userName)
            }
        }

        userData.file?.let { file ->
            callback.onSomeoneLoadedFile(userName, file.name ?: "", file.duration ?: 0.0)
        }
    }

    private fun handlePlaylistIndex(playlistIndex: PlaylistIndexData) {
        val user = playlistIndex.user ?: return
        val index = playlistIndex.index ?: return
        callback.onPlaylistIndexChanged(user, index)
        session.spIndex.intValue = index
    }

    private fun handlePlaylistChange(playlistChange: PlaylistChangeData) {
        val user = playlistChange.user ?: ""
        val files = playlistChange.files ?: return
        session.sharedPlaylist.clear()
        session.sharedPlaylist.addAll(files)
        callback.onPlaylistUpdated(user)
    }

    private suspend fun handleNewControlledRoom(data: NewControlledRoom) {
        try {
            callback.onNewControlledRoom(data)
            network.send(ClientMessage.roomChange(data.roomName))
            network.sendAsync(ClientMessage.listRequest())
            network.send(ClientMessage.controllerAuth(room = data.roomName, password = data.password))
        } finally {
            viewmodel.viewModelScope.launch {
                delay(1000)
                viewmodel.protocol.isRoomChanging = false
            }
        }
    }

    private fun handleControllerAuth(data: ControllerAuthData) {
        callback.onHandleControllerAuth(data)
    }
}
