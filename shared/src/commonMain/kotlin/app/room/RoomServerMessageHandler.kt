package app.room

import androidx.lifecycle.viewModelScope
import app.player.models.MediaFile
import app.preferences.Preferences
import app.preferences.value
import app.protocol.ProtocolManager.Companion.FASTFORWARD_BEHIND_THRESHOLD
import app.protocol.ProtocolManager.Companion.FASTFORWARD_EXTRA_TIME
import app.protocol.ProtocolManager.Companion.FASTFORWARD_RESET_THRESHOLD
import app.protocol.ProtocolManager.Companion.FASTFORWARD_THRESHOLD
import app.protocol.ProtocolManager.Companion.SEEK_THRESHOLD
import app.protocol.ProtocolManager.Companion.SLOWDOWN_RATE
import app.protocol.ProtocolManager.Companion.SLOWDOWN_RESET_THRESHOLD
import app.protocol.ProtocolManager.Companion.SLOWDOWN_THRESHOLD
import app.protocol.WireMessage
import app.protocol.WireMessageHandler
import app.protocol.models.User
import app.protocol.wire.ControllerAuthData
import app.protocol.wire.NewControlledRoom
import app.protocol.wire.PlaylistChangeData
import app.protocol.wire.PlaylistIndexData
import app.protocol.wire.ReadyData
import app.protocol.wire.UserSetData
import app.utils.loggy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Client-side implementation of [WireMessageHandler].
 *
 * Owns the room-level reactions to every incoming server-bound [WireMessage] — playback
 * synchronization, user-list rendering, chat broadcasts, TLS upgrade, etc. Only the
 * server→client variants are overridden; the client→server ones inherit the no-op
 * defaults.
 *
 * This is the place where the protocol's typed payloads meet the room's domain logic.
 * The wire models live in `app.protocol.wire` and are deliberately free of any client
 * coupling; only this class knows about [RoomViewmodel], the player, preferences, etc.
 */
class RoomServerMessageHandler(private val viewmodel: RoomViewmodel) : WireMessageHandler {

    private val protocol get() = viewmodel.protocol
    private val callback get() = viewmodel.callback
    private val dispatcher get() = viewmodel.dispatcher
    private val network get() = viewmodel.networkManager
    private val session get() = viewmodel.session

    override suspend fun onHello(message: WireMessage.Hello) {
        val data = message.data
        data.username?.let { session.currentUsername = it }
        session.roomFeatures = data.features

        // Ask for the user list right away — the server will send a `List` reply.
        network.send(WireMessage.listRequest())
        callback.onConnected()
    }

    override suspend fun onState(message: WireMessage.State) {
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
                protocol.pingService.receiveMessage(timestamp, serverRtt)
            }
            messageAge = protocol.pingService.forwardDelay
        }

        if (position != null && paused != null && protocol.clientIgnFly == 0) {
            // Match python: when the room is playing, the position the server sent us is
            // already messageAge seconds stale, so the *current* expected position is
            // position + messageAge. All threshold comparisons must use this aged value.
            val agedPosition = if (paused) position else position + messageAge

            val pausedChanged = protocol.globalPaused != paused || paused == viewmodel.player.isPlaying()
            val diff = withContext(Dispatchers.Main.immediate) {
                (viewmodel.player.currentPositionMs() / 1000.0) - agedPosition
            }

            /* Updating Global State */
            protocol.globalPaused = paused
            protocol.globalPositionMs = agedPosition * 1000.0
            protocol.lastGlobalPositionSetAt = Clock.System.now()

            if (protocol.lastGlobalUpdate == null) {
                if (viewmodel.media != null) {
                    // Set the expected pause state BEFORE touching the player. The flow
                    // collector watching PlayerManager.isNowPlaying will fire as soon as
                    // the engine catches up with our pause()/play() — if expectedPaused
                    // is still at its default when that happens, the collector treats it
                    // as a divergence and re-broadcasts to the room, looping our own
                    // first-sync back at the server.
                    protocol.noteExpectedPlaybackState(paused = paused)
                    withContext(Dispatchers.Main) {
                        viewmodel.player.seekTo((agedPosition * 1000.0).toLong())
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
                callback.onSomeoneSeeked(setBy, agedPosition)
            }

            /* Rewind check if someone is behind */
            if (diff > protocol.rewindThreshold && doSeek != true && Preferences.SYNC_REWIND.value()) {
                if (protocol.speedChanged) {
                    withContext(Dispatchers.Main) { viewmodel.player.setSpeed(1.0) }
                    protocol.speedChanged = false
                }
                callback.onSomeoneBehind(setBy ?: "", agedPosition)
            }

            /* Fast-forward if persistently behind. Mirrors python's gating: only triggers
             * when we cannot control the room (in a controlled room as a non-controller),
             * OR when SYNC_DONT_SLOW_WITH_ME is on. Without this, a controller in their
             * own controlled room would yank themselves forward over their own pace. */
            val canFastForward = Preferences.SYNC_FASTFORWARD.value()
                    && (!isControllerInControlledRoom() || Preferences.SYNC_DONT_SLOW_WITH_ME.value())
            if (diff < -FASTFORWARD_BEHIND_THRESHOLD && doSeek != true && canFastForward) {
                val now = Clock.System.now()
                if (protocol.behindFirstDetected == null) {
                    protocol.behindFirstDetected = now
                } else {
                    val durationBehind = (now - protocol.behindFirstDetected!!).inWholeMilliseconds / 1000.0
                    if (durationBehind > (FASTFORWARD_THRESHOLD - FASTFORWARD_BEHIND_THRESHOLD)
                        && diff < -FASTFORWARD_THRESHOLD
                    ) {
                        val ffTarget = agedPosition + FASTFORWARD_EXTRA_TIME
                        callback.onSomeoneFastForwarded(setBy ?: "", ffTarget)
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

            // No baseline-priming needed here anymore: the pause-broadcast flow
            // collector keys off PlayerManager.isNowPlaying changes, and pause-state
            // changes are routed through dispatcher.controlPlayback (in onSomeonePaused
            // / onSomeonePlayed), which sets expectedPaused itself before touching the
            // player. Pure seeks (doSeek=true, rewind, fastforward) don't toggle
            // isNowPlaying, so no flow emission to suppress.
        }

        // Acknowledge with our own State packet.
        if (protocol.lastGlobalUpdate != null && position != null) {
            val ackPos = if (Preferences.SYNC_DONT_SLOW_WITH_ME.value()) {
                // Use the room's *current* expected position, not the last 1 Hz snapshot —
                // mirrors python's getGlobalPosition() extrapolation.
                protocol.extrapolatedGlobalPositionMs() / 1000.0
            } else {
                withContext(Dispatchers.Main.immediate) { viewmodel.player.currentPositionMs() / 1000.0 }
            }
            val playerPosSec = withContext(Dispatchers.Main.immediate) {
                viewmodel.player.currentPositionMs() / 1000.0
            }
            val playerDiff = abs(playerPosSec - position)
            val globalDiff = abs(protocol.extrapolatedGlobalPositionMs() / 1000.0 - position)
            val surelyPausedChanged = protocol.globalPaused != paused && paused == viewmodel.player.isPlaying()
            val seeked = playerDiff > SEEK_THRESHOLD && globalDiff > SEEK_THRESHOLD

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

    override suspend fun onSet(message: WireMessage.Set) {
        val set = message.data
        when {
            set.user != null -> handleUserSet(set.user)
            set.ready != null -> handleReadySet(set.ready)
            set.playlistIndex != null -> handlePlaylistIndex(set.playlistIndex)
            set.playlistChange != null -> handlePlaylistChange(set.playlistChange)
            set.newControlledRoom != null -> handleNewControlledRoom(set.newControlledRoom)
            set.controllerAuth != null -> handleControllerAuth(set.controllerAuth)
        }
        // No List request after every Set. The python client mutates its userlist in
        // place from the very Set it just received (see `_SetUser` / `setReady` in
        // protocols.py); [handleUserSet] and [handleReadySet] do the same. The public
        // server only proactively pushes a List broadcast when persistent rooms are
        // configured (`roomsDbFile` set on server), so we cannot rely on it.
    }

    /**
     * True if we're in a `+room:HASH` controlled room and have not authenticated as
     * controller yet. Used to gate fastforward, instaplay, and the auto re-auth on
     * reconnect — same rule as python's `currentUser.canControl()` (inverted).
     */
    private fun isControllerInControlledRoom(): Boolean {
        if (!session.roomFeatures.supportsManagedRooms) return false
        if (!session.currentRoom.startsWith("+")) return false
        // Find ourselves in the user list — `isController` is set by the server's auth ack.
        return session.userList.value.firstOrNull { it.name == session.currentUsername }?.isController == true
    }

    override suspend fun onListResponse(message: WireMessage.ListResponse) {
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

    override suspend fun onChatBroadcast(message: WireMessage.ChatBroadcast) {
        val sender = message.data.username ?: return
        val text = message.data.message ?: return
        callback.onChatReceived(sender, text)
    }

    override suspend fun onTLS(message: WireMessage.TLS) {
        val startTLS = message.data.startTLS ?: return
        // Match python client semantics: `"true" in answer` / `"false" in answer`.
        val supported = startTLS.contains("true", ignoreCase = true)
        callback.onReceivedTLS(supported)
    }

    override suspend fun onError(message: WireMessage.Error) {
        message.data.message?.let { loggy("Server error: $it") }
    }

    // -----------------------------------------------------------
    // Set sub-routing
    // -----------------------------------------------------------

    /**
     * Mirrors python's `_SetUser` (protocols.py): mutates the local user list directly
     * from the Set we just received, instead of round-tripping a List request. Without
     * this, `session.userList` only refreshes on the periodic 15-second probe, which
     * is what surfaces as "user info takes 5+ seconds to update."
     *
     * Wire shape per user:
     *  - `event.joined` (with optional file) → add to list
     *  - `event.left`                        → remove from list
     *  - file present, no event              → update file in place (`modUser`)
     */
    private suspend fun handleUserSet(userMap: Map<String, UserSetData>) {
        val (userName, userData) = userMap.entries.firstOrNull() ?: return

        val current = session.userList.value
        val updated = current.toMutableList()
        var changed = false

        userData.event?.let { event ->
            when {
                event.left != null -> {
                    val idx = updated.indexOfFirst { it.name == userName }
                    if (idx >= 0) {
                        updated.removeAt(idx)
                        changed = true
                    }
                    callback.onSomeoneLeft(userName)
                }
                event.joined != null -> {
                    if (updated.none { it.name == userName }) {
                        val nextIndex = (updated.maxOfOrNull { it.index } ?: 0) + 1
                        updated.add(
                            User(
                                name = userName,
                                index = nextIndex,
                                readiness = false,
                                file = null,
                                isController = false,
                            )
                        )
                        changed = true
                    }
                    callback.onSomeoneJoined(userName)
                }
            }
        }

        userData.file?.let { file ->
            // Re-resolve the index — the join branch above may have just inserted them.
            val idx = updated.indexOfFirst { it.name == userName }
            if (idx >= 0 && file.name != null) {
                val mediaFile = MediaFile().apply {
                    fileName = file.name
                    fileDuration = file.duration ?: 0.0
                    fileSize = file.size ?: ""
                }
                updated[idx] = updated[idx].copy(file = mediaFile)
                changed = true
            }
            callback.onSomeoneLoadedFile(userName, file.name ?: "", file.duration ?: 0.0)
        }

        if (changed) session.userList.emit(updated)
    }

    /**
     * Mirrors python's `setReady` (client.py): toggles the user's readiness flag in the
     * local user list. The server sends `Set.ready` independently of `Set.user`, so this
     * is its own branch.
     */
    private suspend fun handleReadySet(ready: ReadyData) {
        val userName = ready.username ?: return
        val isReady = ready.isReady ?: return

        val current = session.userList.value
        val idx = current.indexOfFirst { it.name == userName }
        if (idx < 0) return
        if (current[idx].readiness == isReady) return

        val updated = current.toMutableList()
        updated[idx] = updated[idx].copy(readiness = isReady)
        session.userList.emit(updated)
    }

    private fun handlePlaylistIndex(playlistIndex: PlaylistIndexData) {
        val user = playlistIndex.user ?: return
        val index = playlistIndex.index ?: return
        // Self-initiated changes already updated spIndex locally before the round-trip;
        // applying it again is harmless but prevents a brief stale-list window if our
        // own echo arrives before the matching playlistChange.
        if (user != session.currentUsername) {
            session.spIndex.intValue = index
        }
        callback.onPlaylistIndexChanged(user, index)
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
            network.send(WireMessage.roomChange(data.roomName))
            network.sendAsync(WireMessage.listRequest())
            network.send(WireMessage.controllerAuth(room = data.roomName, password = data.password))
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
