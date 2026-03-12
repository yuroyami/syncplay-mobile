package app.protocol.event

import androidx.lifecycle.viewModelScope
import app.AbstractManager
import app.player.Playback
import app.preferences.Preferences.PAUSE_ON_SOMEONE_LEAVE
import app.preferences.Preferences.READY_FIRST_HAND
import app.preferences.value
import app.protocol.models.ConnectionState
import app.protocol.models.TlsState
import app.protocol.server.Set
import app.protocol.server.Set.ControllerAuthResponse
import app.room.RoomViewmodel
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

    fun onSomeonePaused(pauser: String) {
        loggy("SYNCPLAY Protocol: Someone ($pauser) paused.")

        if (pauser.isNotSelf()) dispatcher.controlPlayback(Playback.PAUSE, false)

        dispatcher.broadcastMessage(
            message = {
                getString(
                    resource = Res.string.room_guy_paused,
                    pauser, timestampFromMillis(protocol.globalPositionMs)
                )
            },
            isChat = false
        )
    }

    fun onSomeonePlayed(player: String) {
        loggy("SYNCPLAY Protocol: Someone ($player) unpaused.")

        if (player.isNotSelf()) {
            dispatcher.controlPlayback(Playback.PLAY, false)
        }

        dispatcher.broadcastMessage(message = { getString(Res.string.room_guy_played, player) }, isChat = false)
    }

    fun onChatReceived(chatter: String, chatmessage: String) {
        loggy("SYNCPLAY Protocol: $chatter sent: $chatmessage")

        dispatcher.broadcastMessage(message = { chatmessage }, isChat = true, chatter = chatter)
    }

    fun onSomeoneJoined(joiner: String) {
        loggy("SYNCPLAY Protocol: $joiner joined the room.")

        dispatcher.broadcastMessage(message = { getString(Res.string.room_guy_joined, joiner) }, isChat = false)
    }

    fun onSomeoneLeft(leaver: String) {
        if (viewmodel.protocol.isRoomChanging) {
            viewmodel.protocol.isRoomChanging = false
            return
        }

        loggy("SYNCPLAY Protocol: $leaver left the room.")

        dispatcher.broadcastMessage(message = { getString(Res.string.room_guy_left, leaver) }, isChat = false)

        viewmodel.viewModelScope.launch(Dispatchers.Main) {
            if (viewmodel.player.hasMedia() && PAUSE_ON_SOMEONE_LEAVE.value()) {
                this@RoomCallback.dispatcher.controlPlayback(Playback.PAUSE, true)
            }
        }

        if (leaver.isSelf()) onDisconnected()
    }

    fun onSomeoneSeeked(seeker: String, toPosition: Double) {
        loggy("SYNCPLAY Protocol: $seeker seeked to: $toPosition")

        onMainThread {
            val oldPosMs = if (seeker.isSelf()) dispatcher.pendingSeekFromMs else viewmodel.player.currentPositionMs()
            val newPosMs = toPosition.toLong() * 1000L

            if (seeker.isNotSelf()) viewmodel.player.seekTo(newPosMs)

            dispatcher.broadcastMessage(
                message = { getString(Res.string.room_seeked, seeker, timestampFromMillis(oldPosMs), timestampFromMillis(newPosMs)) },
                isChat = false
            )

            viewmodel.seeks.add(Pair(oldPosMs, newPosMs))

        }
    }

    fun onSomeoneBehind(behinder: String, toPosition: Double) {
        loggy("SYNCPLAY Protocol: $behinder is behind. Rewinding to $toPosition")

        if (behinder.isNotSelf()) {
            onMainThread {
                viewmodel.player.seekTo((toPosition * 1000L).toLong())
                dispatcher.broadcastMessage(message = { getString(Res.string.room_rewinded, behinder) }, isChat = false)
            }
        }
    }

    fun onReceivedList() {
        loggy("SYNCPLAY Protocol: Received list update.")
    }

    fun onSomeoneLoadedFile(person: String, file: String?, fileduration: Double?) {
        loggy("SYNCPLAY Protocol: $person loaded: $file - Duration: $fileduration")

        dispatcher.broadcastMessage(
            message = { getString(Res.string.room_isplayingfile, person, file ?: "", timestampFromMillis(fileduration?.toLong()?.times(1000L) ?: 0)) },
            isChat = false
        )

        if (person.isNotSelf()) {
            viewmodel.checkFileMismatches()
        }
    }

    fun onPlaylistUpdated(user: String) {
        loggy("SYNCPLAY Protocol: Playlist updated by $user")

        if (viewmodel.session.sharedPlaylist.isNotEmpty() && viewmodel.session.spIndex.intValue == -1) {
            //changePlaylistSelection(0)
        }

        if (user == "") return
        dispatcher.broadcastMessage(message = { getString(Res.string.room_shared_playlist_updated, user) }, isChat = false)
    }

    fun onPlaylistIndexChanged(user: String, index: Int) {
        loggy("SYNCPLAY Protocol: Playlist index changed by $user to $index")

        viewmodel.viewModelScope.launch {
            if (user.isNotSelf()) {
                viewmodel.playlistManager.changePlaylistSelection(index)
            }
        }

        if (user == "") return
        dispatcher.broadcastMessage(message = { getString(Res.string.room_shared_playlist_changed, user) }, isChat = false)
    }

    suspend fun onConnected() {
        loggy("SYNCPLAY Protocol: Connected!")

        network.state.value = ConnectionState.CONNECTED

        network.sendAsync<ClientMessage.Readiness> {
            isReady = if (viewmodel.media == null && READY_FIRST_HAND.value()) true else session.ready.value
            manuallyInitiated = false
        }

        dispatcher.broadcastMessage(message = { getString(Res.string.room_connected_to_server) }, isChat = false)
        dispatcher.broadcastMessage(message = { getString(Res.string.room_you_joined_room, session.currentRoom) }, isChat = false)

        if (viewmodel.media != null) {
            network.sendAsync<ClientMessage.File> { this@sendAsync.media = viewmodel.media }
        }

        for (m in session.outboundQueue) network.transmitPacket(m)
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

        network.state.value = ConnectionState.DISCONNECTED
        dispatcher.broadcastMessage(message = { getString(Res.string.room_connection_failed) }, isChat = false, isError = true)
        network.reconnect()
    }

    fun onDisconnected() {
        loggy("SYNCPLAY Protocol: Disconnected.")

        network.state.value = ConnectionState.DISCONNECTED
        dispatcher.broadcastMessage(message = { getString(Res.string.room_attempting_reconnection) }, isChat = false, isError = true)
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
    fun onNewControlledRoom(data: Set.NewControlledRoom) {
        session.currentRoom = data.roomName
        session.currentOperatorPassword = data.password

        dispatcher.broadcastMessage(
            message = { getString(Res.string.room_on_newcontrolledroom, data.roomName, data.password) },
            isChat = false
        )
    }

    fun onHandleControllerAuth(data: ControllerAuthResponse) {
        val user = data.user ?: session.currentUsername

        network.sendAsync<ClientMessage.EmptyList>()

        dispatcher.broadcastMessage(
            message = {
                getString(
                    when (data.success) {
                        true -> Res.string.room_on_controller_auth_success
                        false -> Res.string.room_on_controller_auth_failed
                    }, user
                )
            },
            isChat = false,
            isError = !data.success
        )
    }
}