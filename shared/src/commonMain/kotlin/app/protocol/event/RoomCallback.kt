package app.protocol.event

import androidx.annotation.UiThread
import androidx.lifecycle.viewModelScope
import app.AbstractManager
import app.i18n.Localization
import app.player.Playback
import app.preferences.Preferences.HAPTIC_ON_CHAT
import app.preferences.Preferences.HAPTIC_ON_CONNECTION
import app.preferences.Preferences.HAPTIC_ON_JOINED
import app.preferences.Preferences.HAPTIC_ON_LEFT
import app.preferences.Preferences.HAPTIC_ON_PAUSED
import app.preferences.Preferences.HAPTIC_ON_PLAYED
import app.preferences.Preferences.HAPTIC_ON_PLAYLIST
import app.preferences.Preferences.HAPTIC_ON_SEEKED
import app.preferences.Preferences.PAUSE_ON_SOMEONE_LEAVE
import app.preferences.Preferences.READY_FIRST_HAND
import app.preferences.Preferences.TLS_REQUIRED
import app.preferences.value
import app.protocol.WireMessage
import app.protocol.models.ConnectionState
import app.protocol.models.TlsState
import app.protocol.wire.ControllerAuthData
import app.protocol.wire.NewControlledRoom
import app.room.OSDCategory
import app.room.RoomViewmodel
import app.room.models.BIDI_ISOLATE_END
import app.room.models.BIDI_ISOLATE_START
import app.room.toFileData
import app.utils.loggy
import app.utils.platformCallback
import app.utils.timestampFromMillis
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import app.protocol.OFFICIAL_SERVER_ADDRESS
import app.protocol.OFFICIAL_SERVER_NAME
import app.protocol.sync.roomToLocalMs
import app.protocol.sync.LocalSeek

/**
 * Handles incoming Syncplay protocol events, updating local state and broadcasting
 * user-facing messages. Counterpart to [RoomEventDispatcher] which handles outgoing actions.
 */
class RoomCallback(val viewmodel: RoomViewmodel) : AbstractManager(viewmodel) {
    val network = viewmodel.networkManager
    val dispatcher = viewmodel.dispatcher
    val protocol = viewmodel.protocol
    val session = viewmodel.protocol.session

    fun String.isSelf(): Boolean = (this == session.currentUsername)
    fun String.isNotSelf(): Boolean = (this != session.currentUsername)

    /** A username inside a system line keeps its own direction; chat rows already do this. */
    private fun String.isolated(): String = BIDI_ISOLATE_START + this + BIDI_ISOLATE_END

    /** Triggers a platform haptic feedback event if the given preference is enabled */
    private fun hapticIf(pref: app.preferences.Pref<Boolean>) {
        if (pref.value()) viewmodel.uiState.triggerHaptic()
    }

    fun onSomeonePaused(pauser: String) {
        loggy("SYNCPLAY Protocol: Someone ($pauser) paused.")

        // A pause is what makes a countdown possible at all, so re-read the room.
        viewmodel.readiness.evaluate()

        if (pauser.isNotSelf()) {
            hapticIf(HAPTIC_ON_PAUSED)
            // Snap to the room's *current* expected position, not the last 1 Hz snapshot —
            // mirrors python's SYNC_ON_PAUSE which seeks to getGlobalPosition() (extrapolated).
            // Otherwise we land on a frame from up to 1 s ago and look out of sync with peers.
            // Gate on media: seekTo on an unloaded VLCKit 4 player segfaults the same way
            // controlPlayback does — same NULL libvlc handle, same dispatch-queue race. The
            // controlPlayback call below has its own internal gate.
            if (viewmodel.media != null) {
                // The room's position translated into our own copy's time.
                val target = roomToLocalMs(protocol.extrapolatedGlobalPositionMs(), protocol.userTimeOffsetSeconds())
                onMainThread { viewmodel.player.seekTo(target.toLong()) }
            }
            dispatcher.controlPlayback(Playback.PAUSE, false)
        }

        val osdMessage: suspend () -> String = {
            Localization.strings.roomGuyPaused(pauser.isolated(), timestampFromMillis(protocol.globalPositionMs))
        }
        dispatcher.broadcastMessage(message = osdMessage, isChat = false)
        viewmodel.dispatchOSD(OSDCategory.SAME_ROOM, originUser = pauser, getter = osdMessage)
    }

    fun onSomeonePlayed(player: String) {
        loggy("SYNCPLAY Protocol: Someone ($player) unpaused.")

        // The room is moving; nothing left to count down to.
        viewmodel.readiness.evaluate()

        if (player.isNotSelf()) {
            hapticIf(HAPTIC_ON_PLAYED)
            dispatcher.controlPlayback(Playback.PLAY, false)
        }

        val osdMessage: suspend () -> String = { Localization.strings.roomGuyPlayed(player.isolated()) }
        dispatcher.broadcastMessage(message = osdMessage, isChat = false)
        viewmodel.dispatchOSD(OSDCategory.SAME_ROOM, originUser = player, getter = osdMessage)
    }

    fun onChatReceived(chatter: String, chatmessage: String) {
        loggy("SYNCPLAY Protocol: chat from $chatter (${chatmessage.length} chars)")

        if (chatter.isNotSelf()) hapticIf(HAPTIC_ON_CHAT)
        dispatcher.broadcastMessage(message = { chatmessage }, isChat = true, chatter = chatter)
    }

    fun onSomeoneJoined(joiner: String) {
        loggy("SYNCPLAY Protocol: $joiner joined the room.")

        if (joiner.isNotSelf()) hapticIf(HAPTIC_ON_JOINED)
        val osdMessage: suspend () -> String = { Localization.strings.roomGuyJoined(joiner.isolated()) }
        dispatcher.broadcastMessage(message = osdMessage, isChat = false)
        viewmodel.dispatchOSD(OSDCategory.SAME_ROOM, originUser = joiner, getter = osdMessage)
    }

    /** Presence in another room on a server without isolated rooms: a quiet notice, nothing more. */
    fun onSomeoneJoinedOtherRoom(joiner: String, room: String) {
        viewmodel.dispatchOSD(OSDCategory.OTHER_ROOM) { Localization.strings.roomGuyJoinedOtherRoom(joiner.isolated(), room.isolated()) }
    }

    fun onSomeoneLeftOtherRoom(leaver: String, room: String) {
        viewmodel.dispatchOSD(OSDCategory.OTHER_ROOM) { Localization.strings.roomGuyLeftOtherRoom(leaver.isolated(), room.isolated()) }
    }

    fun onSomeoneLeft(leaver: String) {
        // Our own "left" arrives when the server moves us between rooms (an isolated server tells
        // the old room, us included). It is not news, and it is not a lost connection: tearing
        // the socket down here started a reconnect on a healthy session.
        if (leaver.isSelf()) return

        loggy("SYNCPLAY Protocol: $leaver left the room.")

        hapticIf(HAPTIC_ON_LEFT)
        val osdMessage: suspend () -> String = { Localization.strings.roomGuyLeft(leaver.isolated()) }
        dispatcher.broadcastMessage(message = osdMessage, isChat = false)
        viewmodel.dispatchOSD(OSDCategory.SAME_ROOM, originUser = leaver, getter = osdMessage)

        viewmodel.viewModelScope.launch(Dispatchers.Main) {
            if (viewmodel.player.hasMedia() && PAUSE_ON_SOMEONE_LEAVE.value()) {
                // Pause LOCALLY only. PC's pauseOnLeave (client.py:474) calls the player's
                // setPaused directly without sending State — it does not broadcast. Passing
                // tellServer=true here would echo a redundant "X paused" to the room, and with
                // multiple clients each reacting to the same leave it multiplies the pause
                // events. Local-only matches PC.
                this@RoomCallback.dispatcher.controlPlayback(Playback.PAUSE, false)
            }
        }
    }

    /** The handler owns Main dispatch and protects the target until this call returns. */
    @UiThread
    fun onSomeoneSeeked(seeker: String, toPosition: Double, localSeek: LocalSeek? = null) {
        loggy("SYNCPLAY Protocol: $seeker seeked to: $toPosition")

        if (seeker.isNotSelf()) hapticIf(HAPTIC_ON_SEEKED)
        // A self echo records the sent gesture; an unmatched duplicate must not invent an origin.
        if (seeker.isSelf() && localSeek == null) return
        val oldPosMs = localSeek?.fromMs ?: viewmodel.player.currentPositionMs()
        // Multiply before truncating so subsecond seek targets survive the conversion.
        val newPosMs = (toPosition * 1000.0).toLong()

        if (seeker.isNotSelf() && viewmodel.media != null) viewmodel.player.seekTo(newPosMs)

        // Apply the seek even when it is too small to announce or record for undo.
        if (abs(oldPosMs - newPosMs) < SEEK_NOOP_THRESHOLD_MS) return
        val osdMessage: suspend () -> String = {
            Localization.strings.roomSeeked(seeker.isolated(), timestampFromMillis(oldPosMs), timestampFromMillis(newPosMs))
        }
        dispatcher.broadcastMessage(message = osdMessage, isChat = false)
        viewmodel.dispatchOSD(OSDCategory.SAME_ROOM, originUser = seeker, getter = osdMessage)

        // Undo belongs to the person who sought, not to every receiver of the room update.
        if (seeker.isSelf() && localSeek?.recordUndo == true) viewmodel.seeks.add(Pair(oldPosMs, newPosMs))
    }

    @UiThread
    fun onSomeoneBehind(behinder: String, toPosition: Double) {
        loggy("SYNCPLAY Protocol: $behinder is behind. Rewinding to $toPosition")

        if (behinder.isNotSelf()) {
            viewmodel.player.seekTo((toPosition * 1000L).toLong())
            val osdMessage: suspend () -> String = { Localization.strings.roomRewinded(behinder.isolated()) }
            dispatcher.broadcastMessage(message = osdMessage, isChat = false)
            viewmodel.dispatchOSD(OSDCategory.SLOWDOWN, getter = osdMessage)
        }
    }

    @UiThread
    fun onSomeoneFastForwarded(setBy: String, toPosition: Double) {
        loggy("SYNCPLAY Protocol: Fast-forwarding to $toPosition due to time difference with $setBy")

        if (setBy.isNotSelf()) {
            viewmodel.player.seekTo((toPosition * 1000L).toLong())
            val osdMessage: suspend () -> String = { Localization.strings.roomFastforwarded(setBy.isolated()) }
            dispatcher.broadcastMessage(message = osdMessage, isChat = false)
            viewmodel.dispatchOSD(OSDCategory.SLOWDOWN, getter = osdMessage)
        }
    }

    fun onReceivedList() {
        loggy("SYNCPLAY Protocol: Received list update.")
    }

    fun onSomeoneLoadedFile(person: String, file: String?, fileduration: Double?) {
        loggy("SYNCPLAY Protocol: $person loaded: $file - Duration: $fileduration")

        val osdMessage: suspend () -> String = {
            Localization.strings.roomIsplayingfile(person.isolated(), (file ?: "").isolated(), timestampFromMillis(fileduration?.toLong()?.times(1000L) ?: 0))
        }
        dispatcher.broadcastMessage(message = osdMessage, isChat = false)
        viewmodel.dispatchOSD(OSDCategory.SAME_ROOM, originUser = person, getter = osdMessage)

        if (person.isNotSelf()) {
            viewmodel.checkFileMismatches()
        }
    }

    fun onPlaylistUpdated(user: String) {
        loggy("SYNCPLAY Protocol: Playlist updated by $user")
        if (user.isNotEmpty()) hapticIf(HAPTIC_ON_PLAYLIST)

        if (user == "") return
        val osdMessage: suspend () -> String = { Localization.strings.roomSharedPlaylistUpdated(user.isolated()) }
        dispatcher.broadcastMessage(message = osdMessage, isChat = false)
        viewmodel.dispatchOSD(OSDCategory.SAME_ROOM, originUser = user, getter = osdMessage)
    }

    fun onPlaylistIndexChanged(user: String, index: Int) {
        loggy("SYNCPLAY Protocol: Playlist index changed by $user to $index")

        // Load for everyone, including our own echo. changePlaylistSelection() guards against
        // reloading a file we already have loaded, so the self-echo is a cheap no-op while a
        // genuine selection (local click or a peer's change) actually loads the file. Must not
        // gate on isNotSelf(): that suppresses loading a locally-clicked playlist item.
        viewmodel.viewModelScope.launch {
            viewmodel.playlistManager.changePlaylistSelection(index)
        }

        if (user == "") return
        val osdMessage: suspend () -> String = { Localization.strings.roomSharedPlaylistChanged(user.isolated()) }
        dispatcher.broadcastMessage(message = osdMessage, isChat = false)
        viewmodel.dispatchOSD(OSDCategory.SAME_ROOM, originUser = user, getter = osdMessage)
    }

    suspend fun onConnected() {
        loggy("SYNCPLAY Protocol: Connected! Handshake took ${network.sinceHandshakeStart()}")

        network.state.value = ConnectionState.CONNECTED

        // Channel-health monitoring: starts a periodic List-probe and a State watchdog
        // that detects silent disconnects. Bound to this room session — stopped in
        // onDisconnected/onConnectionFailed and on ProtocolManager.invalidate(), so it
        // never leaks into solo mode or after the user leaves the room.
        protocol.startChannelHealthMonitoring()

        // Watches the roster so the room can say who it is waiting for, and start on its own
        // once it is waiting for nobody.
        viewmodel.readiness.start()

        val initialReady = if (viewmodel.media == null && READY_FIRST_HAND.value()) true else session.ready.value
        network.sendAsync(WireMessage.readiness(isReady = initialReady, manuallyInitiated = false))

        dispatcher.broadcastMessage(message = { Localization.strings.roomConnectedToServer }, isChat = false)
        dispatcher.broadcastMessage(message = { Localization.strings.roomYouJoinedRoom(session.currentRoom) }, isChat = false)

        viewmodel.media?.let { network.sendAsync(WireMessage.file(it.toFileData())) }

        // Atomic snapshot-and-clear under the queue's lock — a failed transmit during the
        // replay loop below re-queues safely without racing the drain.
        val drained = session.drainOutbound()
        // Not awaited, one by one: this runs on the serial inbound consumer, and waiting here
        // stops that consumer reading State packets, which is what the channel watchdog counts.
        for (m in drained) network.sendRawAsync(m, queueable = true)

        // Mirror python's reIdentifyAsController — after every (re)connect, if we're
        // in a controlled room and we know the operator password, re-auth so the server
        // restores our control privileges. Without this, a network blip silently demotes
        // the operator and their pause/seek attempts get reverted by forcePositionUpdate.
        if (session.currentRoom.startsWith("+") && session.currentOperatorPassword.isNotEmpty()) {
            network.sendAsync(
                WireMessage.controllerAuth(
                    room = session.currentRoom,
                    password = session.currentOperatorPassword
                )
            )
        }
    }

    fun onConnectionAttempt() {
        loggy("SYNCPLAY Protocol: Attempting connection...")

        dispatcher.broadcastMessage(
            message = {
                Localization.strings.roomAttemptingConnect(if (session.serverHost == OFFICIAL_SERVER_ADDRESS) OFFICIAL_SERVER_NAME else session.serverHost, session.serverPort.toString())
            },
            isChat = false
        )
    }

    fun onConnectionFailed() {
        loggy("SYNCPLAY Protocol: Connection failed :/")

        hapticIf(HAPTIC_ON_CONNECTION)
        protocol.stopChannelHealthMonitoring()
        // A countdown outliving the connection would start playback into a room we have left.
        viewmodel.readiness.stop()
        network.state.value = ConnectionState.DISCONNECTED
        viewmodel.playlistManager.noteConnectionLost()
        val osdMessage: suspend () -> String = { Localization.strings.roomConnectionFailed }
        dispatcher.broadcastMessage(message = osdMessage, isChat = false, isError = true)
        viewmodel.dispatchOSD(OSDCategory.WARNING, getter = osdMessage)
        network.reconnect()
    }

    fun onDisconnected() {
        loggy("SYNCPLAY Protocol: Disconnected.")

        hapticIf(HAPTIC_ON_CONNECTION)
        protocol.stopChannelHealthMonitoring()
        viewmodel.readiness.stop()
        network.state.value = ConnectionState.DISCONNECTED
        viewmodel.playlistManager.noteConnectionLost()
        val osdMessage: suspend () -> String = { Localization.strings.roomAttemptingReconnection }
        dispatcher.broadcastMessage(message = osdMessage, isChat = false, isError = true)
        viewmodel.dispatchOSD(OSDCategory.WARNING, getter = osdMessage)
        network.reconnect()
    }

    /**
     * Encryption is required and this connection cannot give it. Say so once and stop: no Hello
     * in plain text, no retry loop that would only refuse again.
     */
    fun onTlsRequiredButUnavailable() {
        val refused: suspend () -> String = { Localization.strings.roomTlsRequiredDowngrade }
        dispatcher.broadcastMessage(message = refused, isChat = false, isError = true)
        viewmodel.dispatchOSD(OSDCategory.WARNING, getter = refused)
    }

    fun onTLSCheck() {
        loggy("SYNCPLAY Protocol: Checking TLS...")

        dispatcher.broadcastMessage(message = { Localization.strings.roomAttemptingTls }, isChat = false)
    }

    suspend fun onReceivedTLS(supported: Boolean) {
        loggy("Handshake: TLS answer ($supported) after ${network.sinceHandshakeStart()}")

        if (supported) {
            dispatcher.broadcastMessage(message = { Localization.strings.roomTlsSupported }, isChat = false)
            network.tls = TlsState.TLS_YES
            try {
                network.upgradeTls()
                // Only now is the socket really encrypted; the room's lock reads this.
                network.encrypted.value = true
                loggy("Handshake: TLS established after ${network.sinceHandshakeStart()}")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // A failed handshake (bad/expired cert, MITM, transport dropped mid-upgrade)
                // must surface as a connection failure, NOT propagate: the packet-dispatch
                // coroutine's catch only covers SerializationException, so anything else here
                // would crash the process. Treat the socket as dead and let the retry loop
                // take over (it re-arms TLS_ASK itself).
                network.encrypted.value = false
                loggy("TLS upgrade failed: ${e.stackTraceToString()}")
                val reason = e.message ?: e::class.simpleName ?: ""
                val failure: suspend () -> String = { Localization.strings.roomTlsHandshakeFailed(reason) }
                dispatcher.broadcastMessage(message = failure, isChat = false, isError = true)
                viewmodel.dispatchOSD(OSDCategory.WARNING, getter = failure)
                network.terminateExistingConnection()
                onConnectionFailed()
                return
            }
        } else {
            if (TLS_REQUIRED.value()) {
                onTlsRequiredButUnavailable()
                network.abortConnection()
                return
            }
            dispatcher.broadcastMessage(message = { Localization.strings.roomTlsNotSupported }, isChat = false, isError = true)
            network.tls = TlsState.TLS_NO
        }

        dispatcher.sendHello()
    }

    fun onNewControlledRoom(data: NewControlledRoom) {
        session.currentRoom = data.roomName
        session.currentOperatorPassword = data.password

        /* The notice has always told the user this is on their clipboard. Now it is. What goes
         * there is the operator join string, "room:password", which is the thing an operator
         * pastes into the room field to authenticate on the way in. */
        val operatorJoin = "${data.roomName}:${data.password}"
        runCatching { platformCallback.copyText(operatorJoin) }

        dispatcher.broadcastMessage(
            message = { Localization.strings.roomOnNewcontrolledroom(data.roomName, data.password, operatorJoin) },
            isChat = false
        )
    }

    fun onHandleControllerAuth(data: ControllerAuthData) {
        val user = data.user ?: session.currentUsername

        // Our own successful identification: keep the password, so the re-identification that
        // every reconnect performs (see onConnected) can restore control without asking again.
        if (data.success && user.isSelf() && session.lastControlPasswordAttempt.isNotEmpty()) {
            session.currentOperatorPassword = session.lastControlPasswordAttempt
        }
        // Whatever the answer, the attempt is spent. Keeping a refused one meant a later
        // success by somebody else could save the wrong password as ours.
        if (user.isSelf()) session.lastControlPasswordAttempt = ""

        network.sendAsync(WireMessage.listRequest())

        val osdMessage: suspend () -> String = {
            (when (data.success) {
                    true -> Localization.strings.roomOnControllerAuthSuccess
                    false -> Localization.strings.roomOnControllerAuthFailed
                })(user.isolated())
        }
        dispatcher.broadcastMessage(message = osdMessage, isChat = false, isError = !data.success)
        if (!data.success) {
            viewmodel.dispatchOSD(OSDCategory.WARNING, getter = osdMessage)
        }
    }

    companion object {
        /**
         * Below this from→to delta (ms), a seek is considered visually a no-op and we
         * skip the room/OSD announcement plus the Undo Seek history entry. 1 second is
         * also the protocol-wide [ProtocolManager.SEEK_THRESHOLD], so anything tighter
         * than that wouldn't even register as a seek in the desync-detection algorithm
         * on either side.
         */
        const val SEEK_NOOP_THRESHOLD_MS = 1000L
    }
}
