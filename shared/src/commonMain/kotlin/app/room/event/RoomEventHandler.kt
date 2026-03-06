package app.room.event

import androidx.lifecycle.viewModelScope
import app.AbstractManager
import app.room.RoomViewmodel
import app.utils.loggy
import app.utils.timestampFromMillis
import app.preferences.Preferences.PAUSE_ON_SOMEONE_LEAVE
import app.preferences.Preferences.READY_FIRST_HAND
import app.preferences.value
import app.protocol.models.CONNECTIONSTATE
import app.protocol.models.ClientMessage
import app.protocol.models.TlsState
import app.protocol.server.Set
import app.protocol.server.Set.ControllerAuthResponse
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
class RoomEventHandler(val viewmodel: RoomViewmodel) : AbstractManager(viewmodel) {

    val broadcaster = viewmodel.roomOut

    fun onSomeonePaused(pauser: String) {
        loggy("SYNCPLAY Protocol: Someone ($pauser) paused.")

        if (pauser != viewmodel.session.currentUsername) broadcaster.pausePlayback()

        broadcaster.broadcastMessage(
            message = { getString(Res.string.room_guy_paused, pauser, timestampFromMillis(viewmodel.protocolManager.globalPositionMs)) },
            isChat = false
        )
    }

    fun onSomeonePlayed(player: String) {
        loggy("SYNCPLAY Protocol: Someone ($player) unpaused.")

        if (player != viewmodel.session.currentUsername) broadcaster.playPlayback()

        broadcaster.broadcastMessage(message = { getString(Res.string.room_guy_played, player) }, isChat = false)
    }

    fun onChatReceived(chatter: String, chatmessage: String) {
        loggy("SYNCPLAY Protocol: $chatter sent: $chatmessage")

        broadcaster.broadcastMessage(message = { chatmessage }, isChat = true, chatter = chatter)
    }

    fun onSomeoneJoined(joiner: String) {
        loggy("SYNCPLAY Protocol: $joiner joined the room.")

        broadcaster.broadcastMessage(message = { getString(Res.string.room_guy_joined, joiner) }, isChat = false)
    }

    fun onSomeoneLeft(leaver: String) {
        if (viewmodel.protocolManager.isRoomChanging) {
            viewmodel.protocolManager.isRoomChanging = false
            return
        }

        loggy("SYNCPLAY Protocol: $leaver left the room.")

        broadcaster.broadcastMessage(message = { getString(Res.string.room_guy_left, leaver) }, isChat = false)

        viewmodel.viewModelScope.launch(Dispatchers.Main) {
            if (viewmodel.player.hasMedia() && PAUSE_ON_SOMEONE_LEAVE.value()) {
                broadcaster.pausePlayback()
            }
        }

        if (leaver == viewmodel.session.currentUsername) onDisconnected()
    }

    fun onSomeoneSeeked(seeker: String, toPosition: Double) {
        loggy("SYNCPLAY Protocol: $seeker seeked to: $toPosition")

        onMainThread {
            val oldPosMs = viewmodel.player.currentPositionMs()
            val newPosMs = toPosition.toLong() * 1000L

            broadcaster.broadcastMessage(
                message = { getString(Res.string.room_seeked, seeker, timestampFromMillis(oldPosMs), timestampFromMillis(newPosMs)) },
                isChat = false
            )

            viewmodel.seeks.add(Pair(oldPosMs, newPosMs))

            if (seeker == viewmodel.session.currentUsername) viewmodel.player.seekTo(newPosMs)
        }
    }

    fun onSomeoneBehind(behinder: String, toPosition: Double) {
        loggy("SYNCPLAY Protocol: $behinder is behind. Rewinding to $toPosition")

        onMainThread { viewmodel.player.seekTo((toPosition * 1000L).toLong()) }

        broadcaster.broadcastMessage(message = { getString(Res.string.room_rewinded, behinder) }, isChat = false)
    }

    fun onReceivedList() {
        loggy("SYNCPLAY Protocol: Received list update.")
    }

    fun onSomeoneLoadedFile(person: String, file: String?, fileduration: Double?) {
        loggy("SYNCPLAY Protocol: $person loaded: $file - Duration: $fileduration")

        broadcaster.broadcastMessage(
            message = { getString(Res.string.room_isplayingfile, person, file ?: "", timestampFromMillis(fileduration?.toLong()?.times(1000L) ?: 0)) },
            isChat = false
        )

        viewmodel.checkFileMismatches()
    }

    fun onPlaylistUpdated(user: String) {
        loggy("SYNCPLAY Protocol: Playlist updated by $user")

        if (viewmodel.session.sharedPlaylist.isNotEmpty() && viewmodel.session.spIndex.intValue == -1) {
            //changePlaylistSelection(0)
        }

        if (user == "") return
        broadcaster.broadcastMessage(message = { getString(Res.string.room_shared_playlist_updated, user) }, isChat = false)
    }

    fun onPlaylistIndexChanged(user: String, index: Int) {
        loggy("SYNCPLAY Protocol: Playlist index changed by $user to $index")

        viewmodel.viewModelScope.launch {
            viewmodel.playlistManager.changePlaylistSelection(index)
        }

        if (user == "") return
        broadcaster.broadcastMessage(message = { getString(Res.string.room_shared_playlist_changed, user) }, isChat = false)
    }

    suspend fun onConnected() {
        loggy("SYNCPLAY Protocol: Connected!")

        viewmodel.networkManager.state = CONNECTIONSTATE.STATE_CONNECTED

        viewmodel.networkManager.sendAsync<ClientMessage.Readiness> {
            isReady = if (viewmodel.media == null && READY_FIRST_HAND.value()) true
            else viewmodel.session.ready.value
            manuallyInitiated = false
        }

        broadcaster.broadcastMessage(message = { getString(Res.string.room_connected_to_server) }, isChat = false)
        broadcaster.broadcastMessage(message = { getString(Res.string.room_you_joined_room, viewmodel.session.currentRoom) }, isChat = false)

        if (viewmodel.media != null) {
            viewmodel.networkManager.sendAsync<ClientMessage.File> { this@sendAsync.media = viewmodel.media }
        }

        for (m in viewmodel.session.outboundQueue) viewmodel.networkManager.transmitPacket(m)
        viewmodel.session.outboundQueue.clear()
    }

    fun onConnectionAttempt() {
        loggy("SYNCPLAY Protocol: Attempting connection...")

        broadcaster.broadcastMessage(
            message = { getString(Res.string.room_attempting_connect,
                if (viewmodel.session.serverHost == "151.80.32.178") "syncplay.pl" else viewmodel.session.serverHost,
                viewmodel.session.serverPort.toString()
            )},
            isChat = false
        )
    }

    fun onConnectionFailed() {
        loggy("SYNCPLAY Protocol: Connection failed :/")

        viewmodel.networkManager.state = CONNECTIONSTATE.STATE_DISCONNECTED
        broadcaster.broadcastMessage(message = { getString(Res.string.room_connection_failed) }, isChat = false, isError = true)
        viewmodel.networkManager.reconnect()
    }

    fun onDisconnected() {
        loggy("SYNCPLAY Protocol: Disconnected.")

        viewmodel.networkManager.state = CONNECTIONSTATE.STATE_DISCONNECTED
        broadcaster.broadcastMessage(message = { getString(Res.string.room_attempting_reconnection) }, isChat = false, isError = true)
        viewmodel.networkManager.reconnect()
    }

    fun onTLSCheck() {
        loggy("SYNCPLAY Protocol: Checking TLS...")

        broadcaster.broadcastMessage(message = { getString(Res.string.room_attempting_tls) }, isChat = false)
    }

    suspend fun onReceivedTLS(supported: Boolean) {
        loggy("SYNCPLAY Protocol: Received TLS...")

        if (supported) {
            broadcaster.broadcastMessage(message = { getString(Res.string.room_tls_supported) }, isChat = false)
            viewmodel.networkManager.tls = TlsState.TLS_YES
            viewmodel.networkManager.upgradeTls()
        } else {
            broadcaster.broadcastMessage(message = { getString(Res.string.room_tls_not_supported) }, isChat = false, isError = true)
            viewmodel.networkManager.tls = TlsState.TLS_NO
        }

        viewmodel.networkManager.send<ClientMessage.Hello> {
            username = viewmodel.session.currentUsername
            roomname = viewmodel.session.currentRoom
            serverPassword = viewmodel.session.currentPassword
        }
    }

    // TODO: Copy +room:password to clipboard
    fun onNewControlledRoom(data: Set.NewControlledRoom) {
        viewmodel.session.currentRoom = data.roomName
        viewmodel.session.currentOperatorPassword = data.password

        broadcaster.broadcastMessage(
            message = { getString(Res.string.room_on_newcontrolledroom, data.roomName, data.password) },
            isChat = false
        )
    }

    fun onHandleControllerAuth(data: ControllerAuthResponse) {
        val user = data.user ?: viewmodel.session.currentUsername

        viewmodel.networkManager.sendAsync<ClientMessage.EmptyList>()

        broadcaster.broadcastMessage(
            message = { getString(when (data.success) {
                true -> Res.string.room_on_controller_auth_success
                false -> Res.string.room_on_controller_auth_failed
            }, user) },
            isChat = false,
            isError = !data.success
        )
    }
}