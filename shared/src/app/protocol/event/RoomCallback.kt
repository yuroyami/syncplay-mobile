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
import app.protocol.SHARED_PLAYLISTS_MIN_VERSION
import app.protocol.WireMessage
import app.protocol.models.ConnectionState
import app.protocol.models.RoomFeatures
import app.protocol.versionAtLeast
import app.protocol.models.TlsState
import app.protocol.network.ConnectionFailure
import app.protocol.wire.ControllerAuthData
import app.protocol.wire.NewControlledRoom
import app.room.OSDCategory
import app.room.RoomViewmodel
import app.room.models.isolated
import app.room.toFileData
import app.utils.SyncClock
import app.utils.loggy
import app.utils.platformCallback
import app.utils.timestampFromMillis
import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
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

    /** Who left last and when, for the line that joins a leave and a pause. Any room counts, as in Syncplay PC. */
    @Volatile
    private var lastLeft: Pair<String, Instant>? = null

    fun String.isSelf(): Boolean = (this == session.currentUsername)
    fun String.isNotSelf(): Boolean = (this != session.currentUsername)

    /** Triggers a platform haptic feedback event if the given preference is enabled. */
    private fun hapticIf(pref: app.preferences.Pref<Boolean>) {
        if (pref.value()) viewmodel.uiState.triggerHaptic()
    }

    fun onSomeonePaused(pauser: String) {
        loggy("SYNCPLAY Protocol: Someone ($pauser) paused.")

        // A pause is what makes a countdown possible at all, so re-read the room.
        viewmodel.readiness.evaluate()

        if (pauser.isNotSelf()) {
            hapticIf(HAPTIC_ON_PAUSED)
            // Snap to the room's *current* expected position, not the last 1 Hz snapshot. This
            // mirrors Python's SYNC_ON_PAUSE, which seeks to the extrapolated getGlobalPosition().
            // Otherwise we land on a frame from up to 1 s ago and look out of sync with peers.
            // Gate on media: VLCKit 4 crashes natively on a seek with no media loaded. The
            // controlPlayback call below has its own media gate.
            if (viewmodel.media != null) {
                // The room's position translated into our own copy's time.
                val target = roomToLocalMs(protocol.extrapolatedGlobalPositionMs(), protocol.userTimeOffsetSeconds())
                onMainThread { viewmodel.player.seekTo(target.toLong()) }
            }
            dispatcher.controlPlayback(Playback.PAUSE, false)
        }

        // A pause soon after a leave gets one line that names both, as in Syncplay PC (`_serverPaused`).
        val leaver = lastLeft?.takeIf { (_, at) -> SyncClock.now() - at <= LEFT_PAUSED_WINDOW }?.first
        val osdMessage: suspend () -> String = {
            if (leaver != null) {
                Localization.strings.roomLeftPaused(leaver.isolated(), pauser.isolated())
            } else {
                Localization.strings.roomGuyPaused(pauser.isolated(), timestampFromMillis(protocol.globalPositionMs))
            }
        }
        dispatcher.broadcastMessage(message = osdMessage, isChat = false, people = listOfNotNull(leaver, pauser))
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
        dispatcher.broadcastMessage(message = osdMessage, isChat = false, people = listOf(player))
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
        val osdMessage: suspend () -> String = { Localization.strings.roomGuyJoined(joiner.isolated(), session.currentRoom.isolated()) }
        dispatcher.broadcastMessage(message = osdMessage, isChat = false, people = listOf(joiner))
        viewmodel.dispatchOSD(OSDCategory.SAME_ROOM, originUser = joiner, getter = osdMessage)
    }

    /** We moved to [room] with `/room`. Syncplay writes the same join line as on connect. */
    fun onYouSwitchedRoom(room: String) {
        dispatcher.broadcastMessage(
            message = { Localization.strings.roomYouJoinedRoom(session.currentUsername.isolated(), room.isolated()) },
            isChat = false,
        )
    }

    /** [user] moved from our room to [room]. As in Syncplay, the line names the new room. */
    fun onSomeoneSwitchedAway(user: String, room: String) {
        val osdMessage: suspend () -> String = { Localization.strings.roomGuyJoined(user.isolated(), room.isolated()) }
        dispatcher.broadcastMessage(message = osdMessage, isChat = false, people = listOf(user))
        viewmodel.dispatchOSD(OSDCategory.SAME_ROOM, originUser = user, getter = osdMessage)
    }

    /** Presence in another room on a server without isolated rooms: a quiet notice, nothing more. */
    fun onSomeoneJoinedOtherRoom(joiner: String, room: String) {
        viewmodel.dispatchOSD(OSDCategory.OTHER_ROOM) { Localization.strings.roomGuyJoinedOtherRoom(joiner.isolated(), room.isolated()) }
    }

    // Syncplay's line names no room: a person who leaves the server has left every room.
    fun onSomeoneLeftOtherRoom(leaver: String) {
        lastLeft = leaver to SyncClock.now()
        viewmodel.dispatchOSD(OSDCategory.OTHER_ROOM) { Localization.strings.roomGuyLeftOtherRoom(leaver.isolated()) }
    }

    fun onSomeoneLeft(leaver: String) {
        // Our own "left" arrives when the server moves us between rooms (an isolated server tells
        // the old room, us included). It is not news, and it is not a lost connection: tearing
        // the socket down here would start a reconnect on a healthy session.
        if (leaver.isSelf()) return

        loggy("SYNCPLAY Protocol: $leaver left the room.")
        lastLeft = leaver to SyncClock.now()

        hapticIf(HAPTIC_ON_LEFT)
        val osdMessage: suspend () -> String = { Localization.strings.roomGuyLeft(leaver.isolated()) }
        dispatcher.broadcastMessage(message = osdMessage, isChat = false, people = listOf(leaver))
        viewmodel.dispatchOSD(OSDCategory.SAME_ROOM, originUser = leaver, getter = osdMessage)

        viewmodel.viewModelScope.launch(Dispatchers.Main) {
            if (viewmodel.player.hasMedia() && PAUSE_ON_SOMEONE_LEAVE.value()) {
                // Pause LOCALLY only. With pauseOnLeave set, PC's `onDisconnect` (client.py) calls
                // `setPaused(True)` on the player without sending a State, so it does not
                // broadcast. Passing tellServer=true here would echo a redundant "X paused" to the
                // room, and with several clients reacting to the same leave, the pause events
                // multiply. Local-only matches PC.
                this@RoomCallback.dispatcher.controlPlayback(Playback.PAUSE, false)
            }
        }
    }

    /**
     * Applies and announces a seek from the room. The message handler calls it on Main, and
     * keeps the seek target pending until this call returns.
     */
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
        dispatcher.broadcastMessage(message = osdMessage, isChat = false, people = listOf(seeker))
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
            dispatcher.broadcastMessage(message = osdMessage, isChat = false, people = listOf(behinder))
            viewmodel.dispatchOSD(OSDCategory.SLOWDOWN, getter = osdMessage)
        }
    }

    @UiThread
    fun onSomeoneFastForwarded(setBy: String, toPosition: Double) {
        loggy("SYNCPLAY Protocol: Fast-forwarding to $toPosition due to time difference with $setBy")

        if (setBy.isNotSelf()) {
            viewmodel.player.seekTo((toPosition * 1000L).toLong())
            val osdMessage: suspend () -> String = { Localization.strings.roomFastforwarded(setBy.isolated()) }
            dispatcher.broadcastMessage(message = osdMessage, isChat = false, people = listOf(setBy))
            viewmodel.dispatchOSD(OSDCategory.SLOWDOWN, getter = osdMessage)
        }
    }

    fun onReceivedList() {
        loggy("SYNCPLAY Protocol: Received list update.")
    }

    fun onSomeoneLoadedFile(person: String, file: String?, fileduration: Double?) {
        loggy("SYNCPLAY Protocol: $person loaded: $file - Duration: $fileduration")

        val osdMessage: suspend () -> String = {
            val name = (file ?: "").isolated()
            val duration = timestampFromMillis(fileduration?.toLong()?.times(1000L) ?: 0)
            // Syncplay PC names the room on the line about our own file.
            if (person.isSelf()) {
                Localization.strings.roomIsplayingfileInRoom(person.isolated(), name, duration, session.currentRoom.isolated())
            } else {
                Localization.strings.roomIsplayingfile(person.isolated(), name, duration)
            }
        }
        dispatcher.broadcastMessage(message = osdMessage, isChat = false, people = listOf(person))
        viewmodel.dispatchOSD(OSDCategory.SAME_ROOM, originUser = person, getter = osdMessage)

        if (person.isNotSelf()) {
            viewmodel.checkFileMismatches(changedUser = person)
        }
    }

    fun onPlaylistUpdated(user: String) {
        loggy("SYNCPLAY Protocol: Playlist updated by $user")
        if (user.isNotEmpty()) hapticIf(HAPTIC_ON_PLAYLIST)

        if (user == "") return
        val osdMessage: suspend () -> String = { Localization.strings.roomSharedPlaylistUpdated(user.isolated()) }
        dispatcher.broadcastMessage(message = osdMessage, isChat = false, people = listOf(user))
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
        dispatcher.broadcastMessage(message = osdMessage, isChat = false, people = listOf(user))
        viewmodel.dispatchOSD(OSDCategory.SAME_ROOM, originUser = user, getter = osdMessage)
    }

    suspend fun onConnected() {
        loggy("SYNCPLAY Protocol: Connected! Handshake took ${network.sinceHandshakeStart()}")

        network.state.value = ConnectionState.CONNECTED
        network.lastFailure.value = null

        // Channel-health monitoring: starts a periodic List probe, a State watchdog that detects
        // silent disconnects, and the playback divergence check. Bound to this room session:
        // stopped in onDisconnected/onConnectionFailed and on ProtocolManager.invalidate(), so
        // it never leaks into solo mode or past the moment the user leaves the room.
        protocol.startChannelHealthMonitoring()

        // Watches the roster so the room can say who it is waiting for, and start on its own
        // once it is waiting for nobody.
        viewmodel.readiness.start()

        val initialReady = if (viewmodel.media == null && READY_FIRST_HAND.value()) true else session.ready.value
        network.sendAsync(WireMessage.readiness(isReady = initialReady, manuallyInitiated = false))

        // No join line for our own join, as in Syncplay PC: the server does not send us our own join.
        dispatcher.broadcastMessage(message = { Localization.strings.roomConnectedToServer }, isChat = false)

        viewmodel.media?.let { network.sendAsync(WireMessage.file(it.toFileData())) }

        // Atomic snapshot-and-clear under the queue's lock, so a failed transmit during the
        // replay loop below re-queues safely without racing the drain.
        val drained = session.drainOutbound()
        // Not awaited, one by one: this runs on the serial inbound consumer, and waiting here
        // stops that consumer reading State packets, which is what the channel watchdog counts.
        for (m in drained) network.sendRawAsync(m, queueable = true)

        // Mirrors Python's reIdentifyAsController: after every connect or reconnect, if we are
        // in a controlled room and know the operator password, authenticate again so the server
        // restores our control. Without this, a short network drop silently demotes the
        // operator, and forcePositionUpdate reverts their pause and seek attempts.
        if (session.currentRoom.startsWith("+") && session.currentOperatorPassword.isNotEmpty()) {
            dispatcher.identifyAsOperator(session.currentOperatorPassword)
        }
    }

    fun onConnectionAttempt() {
        loggy("SYNCPLAY Protocol: Attempting connection...")

        dispatcher.broadcastMessage(
            message = { Localization.strings.roomAttemptingConnect(serverName(), session.serverPort.toString()) },
            isChat = false
        )
    }

    /** The first socket of this room opened. Syncplay PC names the server and the address it reached. */
    fun onServerReached(ip: String) {
        dispatcher.broadcastMessage(message = { Localization.strings.roomServerReached(serverName(), ip) }, isChat = false)
    }

    /** The server's address as the connection lines show it: the official server by its name. */
    private fun serverName(): String =
        if (session.serverHost == OFFICIAL_SERVER_ADDRESS) OFFICIAL_SERVER_NAME else session.serverHost

    /**
     * Says why the playlist actions are missing, as Syncplay PC does after each Hello
     * (`setServerVersion`): the server is too old for shared playlists, or it turned them off.
     */
    fun onServerFeatures(serverVersion: String?, features: RoomFeatures) {
        val tooOld = serverVersion != null && versionAtLeast(serverVersion, SHARED_PLAYLISTS_MIN_VERSION) == false
        if (!tooOld && features.supportsSharedPlaylists) return
        dispatcher.broadcastMessage(isChat = false, isError = true) {
            if (tooOld) Localization.strings.roomSharedPlaylistsNotSupported(SHARED_PLAYLISTS_MIN_VERSION, serverVersion.orEmpty())
            else Localization.strings.roomSharedPlaylistsDisabled
        }
    }

    /** No State from the server for too long. Syncplay PC says so before the reconnect line. */
    fun onServerTimedOut() {
        dispatcher.broadcastMessage(message = { Localization.strings.roomServerTimeout }, isChat = false, isError = true)
        onDisconnected()
    }

    /**
     * A socket that closed during the handshake, with no known reason. The lines that arrived
     * before the close are handled first. When one of them was a server Error, [onServerError] has
     * ended the attempt for good, and this close reports nothing.
     */
    fun onConnectionFailed() = network.afterPendingLines {
        if (network.state.value == ConnectionState.CONNECTING) onConnectionFailed(null)
    }

    /**
     * The server sent an Error, and it closes the socket after it. Examples are a wrong server
     * password and a malformed Hello. A retry would be refused again, so the retries stop, as in
     * Syncplay PC (`dropWithError`). The room stays disconnected until the person connects again.
     */
    fun onServerError() {
        loggy("SYNCPLAY Protocol: The server refused the connection. The retries stop.")
        hapticIf(HAPTIC_ON_CONNECTION)
        protocol.stopChannelHealthMonitoring()
        // A countdown outliving the connection would start playback into a room we have left.
        viewmodel.readiness.stop()
        viewmodel.playlistManager.noteConnectionLost()
        network.abortConnection()
        dispatcher.broadcastMessage(message = { Localization.strings.roomDisconnected }, isChat = false, isError = true)
    }

    /** A failed attempt. [reason] is said in the notice, and the status line keeps it until a connect. */
    fun onConnectionFailed(reason: ConnectionFailure?) {
        loggy("SYNCPLAY Protocol: Connection failed :/ ${reason ?: ""}")
        // The room is asking whether to trust the server's certificate. A retry would only ask again.
        if (network.untrustedCertificate.value != null) return
        if (reason != null) network.lastFailure.value = reason

        hapticIf(HAPTIC_ON_CONNECTION)
        protocol.stopChannelHealthMonitoring()
        // A countdown outliving the connection would start playback into a room we have left.
        viewmodel.readiness.stop()
        network.state.value = ConnectionState.DISCONNECTED
        viewmodel.playlistManager.noteConnectionLost()
        val osdMessage: suspend () -> String = {
            val s = Localization.strings
            when (reason) {
                ConnectionFailure.NameNotFound -> s.roomConnectionFailedName
                ConnectionFailure.Refused -> s.roomConnectionFailedRefused
                ConnectionFailure.TimedOut -> s.roomConnectionFailedTimeout
                // The encryption failure already said its reason in its own notice.
                ConnectionFailure.Encryption, null -> s.roomConnectionFailed
            }
        }
        dispatcher.broadcastMessage(message = osdMessage, isChat = false, isError = true)
        viewmodel.dispatchOSD(OSDCategory.WARNING, getter = osdMessage)
        network.reconnect()
    }

    fun onDisconnected() {
        loggy("SYNCPLAY Protocol: Disconnected.")
        if (network.untrustedCertificate.value != null) return

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
            network.tls = TlsState.TLS_YES
            network.tlsVersion = null
            try {
                network.upgradeTls()
                // Only now is the socket really encrypted; the room's lock icon reads this.
                network.encrypted.value = true
                loggy("Handshake: TLS established after ${network.sinceHandshakeStart()}")
                // After the handshake, as Syncplay does. A transport that cannot name the version says TLS.
                val version = network.tlsVersion ?: "TLS"
                dispatcher.broadcastMessage(message = { Localization.strings.roomTlsSupported(version) }, isChat = false)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // A failed handshake (bad or expired certificate, MITM, transport dropped during
                // the upgrade) must surface as a connection failure, NOT propagate. The inbound
                // consumer would only log it, and the room would sit on a half-upgraded socket
                // until the handshake deadline. Treat the socket as dead and let the retry loop
                // take over (it re-arms TLS_ASK itself).
                network.encrypted.value = false
                // A certificate that nothing trusts yet is a question for the person, not a
                // failure: the room shows its fingerprint, and nothing retries meanwhile.
                if (network.untrustedCertificate.value != null) {
                    loggy("TLS upgrade stopped at a certificate that is not trusted yet")
                    network.abortConnection()
                    return
                }
                loggy("TLS upgrade failed: ${e.stackTraceToString()}")
                val reason = e.message ?: e::class.simpleName ?: ""
                val failure: suspend () -> String = { Localization.strings.roomTlsHandshakeFailed(reason) }
                dispatcher.broadcastMessage(message = failure, isChat = false, isError = true)
                viewmodel.dispatchOSD(OSDCategory.WARNING, getter = failure)
                network.terminateExistingConnection()
                onConnectionFailed(ConnectionFailure.Encryption)
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

        /* The notice tells the user that the operator join string is on their clipboard. The
         * string is "room:password", which an operator pastes into the room field to
         * authenticate on the way in. */
        val operatorJoin = "${data.roomName}:${data.password}"
        runCatching { platformCallback.copyText(operatorJoin) }

        dispatcher.broadcastMessage(
            message = { Localization.strings.roomOnNewcontrolledroom(data.roomName, data.password, data.roomName, operatorJoin) },
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
        // Whatever the answer, the attempt is spent. Keeping a refused one would let a later
        // success by somebody else save the wrong password as ours.
        if (user.isSelf()) session.lastControlPasswordAttempt = ""

        network.sendAsync(WireMessage.listRequest())

        // As in Syncplay, only the person who tried hears that the password was refused.
        if (!data.success && user.isNotSelf()) return
        // A server without isolated rooms sends every success to every room. As in Syncplay, the
        // line shows only for our own room.
        if (data.success && data.room != null && data.room != session.currentRoom) return

        val osdMessage: suspend () -> String = {
            (when (data.success) {
                    true -> Localization.strings.roomOnControllerAuthSuccess
                    false -> Localization.strings.roomOnControllerAuthFailed
                })(user.isolated())
        }
        dispatcher.broadcastMessage(message = osdMessage, isChat = false, isError = !data.success, people = listOf(user))
        if (!data.success) {
            viewmodel.dispatchOSD(OSDCategory.WARNING, getter = osdMessage)
        }
    }

    companion object {
        /** A pause this soon after a leave gets the joined line. Syncplay PC uses its `OSD_DURATION`. */
        val LEFT_PAUSED_WINDOW = 3.seconds

        /**
         * Below this from-to distance in milliseconds, a seek counts as a visual no-op: the room
         * message, the OSD notice (the on-screen message over the video) and the undo entry are
         * skipped. One second matches PC's `SEEK_THRESHOLD` ([ProtocolManager.SEEK_THRESHOLD]),
         * below which the PC client does not detect a seek at all.
         */
        const val SEEK_NOOP_THRESHOLD_MS = 1000L
    }
}
