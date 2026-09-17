package app.room

import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.viewModelScope
import app.i18n.Localization
import app.player.models.MediaFile
import app.preferences.Preferences
import app.preferences.value
import app.protocol.ProtocolManager.Companion.SLOWDOWN_RATE
import app.protocol.sync.SyncAction
import app.protocol.sync.LocalSeek
import app.protocol.sync.PendingSeekPosition
import app.protocol.sync.SyncContext
import app.protocol.sync.SyncPrefs
import app.protocol.sync.decideSync
import app.protocol.sync.withIgnoringOnTheFly
import app.protocol.Session
import app.protocol.WireMessage
import app.protocol.WireMessageHandler
import app.protocol.models.TlsState
import app.protocol.models.User
import app.protocol.wire.ControllerAuthData
import app.protocol.wire.NewControlledRoom
import app.protocol.wire.PlaylistChangeData
import app.protocol.wire.PlaylistIndexData
import app.protocol.wire.ReadyData
import app.protocol.wire.UserSetData
import app.utils.SyncClock
import app.utils.loggy
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
        /* We asked to upgrade and got a Hello instead: this server skipped the answer, so the
         * socket is still plain text. Required means we leave rather than carry on. */
        if (network.tls == TlsState.TLS_ASK) {
            if (Preferences.TLS_REQUIRED.value()) {
                callback.onTlsRequiredButUnavailable()
                network.abortConnection()
                return
            }
            loggy("SYNCPLAY Protocol: Hello arrived before the TLS answer; continuing in plain text")
            network.tls = TlsState.TLS_NO
        }

        val data = message.data
        data.username?.let { session.currentUsername = it.take(MAX_USERNAME_CHARS) }
        session.roomFeatures = data.features

        // Ask for the user list right away — the server will send a `List` reply. Fire and
        // forget: the answer arrives on the wire, and this runs on the serial inbound consumer,
        // which must not sit on a write while State packets queue behind it.
        network.sendAsync(WireMessage.listRequest())
        callback.onConnected()

        // The server's message of the day, shown in chat like PC does (it may carry an update
        // notice or house rules).
        data.motd?.takeIf { it.isNotBlank() }?.let { motd ->
            val text = motd.take(MAX_CHAT_CHARS)
            dispatcher.broadcastMessage(message = { text }, isChat = false)
        }
    }

    override suspend fun onState(message: WireMessage.State) {
        // Freshness stamp for the channel-health watchdog. Set before any other processing
        // so even a State we end up ignoring still resets the "no State received" timer —
        // the server is clearly alive.
        protocol.lastStateReceivedAt = SyncClock.now()

        val state = message.data
        var latencyCalculation: Double? = null
        var messageAge = 0.0

        state.ping?.let { ping ->
            latencyCalculation = ping.latencyCalculation
            ping.clientLatencyCalculation?.let { timestamp ->
                val serverRtt = ping.serverRtt ?: return@let
                protocol.pingService.receiveMessage(timestamp, serverRtt)
                // Measured, not acted on: see ProtocolManager.clockOffset.
                protocol.clockOffset.observe(
                    ourSendTime = timestamp,
                    serverSendTime = ping.latencyCalculation ?: 0.0,
                    ourReceiveTime = SyncClock.nowSeconds(),
                )
            }
            messageAge = protocol.pingService.forwardDelay
        }

        /* The decision itself is a pure function; see app.protocol.sync.SyncDecision. Everything
         * it needs is gathered here, and everything it decides is applied below, in order.
         *
         * Read, decide and write back are one step. The anchor is eight fields written back as a
         * whole, so a reconnect or a file load landing between the read and the write would be
         * erased. Nothing in here suspends. */
        var localSeek: LocalSeek? = null
        var sentPendingIntent = false
        var decisionMedia: MediaFile? = null
        var decisionRevision = 0L
        var pendingActions: List<Pair<SyncAction, PendingSeekPosition?>> = emptyList()
        synchronized(protocol.syncLock) {
            decisionMedia = viewmodel.media
            protocol.syncState = protocol.syncState.withIgnoringOnTheFly(state.ignoringOnTheFly)
            localSeek = protocol.consumeLocalSeekEcho(state)
            // A second user seek must survive the first seek's forced echo. The latest queued
            // intent carries this ACK and re-arms the existing ignore gate before deciding any
            // obsolete inbound correction. Nothing here waits for the main thread or the socket.
            sentPendingIntent = protocol.flushPendingLocalState(latencyCalculation)
            decisionRevision = protocol.localStateRevision
            decideSync(
                playstate = state.playstate,
                state = protocol.syncState,
                ctx = SyncContext(
                    now = SyncClock.now(),
                    playerPositionMs = viewmodel.playerManager.estimatedPositionMs().toDouble(),
                    hasMedia = decisionMedia != null,
                    isInBackground = viewmodel.uiState.isInBackground,
                    supportsSpeedAdjustment = viewmodel.player.supportsSpeedAdjustment,
                    selfName = session.currentUsername,
                    followerInControlledRoom = session.isInControlledRoomWithoutController(),
                    prefs = SyncPrefs(
                        rewind = Preferences.SYNC_REWIND.value(),
                        fastForward = Preferences.SYNC_FASTFORWARD.value(),
                        slowdown = Preferences.SYNC_SLOWDOWN.value(),
                        dontSlowWithMe = Preferences.SYNC_DONT_SLOW_WITH_ME.value(),
                    ),
                    messageAge = messageAge,
                    // Stored in tenths of a second so the sliders are whole numbers.
                    rewindThreshold = Preferences.SYNC_REWIND_THRESHOLD.value() / 10.0,
                    slowdownThreshold = Preferences.SYNC_SLOWDOWN_THRESHOLD.value() / 10.0,
                    fastForwardThreshold = Preferences.SYNC_FASTFORWARD_THRESHOLD.value() / 10.0,
                    userOffsetSeconds = protocol.userTimeOffsetSeconds(),
                    seekPending = protocol.isSeekPending,
                ),
            ).also { outcome ->
                protocol.syncState = outcome.state
                // Publish every accepted target before an ACK can release the server's stale-report gate.
                pendingActions = outcome.actions.map { it to protocol.queueSeekPosition(it) }
            }
        }
        pendingActions.forEach { (action, pendingSeek) ->
            // Local origin metadata was matched to the sent packet before a newer seek flushed.
            // Unmatched self echoes are duplicates/overrides, not new gestures to undo.
            if (action !is SyncAction.SomeoneSeeked || action.by != session.currentUsername) {
                apply(action, decisionMedia, decisionRevision, pendingSeek)
            }
        }
        localSeek?.let { seek ->
            viewmodel.viewModelScope.launch(Dispatchers.Main.immediate) {
                if (viewmodel.media !== decisionMedia) return@launch
                callback.onSomeoneSeeked(session.currentUsername, seek.toMs / 1000.0, seek)
            }
        }
        if (sentPendingIntent) return

        /* Acknowledge with our own State packet. The gate is "the message carried a playstate
         * at all", not "it carried a position": the reference client reads a missing position
         * as zero, and a State whose playstate omits it still gets a positioned ACK. */
        if (protocol.lastGlobalUpdate != null && state.playstate != null) {
            val ackPos = if (Preferences.SYNC_DONT_SLOW_WITH_ME.value()) {
                // Use the room's *current* expected position, not the last 1 Hz snapshot —
                // mirrors python's getGlobalPosition() extrapolation.
                protocol.extrapolatedGlobalPositionMs() / 1000.0
            } else {
                // While a freshly-loaded file is still catching up, this reports the room
                // position instead of the engine's ~0, so we never drag the room back to us.
                protocol.reportableStatePositionSec()
            }
            /* `play` MUST be the state we just acknowledged from the server, NOT the transient
             * viewmodel.player.isPlaying(). VLCKit 4's libvlc pause/play API is asynchronous:
             * pause() returns before the player has transitioned, so isPlaying() right after
             * pause() still reports true. ACKing that stale value would send State(play=true)
             * when the server told us to pause, which the public Syncplay server reads as "this
             * watcher is unpausing the room" and rebroadcasts. globalPaused is what the decision
             * just recorded; a genuine application failure is caught later by the isNowPlaying
             * divergence check in ProtocolManager, once the player has settled. */
            protocol.sendStateAcknowledgement(
                serverTime = latencyCalculation,
                // Mobile owns its embedded player, so every real seek is announced explicitly
                // via dispatcher.sendSeek. There is no out-of-band seek to discover, so this
                // periodic ACK never re-derives one — it omits doSeek, like the PC client
                // (whose ACK compares the player against itself, never against the inbound
                // server position).
                position = ackPos,
                /* The state we just acknowledged from the server. Falls back to
                 * globalPaused only when the inbound playstate carried no pause field, or
                 * when we skipped the decision because we are ignoring the server on the
                 * fly and globalPaused is therefore still the room state we last agreed on. */
                play = !(state.playstate?.paused ?: protocol.globalPaused)
            )
        } else {
            protocol.sendStateAcknowledgement(
                serverTime = latencyCalculation,
                position = null,
                play = null
            )
        }
    }

    /** Carries out one decision. The order the actions arrive in is the order they must happen. */
    private fun apply(action: SyncAction, media: MediaFile?, revision: Long, pendingSeek: PendingSeekPosition?) {
        // The serial inbound consumer never waits on Main. Commands retain the media they were
        // decided for: a queued correction cannot seek or pause a newly installed file.
        val job = viewmodel.viewModelScope.launch(Dispatchers.Main.immediate) {
            if (viewmodel.media !== media) return@launch
            // A local command can land after the decision but before this Main task. Its seek
            // or pause must win. Speed actions still apply: the reducer already changed its
            // speedChanged flag, so dropping only the native half could leave rate at 0.95.
            val changesTransport = action !is SyncAction.SlowDown && action != SyncAction.RestoreSpeed
            if (changesTransport && !protocol.isLocalStateRevisionCurrent(revision)) return@launch
            when (action) {
                is SyncAction.FirstSync -> {
                    /* Set the expected pause state BEFORE touching the player. The collector watching
                     * PlayerManager.isNowPlaying fires as soon as the engine catches up with our
                     * pause()/play(); if expectedPaused were still at its default then, it would read
                     * as a divergence and re-broadcast our own first sync back at the server. */
                    protocol.noteExpectedPlaybackState(paused = action.paused)
                    viewmodel.player.seekTo(action.seekToMs)
                    if (action.paused) viewmodel.player.pause() else viewmodel.player.play()
                }
                is SyncAction.SomeoneSeeked -> callback.onSomeoneSeeked(action.by, action.toSeconds)
                is SyncAction.SomeoneBehind -> callback.onSomeoneBehind(action.by, action.toSeconds)
                is SyncAction.SomeoneFastForwarded -> callback.onSomeoneFastForwarded(action.by, action.toSeconds)
                is SyncAction.SomeonePlayed -> callback.onSomeonePlayed(action.by)
                is SyncAction.SomeonePaused -> callback.onSomeonePaused(action.by)
                is SyncAction.SlowDown -> {
                    viewmodel.player.setSpeed(SLOWDOWN_RATE)
                    // PC's slowdown notification: the room hears it, the user must too.
                    viewmodel.dispatchOSD(OSDCategory.SLOWDOWN) { Localization.strings.roomSlowdownNotification(action.by) }
                }
                SyncAction.RestoreSpeed -> {
                    viewmodel.player.setSpeed(1.0)
                    viewmodel.dispatchOSD(OSDCategory.SLOWDOWN) { Localization.strings.roomSlowdownReverted }
                }
            }
        }
        // Also runs when cancellation prevents the Main task from starting at all.
        if (pendingSeek != null) job.invokeOnCompletion { protocol.completeSeekPosition(pendingSeek) }
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

    override suspend fun onListResponse(message: WireMessage.ListResponse) {
        val userlist = message.rooms[session.currentRoom] ?: return
        val newList = mutableListOf<User>()
        var indexer = 1

        for ((rawName, userData) in userlist) {
            if (newList.size >= Session.MAX_USERS) break
            val userName = rawName.take(MAX_USERNAME_CHARS)
            val user = User(
                name = userName,
                index = if (userName != session.currentUsername) indexer++ else 0,
                readiness = userData.isReady ?: false,
                file = userData.file?.let { fileData ->
                    if (fileData.name != null) {
                        MediaFile().apply {
                            fileName = fileData.name.take(MAX_FILENAME_CHARS)
                            fileDuration = fileData.duration ?: 0.0
                            fileSize = fileData.size ?: ""
                        }
                    } else null
                },
                isController = userData.controller,
                position = userData.position,
            )
            newList.add(user)
        }

        // Applied inline, on the serial consumer, like every other roster mutation: a launch here
        // let a List reply land after the Set that followed it on the wire.
        session.userList.emit(newList)
        callback.onReceivedList()
    }

    override suspend fun onChatBroadcast(message: WireMessage.ChatBroadcast) {
        val sender = message.data.username ?: return
        val text = message.data.message ?: return
        // Hard caps, above any honest server's limits: a hostile one must not be able to hand
        // the chat a megabyte to lay out.
        callback.onChatReceived(sender.take(MAX_USERNAME_CHARS), text.take(MAX_CHAT_CHARS))
    }

    override suspend fun onTLS(message: WireMessage.TLS) {
        val startTLS = message.data.startTLS ?: return
        // Only the answer to our own request counts (PC acts on it only before login). An
        // unsolicited TLS line would otherwise re-send Hello and re-pin the transport.
        if (network.tls != TlsState.TLS_ASK) {
            loggy("Ignoring an unsolicited TLS message from the server")
            return
        }
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

    private suspend fun handleSingleUserSet(rawName: String, userData: UserSetData) {
        val userName = rawName.take(MAX_USERNAME_CHARS)
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
                    } else if (eventRoom != null && !inOurRoom) {
                        callback.onSomeoneLeftOtherRoom(userName, eventRoom)
                    }
                }
                event.joined != null && inOurRoom -> {
                    if (updated.none { it.name == userName } && updated.addNewUser(userName)) {
                        changed = true
                    }
                    callback.onSomeoneJoined(userName)
                }
                // Joined another room (a server without isolated rooms): a quiet notice, PC's
                // different-room OSD, and nothing in our roster.
                event.joined != null && eventRoom != null -> callback.onSomeoneJoinedOtherRoom(userName, eventRoom)
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
            } else if (inOurRoom && idx < 0 && updated.addNewUser(userName)) {
                // Moved into our room.
                changed = true
                callback.onSomeoneJoined(userName)
            }
        }

        userData.file?.let { file ->
            // Re-resolve the index — the join branch above may have just inserted them.
            val idx = updated.indexOfFirst { it.name == userName }
            val name = file.name?.take(MAX_FILENAME_CHARS)
            if (idx >= 0 && name != null) {
                val mediaFile = MediaFile().apply {
                    fileName = name
                    fileDuration = file.duration ?: 0.0
                    fileSize = file.size ?: ""
                }
                updated[idx] = updated[idx].copy(file = mediaFile)
                changed = true
            }
            // Announce file loads only for users we can see (our room).
            if (idx >= 0) callback.onSomeoneLoadedFile(userName, name ?: "", file.duration ?: 0.0)
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
            dispatcher.broadcastMessage(
                message = {
                    val line = if (isReady) Localization.strings.roomReadySetBy else Localization.strings.roomNotReadySetBy
                    line(userName, setBy)
                },
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
        // One atomic replacement: a clear then an addAll from this thread let the playlist
        // panel draw an empty list between the two.
        Snapshot.withMutableSnapshot {
            session.sharedPlaylist.clear()
            session.sharedPlaylist.addAll(files)
        }
        viewmodel.playlistManager.onServerPlaylist(user)
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
                viewmodel.protocol.endRoomChange()
            }
        }
    }

    /**
     * Adds an unknown user, or refuses when the roster is full. Says whether it was added.
     *
     * A hostile server streaming joins must not grow the roster without end, and both ways in,
     * a join event and a room switch, have to say so.
     */
    private fun MutableList<User>.addNewUser(name: String): Boolean {
        if (size >= Session.MAX_USERS) return false
        add(
            User(
                name = name,
                index = (maxOfOrNull { it.index } ?: 0) + 1,
                readiness = false,
                file = null,
                isController = false,
            )
        )
        return true
    }

    private fun handleControllerAuth(data: ControllerAuthData) {
        // Any answer settles a creation that is waiting on one, including a refusal.
        viewmodel.protocol.endRoomChange()
        callback.onHandleControllerAuth(data)
    }

    private companion object {
        /** Absolute ceilings on peer-supplied strings, well above any honest server's limits. */
        const val MAX_USERNAME_CHARS = Session.MAX_USERNAME_CHARS
        const val MAX_CHAT_CHARS = 2000
        const val MAX_FILENAME_CHARS = 512
    }
}
