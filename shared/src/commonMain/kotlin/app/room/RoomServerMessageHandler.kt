package app.room

import androidx.lifecycle.viewModelScope
import app.player.models.MediaFile
import app.preferences.Preferences
import app.preferences.value
import app.protocol.ProtocolManager.Companion.FASTFORWARD_BEHIND_THRESHOLD
import app.protocol.ProtocolManager.Companion.FASTFORWARD_EXTRA_TIME
import app.protocol.ProtocolManager.Companion.FASTFORWARD_RESET_THRESHOLD
import app.protocol.ProtocolManager.Companion.FASTFORWARD_THRESHOLD
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
import org.jetbrains.compose.resources.getString
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_not_ready_set_by
import syncplaymobile.shared.generated.resources.room_ready_set_by
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

            // ONLY a genuine room-state transition: the last room pause-state we recorded
            // differs from what the server just sent. Must NOT also test
            // `paused == viewmodel.player.isPlaying()` — that fires on a local player/room
            // divergence rather than a transition, so a stale async isPlaying() (VLCKit's)
            // would re-fire the "X paused/unpaused" OSD on every 1 Hz State. Player drift is
            // corrected by the rewind/fastforward/slowdown block and the channel-health
            // collector, not by re-announcing here.
            val pausedChanged = protocol.globalPaused != paused
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
                    val firstSeekMs = (agedPosition * 1000.0).toLong()
                    withContext(Dispatchers.Main) {
                        viewmodel.player.seekTo(firstSeekMs)
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

            /* Desync-correction logic (rewind / fastforward / slowdown) only makes sense
             * when we actually have media loaded. With no media, currentPositionMs() is 0
             * and `diff = 0 - agedPosition` looks like a multi-second lag, which triggers
             * a phantom "fast-forwarded to match X" OSD even though there's no playback to
             * adjust. Mirrors the PC client's `if self._player:` gate around the whole
             * `_changePlayerStateAccordingToGlobalState` (client.py:459). The pause/play
             * callbacks below still fire — those are informational and useful even without
             * a file loaded ("X paused the room"). */
            if (viewmodel.media != null) {
                /* Rewind check if someone is behind */
                if (diff > protocol.rewindThreshold && doSeek != true && Preferences.SYNC_REWIND.value()) {
                    if (protocol.speedChanged) {
                        withContext(Dispatchers.Main) { viewmodel.player.setSpeed(1.0) }
                        protocol.speedChanged = false
                    }
                    callback.onSomeoneBehind(setBy ?: "", agedPosition)
                }

                /* Fast-forward if persistently behind. PC gates this on !canControl(): only a
                 * follower in a controlled (+) room force-fastforwards to keep up. In a normal
                 * room everyone can control, so nobody force-fastforwards there (the room
                 * follows the slowest member via rewind/slowdown). SYNC_DONT_SLOW_WITH_ME opts
                 * in regardless. */
                val canFastForward = Preferences.SYNC_FASTFORWARD.value()
                        && (isInControlledRoomWithoutController() || Preferences.SYNC_DONT_SLOW_WITH_ME.value())
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
            val ackPos = if (Preferences.SYNC_DONT_SLOW_WITH_ME.value()) {
                // Use the room's *current* expected position, not the last 1 Hz snapshot —
                // mirrors python's getGlobalPosition() extrapolation.
                protocol.extrapolatedGlobalPositionMs() / 1000.0
            } else {
                withContext(Dispatchers.Main.immediate) { viewmodel.player.currentPositionMs() / 1000.0 }
            }
            /* `play` MUST be the state we just acknowledged from the server (`!paused`),
             * NOT the transient `viewmodel.player.isPlaying()`. VLCKit 4's libvlc pause/play
             * API is asynchronous: pause() returns before the player has transitioned, so
             * isPlaying() right after pause() still reports true. ACKing that stale value
             * would send State(play=true) when the server told us to pause, which the public
             * Syncplay server reads as "this watcher is unpausing the room" and rebroadcasts.
             * `!paused` is correct because we applied that state in the block above; a genuine
             * application failure is caught later by the isNowPlaying StateFlow divergence in
             * ProtocolManager, once the player has settled. */
            network.sendAsync(
                protocol.buildStatePacket(
                    serverTime = latencyCalculation,
                    // Mobile owns its embedded player, so every real seek is announced explicitly
                    // via dispatcher.sendSeek. There is no out-of-band seek to discover, so this
                    // periodic ACK never re-derives one — it omits doSeek, like the PC client
                    // (whose ACK compares the player against itself, never against the inbound
                    // server position).
                    doSeek = null,
                    position = ackPos,
                    isLocalStateChange = false,
                    /* `paused` may be null here (the inbound playstate didn't carry one);
                     * fall back to globalPaused which we treat as the current room state. */
                    play = !(paused ?: protocol.globalPaused)
                )
            )
        } else {
            network.sendAsync(
                protocol.buildStatePacket(
                    serverTime = latencyCalculation,
                    doSeek = null,
                    position = null,
                    isLocalStateChange = false,
                    play = null
                )
            )
        }
    }

    override suspend fun onSet(message: WireMessage.Set) {
        val set = message.data
        // Process EVERY key present, not just the first — python's handleSet iterates the
        // whole Set dict, and a server may legally bundle several commands in one message.
        // playlistChange is applied before playlistIndex so a bundled index can resolve
        // against the new entries.
        set.user?.let { handleUserSet(it) }
        set.ready?.let { handleReadySet(it) }
        set.playlistChange?.let { handlePlaylistChange(it) }
        set.playlistIndex?.let { handlePlaylistIndex(it) }
        set.newControlledRoom?.let { handleNewControlledRoom(it) }
        set.controllerAuth?.let { handleControllerAuth(it) }
        // No List request after every Set. The python client mutates its userlist in
        // place from the very Set it just received (see `_SetUser` / `setReady` in
        // protocols.py); [handleUserSet] and [handleReadySet] do the same. The public
        // server only proactively pushes a List broadcast when persistent rooms are
        // configured (`roomsDbFile` set on server), so we cannot rely on it.
    }

    /**
     * True when we ARE in a controlled (+) room but are NOT a controller, so we must follow
     * the controller's pace. Mirrors python's `!currentUser.canControl()`. In a normal room
     * this is false (everyone can control), which is why fast-forward-on-desync must not fire
     * there. Inverse of [isControllerInControlledRoom]'s controller test.
     */
    private fun isInControlledRoomWithoutController(): Boolean {
        if (!session.roomFeatures.supportsManagedRooms) return false
        if (!session.currentRoom.startsWith("+")) return false
        return session.userList.value.firstOrNull { it.name == session.currentUsername }?.isController != true
    }

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
        val errorText = message.data.message ?: return
        loggy("Server error: $errorText")
        // Surface server errors (e.g. "Wrong password supplied") to chat + OSD, like PC's
        // ui.showErrorMessage. Shown verbatim — these originate in English on the server.
        dispatcher.broadcastMessage(message = { errorText }, isChat = false, isError = true)
        viewmodel.dispatchOSD(OSDCategory.WARNING) { errorText }
    }

    // -----------------------------------------------------------
    // Set sub-routing
    // -----------------------------------------------------------

    /**
     * Mirrors python's `_SetUser` (protocols.py): mutates the local user list directly
     * from the Set we just received, instead of round-tripping a List request, so user-list
     * changes show up immediately rather than on the next periodic probe.
     *
     * Wire shape per user:
     *  - `event.joined` (with optional file) → add to list
     *  - `event.left`                        → remove from list
     *  - file present, no event              → update file in place (`modUser`)
     */
    private suspend fun handleUserSet(userMap: Map<String, UserSetData>) {
        // Python iterates every user in the Set dict — the official server usually sends
        // one, but nothing in the protocol forbids several.
        for ((userName, userData) in userMap) handleSingleUserSet(userName, userData)
    }

    private suspend fun handleSingleUserSet(userName: String, userData: UserSetData) {
        val current = session.userList.value
        val updated = current.toMutableList()
        var changed = false

        // The broadcast carries the user's room. Our user list models ONLY the current
        // room (unlike PC's all-rooms pane), so the room field decides membership:
        //  - join/switch INTO our room → appears in the list
        //  - switch OUT of our room    → removed immediately (mirrors python's modUser)
        // A missing room (defensive: python always sends one) is treated as ours.
        val eventRoom = userData.room?.name
        val inOurRoom = eventRoom == null || eventRoom == session.currentRoom

        userData.event?.let { event ->
            when {
                event.left != null -> {
                    val idx = updated.indexOfFirst { it.name == userName }
                    if (idx >= 0) {
                        updated.removeAt(idx)
                        changed = true
                        callback.onSomeoneLeft(userName)
                    }
                }
                event.joined != null && inOurRoom -> {
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
                // joined another room: not in our view, nothing to render.
            }
        }

        // No event but a room is present: a room SWITCH (server's sendRoomSwitchMessage
        // broadcasts a bare `Set.user` with the new room).
        if (userData.event == null && eventRoom != null && userName != session.currentUsername) {
            val idx = updated.indexOfFirst { it.name == userName }
            if (!inOurRoom && idx >= 0) {
                // Moved away from our room — drop them from the list immediately.
                updated.removeAt(idx)
                changed = true
            } else if (inOurRoom && idx < 0) {
                // Moved into our room.
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
                callback.onSomeoneJoined(userName)
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
            // Announce file loads only for users we can see (our room).
            if (idx >= 0) callback.onSomeoneLoadedFile(userName, file.name ?: "", file.duration ?: 0.0)
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

        // A controller set someone ELSE's readiness (setOthersReadiness feature) — announce
        // it like PC does, otherwise the change is invisible beyond the icon flip.
        val setBy = ready.setBy
        if (setBy != null && setBy != userName) {
            val resource = if (isReady) Res.string.room_ready_set_by else Res.string.room_not_ready_set_by
            dispatcher.broadcastMessage(
                message = { getString(resource, userName, setBy) },
                isChat = false
            )
        }

        // Keep our own ready flag in sync when WE are the one being set (otherwise the
        // ready button and instaplay gating disagree with what the room sees).
        if (userName == session.currentUsername) {
            session.ready.value = isReady
        }
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
