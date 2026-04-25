package app.protocol.event

import androidx.lifecycle.viewModelScope
import app.AbstractManager
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
import app.preferences.value
import app.protocol.ClientMessage
import app.protocol.models.ConnectionState
import app.protocol.models.TlsState
import app.protocol.wire.ControllerAuthData
import app.protocol.wire.NewControlledRoom
import app.room.OSDCategory
import app.room.RoomViewmodel
import app.room.toFileData
import app.utils.loggy
import app.utils.timestampFromMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_attempting_connect
import syncplaymobile.shared.generated.resources.room_attempting_reconnection
import syncplaymobile.shared.generated.resources.room_attempting_tls
import syncplaymobile.shared.generated.resources.room_connected_to_server
import syncplaymobile.shared.generated.resources.room_connection_failed
import syncplaymobile.shared.generated.resources.room_guy_joined
import syncplaymobile.shared.generated.resources.room_guy_left
import syncplaymobile.shared.generated.resources.room_guy_paused
import syncplaymobile.shared.generated.resources.room_guy_played
import syncplaymobile.shared.generated.resources.room_isplayingfile
import syncplaymobile.shared.generated.resources.room_on_controller_auth_failed
import syncplaymobile.shared.generated.resources.room_on_controller_auth_success
import syncplaymobile.shared.generated.resources.room_on_newcontrolledroom
import syncplaymobile.shared.generated.resources.room_fastforwarded
import syncplaymobile.shared.generated.resources.room_rewinded
import syncplaymobile.shared.generated.resources.room_seeked
import syncplaymobile.shared.generated.resources.room_shared_playlist_changed
import syncplaymobile.shared.generated.resources.room_shared_playlist_updated
import syncplaymobile.shared.generated.resources.room_tls_not_supported
import syncplaymobile.shared.generated.resources.room_tls_supported
import syncplaymobile.shared.generated.resources.room_you_joined_room

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

    /** Triggers a platform haptic feedback event if the given preference is enabled */
    private fun hapticIf(pref: app.preferences.Pref<Boolean>) {
        if (pref.value()) viewmodel.uiState.triggerHaptic()
    }

    fun onSomeonePaused(pauser: String) {
        loggy("SYNCPLAY Protocol: Someone ($pauser) paused.")

        if (pauser.isNotSelf()) {
            hapticIf(HAPTIC_ON_PAUSED)
            onMainThread { viewmodel.player.seekTo(protocol.globalPositionMs.toLong()) }
            dispatcher.controlPlayback(Playback.PAUSE, false)
        }

        val osdMessage: suspend () -> String = {
            getString(
                resource = Res.string.room_guy_paused,
                pauser, timestampFromMillis(protocol.globalPositionMs)
            )
        }
        dispatcher.broadcastMessage(message = osdMessage, isChat = false)
        viewmodel.dispatchOSD(OSDCategory.SAME_ROOM, originUser = pauser, getter = osdMessage)
    }

    fun onSomeonePlayed(player: String) {
        loggy("SYNCPLAY Protocol: Someone ($player) unpaused.")

        if (player.isNotSelf()) {
            hapticIf(HAPTIC_ON_PLAYED)
            dispatcher.controlPlayback(Playback.PLAY, false)
        }

        val osdMessage: suspend () -> String = { getString(Res.string.room_guy_played, player) }
        dispatcher.broadcastMessage(message = osdMessage, isChat = false)
        viewmodel.dispatchOSD(OSDCategory.SAME_ROOM, originUser = player, getter = osdMessage)
    }

    fun onChatReceived(chatter: String, chatmessage: String) {
        loggy("SYNCPLAY Protocol: $chatter sent: $chatmessage")

        if (chatter.isNotSelf()) hapticIf(HAPTIC_ON_CHAT)
        dispatcher.broadcastMessage(message = { chatmessage }, isChat = true, chatter = chatter)
    }

    fun onSomeoneJoined(joiner: String) {
        loggy("SYNCPLAY Protocol: $joiner joined the room.")

        if (joiner.isNotSelf()) hapticIf(HAPTIC_ON_JOINED)
        val osdMessage: suspend () -> String = { getString(Res.string.room_guy_joined, joiner) }
        dispatcher.broadcastMessage(message = osdMessage, isChat = false)
        viewmodel.dispatchOSD(OSDCategory.SAME_ROOM, originUser = joiner, getter = osdMessage)
    }

    fun onSomeoneLeft(leaver: String) {
        if (viewmodel.protocol.isRoomChanging) {
            viewmodel.protocol.isRoomChanging = false
            return
        }

        loggy("SYNCPLAY Protocol: $leaver left the room.")

        hapticIf(HAPTIC_ON_LEFT)
        val osdMessage: suspend () -> String = { getString(Res.string.room_guy_left, leaver) }
        dispatcher.broadcastMessage(message = osdMessage, isChat = false)
        viewmodel.dispatchOSD(OSDCategory.SAME_ROOM, originUser = leaver, getter = osdMessage)

        viewmodel.viewModelScope.launch(Dispatchers.Main) {
            if (viewmodel.player.hasMedia() && PAUSE_ON_SOMEONE_LEAVE.value()) {
                this@RoomCallback.dispatcher.controlPlayback(Playback.PAUSE, true)
            }
        }

        if (leaver.isSelf()) onDisconnected()
    }

    fun onSomeoneSeeked(seeker: String, toPosition: Double) {
        loggy("SYNCPLAY Protocol: $seeker seeked to: $toPosition")

        if (seeker.isNotSelf()) hapticIf(HAPTIC_ON_SEEKED)
        onMainThread {
            val oldPosMs = if (seeker.isSelf()) dispatcher.pendingSeekFromMs else viewmodel.player.currentPositionMs()
            val newPosMs = toPosition.toLong() * 1000L

            if (seeker.isNotSelf()) viewmodel.player.seekTo(newPosMs)

            val osdMessage: suspend () -> String = {
                getString(Res.string.room_seeked, seeker, timestampFromMillis(oldPosMs), timestampFromMillis(newPosMs))
            }
            dispatcher.broadcastMessage(message = osdMessage, isChat = false)
            viewmodel.dispatchOSD(OSDCategory.SAME_ROOM, originUser = seeker, getter = osdMessage)

            /* Only record this seek for the local "Undo Seek" history if it was initiated
             * by the local user. Seeks coming from other users are still applied above
             * (via player.seekTo for the non-self case) but we don't allow undoing them —
             * doing so would let one user broadcast a counter-seek that surprises others. */
            if (seeker.isSelf()) {
                viewmodel.seeks.add(Pair(oldPosMs, newPosMs))
            }
        }
    }

    fun onSomeoneBehind(behinder: String, toPosition: Double) {
        loggy("SYNCPLAY Protocol: $behinder is behind. Rewinding to $toPosition")

        if (behinder.isNotSelf()) {
            onMainThread {
                viewmodel.player.seekTo((toPosition * 1000L).toLong())
                val osdMessage: suspend () -> String = { getString(Res.string.room_rewinded, behinder) }
                dispatcher.broadcastMessage(message = osdMessage, isChat = false)
                viewmodel.dispatchOSD(OSDCategory.SLOWDOWN, getter = osdMessage)
            }
        }
    }

    fun onSomeoneFastForwarded(setBy: String, toPosition: Double) {
        loggy("SYNCPLAY Protocol: Fast-forwarding to $toPosition due to time difference with $setBy")

        if (setBy.isNotSelf()) {
            onMainThread {
                viewmodel.player.seekTo((toPosition * 1000L).toLong())
                val osdMessage: suspend () -> String = { getString(Res.string.room_fastforwarded, setBy) }
                dispatcher.broadcastMessage(message = osdMessage, isChat = false)
                viewmodel.dispatchOSD(OSDCategory.SLOWDOWN, getter = osdMessage)
            }
        }
    }

    fun onReceivedList() {
        loggy("SYNCPLAY Protocol: Received list update.")
    }

    fun onSomeoneLoadedFile(person: String, file: String?, fileduration: Double?) {
        loggy("SYNCPLAY Protocol: $person loaded: $file - Duration: $fileduration")

        val osdMessage: suspend () -> String = {
            getString(Res.string.room_isplayingfile, person, file ?: "", timestampFromMillis(fileduration?.toLong()?.times(1000L) ?: 0))
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

        if (viewmodel.session.sharedPlaylist.isNotEmpty() && viewmodel.session.spIndex.intValue == -1) {
            //changePlaylistSelection(0)
        }

        if (user == "") return
        val osdMessage: suspend () -> String = { getString(Res.string.room_shared_playlist_updated, user) }
        dispatcher.broadcastMessage(message = osdMessage, isChat = false)
        viewmodel.dispatchOSD(OSDCategory.SAME_ROOM, originUser = user, getter = osdMessage)
    }

    fun onPlaylistIndexChanged(user: String, index: Int) {
        loggy("SYNCPLAY Protocol: Playlist index changed by $user to $index")

        viewmodel.viewModelScope.launch {
            if (user.isNotSelf()) {
                viewmodel.playlistManager.changePlaylistSelection(index)
            }
        }

        if (user == "") return
        val osdMessage: suspend () -> String = { getString(Res.string.room_shared_playlist_changed, user) }
        dispatcher.broadcastMessage(message = osdMessage, isChat = false)
        viewmodel.dispatchOSD(OSDCategory.SAME_ROOM, originUser = user, getter = osdMessage)
    }

    suspend fun onConnected() {
        loggy("SYNCPLAY Protocol: Connected!")

        network.state.value = ConnectionState.CONNECTED

        // Channel-health monitoring: starts a periodic List-probe and a State watchdog
        // that detects silent disconnects. Bound to this room session — stopped in
        // onDisconnected/onConnectionFailed and on ProtocolManager.invalidate(), so it
        // never leaks into solo mode or after the user leaves the room.
        protocol.startChannelHealthMonitoring()

        val initialReady = if (viewmodel.media == null && READY_FIRST_HAND.value()) true else session.ready.value
        network.sendAsync(ClientMessage.readiness(isReady = initialReady, manuallyInitiated = false))

        dispatcher.broadcastMessage(message = { getString(Res.string.room_connected_to_server) }, isChat = false)
        dispatcher.broadcastMessage(message = { getString(Res.string.room_you_joined_room, session.currentRoom) }, isChat = false)

        viewmodel.media?.let { network.sendAsync(ClientMessage.file(it.toFileData())) }

        for (m in session.outboundQueue) network.transmitPacket(m, isHello = false)
        session.outboundQueue.clear()
    }

    fun onConnectionAttempt() {
        loggy("SYNCPLAY Protocol: Attempting connection...")

        dispatcher.broadcastMessage(
            message = {
                getString(
                    Res.string.room_attempting_connect,
                    if (session.serverHost == "151.80.32.178") "syncplay.pl" else session.serverHost,
                    session.serverPort.toString()
                )
            },
            isChat = false
        )
    }

    fun onConnectionFailed() {
        loggy("SYNCPLAY Protocol: Connection failed :/")

        hapticIf(HAPTIC_ON_CONNECTION)
        protocol.stopChannelHealthMonitoring()
        network.state.value = ConnectionState.DISCONNECTED
        val osdMessage: suspend () -> String = { getString(Res.string.room_connection_failed) }
        dispatcher.broadcastMessage(message = osdMessage, isChat = false, isError = true)
        viewmodel.dispatchOSD(OSDCategory.WARNING, getter = osdMessage)
        network.reconnect()
    }

    fun onDisconnected() {
        loggy("SYNCPLAY Protocol: Disconnected.")

        hapticIf(HAPTIC_ON_CONNECTION)
        protocol.stopChannelHealthMonitoring()
        network.state.value = ConnectionState.DISCONNECTED
        val osdMessage: suspend () -> String = { getString(Res.string.room_attempting_reconnection) }
        dispatcher.broadcastMessage(message = osdMessage, isChat = false, isError = true)
        viewmodel.dispatchOSD(OSDCategory.WARNING, getter = osdMessage)
        network.reconnect()
    }

    fun onTLSCheck() {
        loggy("SYNCPLAY Protocol: Checking TLS...")

        dispatcher.broadcastMessage(message = { getString(Res.string.room_attempting_tls) }, isChat = false)
    }

    suspend fun onReceivedTLS(supported: Boolean) {
        loggy("SYNCPLAY Protocol: Received TLS...")

        if (supported) {
            dispatcher.broadcastMessage(message = { getString(Res.string.room_tls_supported) }, isChat = false)
            network.tls = TlsState.TLS_YES
            network.upgradeTls()
        } else {
            dispatcher.broadcastMessage(message = { getString(Res.string.room_tls_not_supported) }, isChat = false, isError = true)
            network.tls = TlsState.TLS_NO
        }

        dispatcher.sendHello()
    }

    // TODO: Copy +room:password to clipboard
    fun onNewControlledRoom(data: NewControlledRoom) {
        session.currentRoom = data.roomName
        session.currentOperatorPassword = data.password

        dispatcher.broadcastMessage(
            message = { getString(Res.string.room_on_newcontrolledroom, data.roomName, data.password) },
            isChat = false
        )
    }

    fun onHandleControllerAuth(data: ControllerAuthData) {
        val user = data.user ?: session.currentUsername

        network.sendAsync(ClientMessage.listRequest())

        val osdMessage: suspend () -> String = {
            getString(
                when (data.success) {
                    true -> Res.string.room_on_controller_auth_success
                    false -> Res.string.room_on_controller_auth_failed
                }, user
            )
        }
        dispatcher.broadcastMessage(message = osdMessage, isChat = false, isError = !data.success)
        if (!data.success) {
            viewmodel.dispatchOSD(OSDCategory.WARNING, getter = osdMessage)
        }
    }
}