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
import app.room.models.isolated
import app.utils.SyncClock
import app.utils.loggy
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The client-side [WireMessageHandler]. It reacts in the room (the group of people watching
 * together) to each [WireMessage] that the server sends: playback sync, the user list, chat, the
 * TLS upgrade and more. Only the server-to-client messages are overridden. The client-to-server
 * ones keep the no-op defaults.
 *
 * This class is where the typed protocol payloads meet the room logic. The wire models in
 * `app.protocol.wire` do not depend on the client. Only this class knows about [RoomViewmodel],
 * the player and the preferences.
 */
class RoomServerMessageHandler(private val viewmodel: RoomViewmodel) : WireMessageHandler {

    private val protocol get() = viewmodel.protocol
    private val callback get() = viewmodel.callback
    private val dispatcher get() = viewmodel.dispatcher
    private val network get() = viewmodel.networkManager
    private val session get() = viewmodel.session

    override suspend fun onHello(message: WireMessage.Hello) {
        /* The client asked for a TLS upgrade and got a Hello instead. The server skipped the
         * answer, so the socket is still plain text. When TLS is required, the client leaves. */
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

        // Ask for the user list now, and the server answers with a `List` message. Do not wait
        // for the write: this runs on the serial inbound consumer, which must not block on a
        // write while State packets queue behind it.
        network.sendAsync(WireMessage.listRequest())
        callback.onConnected()

        // The server's message of the day goes into chat, as on PC. It can hold an update
        // notice or house rules.
        data.motd?.takeIf { it.isNotBlank() }?.let { motd ->
            val text = motd.take(MAX_CHAT_CHARS)
            dispatcher.broadcastMessage(message = { text }, isChat = false)
        }
    }

    override suspend fun onState(message: WireMessage.State) {
        // The time stamp for the channel-health watchdog. It is set before anything else, so a
        // State that is later ignored still resets the "no State received" timer. The server is
        // alive either way.
        protocol.lastStateReceivedAt = SyncClock.now()

        val state = message.data
        var latencyCalculation: Double? = null
        var messageAge = 0.0

        state.ping?.let { ping ->
            latencyCalculation = ping.latencyCalculation
            val receivedAt = SyncClock.nowSeconds()
            ping.clientLatencyCalculation?.let { timestamp ->
                val serverRtt = ping.serverRtt ?: return@let
                protocol.pingService.receiveMessage(timestamp, serverRtt)
                protocol.clockOffset.observe(
                    ourSendTime = timestamp,
                    serverSendTime = ping.latencyCalculation ?: 0.0,
                    ourReceiveTime = receivedAt,
                )
            }
            // This message's own delay once the clock offset is usable, else the smoothed estimate
            // that the reference client uses.
            messageAge = ping.latencyCalculation?.let { protocol.clockOffset.messageAgeSeconds(it, receivedAt) }
                ?: protocol.pingService.forwardDelay
        }

        /* The decision itself is a pure function ([decideSync] in `SyncDecision.kt`). Everything
         * it needs is gathered here, and everything it decides is applied below, in order.
         *
         * Read, decide and write back are one step under the lock. The sync state is ten fields
         * written back as a whole, so a reconnect or a file load between the read and the write
         * would be erased. Nothing in here suspends. */
        var localSeek: LocalSeek? = null
        var sentPendingIntent = false
        var decisionMedia: MediaFile? = null
        var decisionRevision = 0L
        var pendingActions: List<Pair<SyncAction, PendingSeekPosition?>> = emptyList()
        synchronized(protocol.syncLock) {
            decisionMedia = viewmodel.media
            protocol.syncState = protocol.syncState.withIgnoringOnTheFly(state.ignoringOnTheFly)
            localSeek = protocol.consumeLocalSeekEcho(state)
            // A second user seek must survive the forced echo of the first seek. The latest queued
            // intent carries this ACK and sets the ignore gate again, before any outdated inbound
            // correction is decided. Nothing here waits for the main thread or the socket.
            sentPendingIntent = protocol.flushPendingLocalState(latencyCalculation)
            decisionRevision = protocol.localStateRevision
            val before = protocol.syncState
            val ctx = SyncContext(
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
            )
            decideSync(playstate = state.playstate, state = before, ctx = ctx).also { outcome ->
                viewmodel.sessionTap?.decision(state.playstate, before, ctx, outcome)
                protocol.syncState = outcome.state
                // Queue every accepted seek target before an ACK can open the server's stale-report
                // gate.
                pendingActions = outcome.actions.map { it to protocol.queueSeekPosition(it) }
            }
        }
        pendingActions.forEach { (action, pendingSeek) ->
            // A local seek was matched to its sent packet (localSeek) before a newer seek was
            // flushed. A self echo without a match is a duplicate or an override, not a new
            // gesture to undo.
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

        /* Answer with a State packet from this client. The condition is "the message carried a
         * playstate", not "it carried a position". The reference client reads a missing position
         * as zero, so a State whose playstate has no position still gets an ACK with a position. */
        if (protocol.lastGlobalUpdate != null && state.playstate != null) {
            val ackPos = if (Preferences.SYNC_DONT_SLOW_WITH_ME.value()) {
                // Use the room's current expected position, not the last 1 Hz snapshot. This
                // matches the extrapolation in Python's getGlobalPosition().
                protocol.extrapolatedGlobalPositionMs() / 1000.0
            } else {
                // While a newly loaded file is still catching up, this reports the room position
                // instead of the engine's position near 0, so the room is never pulled back.
                protocol.reportableStatePositionSec()
            }
            /* `play` must be the state just acknowledged from the server, never the transient
             * viewmodel.player.isPlaying(). The libvlc pause and play calls in VLCKit 4 are
             * asynchronous: pause() returns before the player changes state, so isPlaying() right
             * after pause() still reports true. An ACK with that stale value sends
             * State(play=true) after the server asked for a pause. The public Syncplay server
             * reads that as "this watcher unpauses the room" and broadcasts it. If the player
             * really fails to apply the state, the isNowPlaying divergence check in
             * ProtocolManager catches the failure after the player settles. */
            protocol.sendStateAcknowledgement(
                serverTime = latencyCalculation,
                // This app owns its embedded player, so dispatcher.sendSeek announces every real
                // seek. No seek can happen outside that path, so this periodic ACK never derives
                // one and omits doSeek. The PC client does the same: its ACK compares the player
                // with itself, never with the inbound server position.
                position = ackPos,
                /* The state just acknowledged from the server. The ACK falls back to
                 * globalPaused only when the inbound playstate has no paused field. */
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

    /** Applies one sync action. Actions must be applied in the order they arrive. */
    private fun apply(action: SyncAction, media: MediaFile?, revision: Long, pendingSeek: PendingSeekPosition?) {
        // The serial inbound consumer never waits on Main. Each command keeps the media it was
        // decided for, so a queued correction cannot seek or pause a newly loaded file.
        val job = viewmodel.viewModelScope.launch(Dispatchers.Main.immediate) {
            if (viewmodel.media !== media) return@launch
            // A local command can arrive after the decision but before this Main task, and its
            // seek or pause must win. Speed actions still apply: the decision already changed its
            // speedChanged flag or nudge level, so skipping only the player call could leave the
            // rate at 0.95 or one nudge off.
            val changesTransport = action !is SyncAction.SlowDown && action != SyncAction.RestoreSpeed && action !is SyncAction.Nudge
            if (changesTransport && !protocol.isLocalStateRevisionCurrent(revision)) return@launch
            when (action) {
                is SyncAction.FirstSync -> {
                    /* Set the expected pause state before touching the player. The collector of
                     * PlayerManager.isNowPlaying fires as soon as the engine applies pause() or
                     * play(). If expectedPaused still had its default then, the collector would see
                     * a divergence and send this first sync back to the server. */
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
                    // The slowdown notice of the PC client. The user must see why playback slowed.
                    viewmodel.dispatchOSD(OSDCategory.SLOWDOWN) { Localization.strings.roomSlowdownNotification(action.by) }
                }
                SyncAction.RestoreSpeed -> {
                    viewmodel.player.setSpeed(1.0)
                    viewmodel.dispatchOSD(OSDCategory.SLOWDOWN) { Localization.strings.roomSlowdownReverted }
                }
                // No notice: the nudge is too small for anyone to notice. The log line is for testers.
                is SyncAction.Nudge -> {
                    loggy("Sync: speed nudged to ${action.rate}")
                    viewmodel.player.setSpeed(action.rate)
                }
            }
        }
        // This also runs when cancellation stops the Main task before it starts.
        if (pendingSeek != null) job.invokeOnCompletion { protocol.completeSeekPosition(pendingSeek) }
    }


    override suspend fun onSet(message: WireMessage.Set) {
        val set = message.data
        // Process every key that is present, not only the first. Python's handleSet iterates the
        // whole Set dict, and a server may bundle several commands in one message.
        // playlistChange is applied before playlistIndex, so a bundled index can resolve against
        // the new entries.
        set.user?.let { handleUserSet(it) }
        set.ready?.let { handleReadySet(it) }
        set.playlistChange?.let { handlePlaylistChange(it) }
        set.playlistIndex?.let { handlePlaylistIndex(it) }
        set.newControlledRoom?.let { handleNewControlledRoom(it) }
        set.controllerAuth?.let { handleControllerAuth(it) }
        // No List request after every Set. The Python client changes its user list in place
        // from the Set it just received (`_SetUser` in protocols.py, `setReady` in client.py),
        // and [handleUserSet] and [handleReadySet] do the same. The public server pushes a List
        // broadcast only when persistent rooms are set up (`roomsDbFile` on the server), so the
        // client cannot rely on one.
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

        // Applied inline on the serial consumer, like every other roster change. A launch here
        // would let a List reply land after the Set that followed it on the wire.
        session.userList.emit(newList)
        callback.onReceivedList()
    }

    override suspend fun onChatBroadcast(message: WireMessage.ChatBroadcast) {
        val sender = message.data.username ?: return
        val text = message.data.message ?: return
        // Hard caps, above the limits of any honest server. A hostile server must not be able to
        // hand the chat a megabyte of text to lay out.
        callback.onChatReceived(sender.take(MAX_USERNAME_CHARS), text.take(MAX_CHAT_CHARS))
    }

    override suspend fun onTLS(message: WireMessage.TLS) {
        val startTLS = message.data.startTLS ?: return
        // Only the answer to this client's own request counts (PC acts on it only before login).
        // An unsolicited TLS line would otherwise send Hello again and pin the transport again.
        if (network.tls != TlsState.TLS_ASK) {
            loggy("Ignoring an unsolicited TLS message from the server")
            return
        }
        // The same substring test as the Python client: `"true" in answer`.
        val supported = startTLS.contains("true", ignoreCase = true)
        callback.onReceivedTLS(supported)
    }

    override suspend fun onError(message: WireMessage.Error) {
        val errorText = message.data.message ?: return
        loggy("Server error: $errorText")
        // Show server errors (for example "Wrong password supplied") in chat and on the OSD, like
        // PC's ui.showErrorMessage. The text is shown as sent, because the server writes it in
        // English.
        dispatcher.broadcastMessage(message = { errorText }, isChat = false, isError = true)
        viewmodel.dispatchOSD(OSDCategory.WARNING) { errorText }
    }

    // -----------------------------------------------------------
    // Set sub-routing
    // -----------------------------------------------------------

    /**
     * Updates the local user list straight from this Set, like Python's `_SetUser`
     * (protocols.py). The change shows at once, not after a List round trip or the next
     * periodic List probe.
     *
     * Wire shape per user:
     *  - `event.joined` (with an optional file): add the user to the list.
     *  - `event.left`: remove the user from the list.
     *  - A file and no event: update the file in place (`modUser`).
     */
    private suspend fun handleUserSet(userMap: Map<String, UserSetData>) {
        // Python iterates every user in the Set dict. The official server usually sends one
        // user, but the protocol allows several.
        for ((userName, userData) in userMap) handleSingleUserSet(userName, userData)
    }

    private suspend fun handleSingleUserSet(rawName: String, userData: UserSetData) {
        val userName = rawName.take(MAX_USERNAME_CHARS)
        val current = session.userList.value
        val updated = current.toMutableList()
        var changed = false

        // The broadcast carries the user's room. This user list holds only the current room
        // (unlike the all-rooms list on PC), so the room field decides membership:
        //  - A join or a switch into the current room adds the user to the list.
        //  - A switch out of the current room removes the user at once (Python's modUser case).
        // A missing room counts as the current room. Python always sends one, so this is only
        // a fallback.
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
                        callback.onSomeoneLeftOtherRoom(userName)
                    }
                }
                event.joined != null && inOurRoom -> {
                    if (updated.none { it.name == userName } && updated.addNewUser(userName)) {
                        changed = true
                    }
                    callback.onSomeoneJoined(userName)
                }
                // A join into another room (on a server without isolated rooms) gets a quiet
                // notice, like the different-room OSD on PC, and no roster entry.
                event.joined != null && eventRoom != null -> callback.onSomeoneJoinedOtherRoom(userName, eventRoom)
            }
        }

        // A room and no event means a room switch. The server's sendRoomSwitchMessage
        // broadcasts a bare `Set.user` with the new room.
        if (userData.event == null && eventRoom != null && userName != session.currentUsername) {
            val idx = updated.indexOfFirst { it.name == userName }
            if (!inOurRoom && idx >= 0) {
                // The user left the current room, so remove the user from the list at once.
                updated.removeAt(idx)
                changed = true
            } else if (inOurRoom && idx < 0 && updated.addNewUser(userName)) {
                // The user moved into the current room.
                changed = true
                callback.onSomeoneJoined(userName)
            }
        }

        userData.file?.let { file ->
            // Look up the index again, because the join branch above may have just added the user.
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
            // Announce file loads only for users in the current room.
            if (idx >= 0) callback.onSomeoneLoadedFile(userName, name ?: "", file.duration ?: 0.0)
        }

        if (changed) session.userList.emit(updated)
    }

    /**
     * Sets the user's readiness flag in the local user list, like Python's `setReady`
     * (client.py). The server sends `Set.ready` apart from `Set.user`, so this is its own branch.
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

        // A controller set the readiness of another user (the setOthersReadiness feature).
        // Announce it as PC does, or the only sign of the change is the icon.
        val setBy = ready.setBy
        if (setBy != null && setBy != userName) {
            dispatcher.broadcastMessage(
                message = {
                    val line = if (isReady) Localization.strings.roomReadySetBy else Localization.strings.roomNotReadySetBy
                    line(userName.isolated(), setBy.isolated())
                },
                isChat = false,
                people = listOf(userName, setBy),
            )
        }

        // When the local user is the one being set, update the local ready flag too. Otherwise
        // the ready button and the play gate (instaplayConditionsMet) disagree with the room.
        if (userName == session.currentUsername) {
            session.ready.value = isReady
        }
    }

    private fun handlePlaylistIndex(playlistIndex: PlaylistIndexData) {
        val user = playlistIndex.user ?: return
        val index = playlistIndex.index ?: return
        // A change by the local user already set spIndex before the round trip. The echo is
        // skipped, so an echo that arrives before the matching playlistChange cannot show a
        // stale list for a moment.
        if (user != session.currentUsername) {
            session.spIndex.intValue = index
        }
        callback.onPlaylistIndexChanged(user, index)
    }

    private fun handlePlaylistChange(playlistChange: PlaylistChangeData) {
        val user = playlistChange.user ?: ""
        val files = playlistChange.files ?: return
        // Replace the list in one atomic step. A separate clear and addAll from this thread
        // would let the playlist panel draw an empty list between the two.
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
     * Adds an unknown user, or refuses when the roster (the list of users in the room) is full.
     * Returns whether the user was added.
     *
     * A hostile server that streams joins must not grow the roster without end. Both paths that
     * add a user (a join event and a room switch) go through this cap.
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
        // Any answer, including a refusal, ends a room creation that waits for one.
        viewmodel.protocol.endRoomChange()
        callback.onHandleControllerAuth(data)
    }

    private companion object {
        /** Hard limits on strings from peers, well above the limits of any honest server. */
        const val MAX_USERNAME_CHARS = Session.MAX_USERNAME_CHARS
        const val MAX_CHAT_CHARS = 2000
        const val MAX_FILENAME_CHARS = 512
    }
}
