package com.yuroyami.syncplay.managers

import androidx.lifecycle.viewModelScope
import com.yuroyami.syncplay.AbstractManager
import com.yuroyami.syncplay.RoomViewmodel
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.PREF_PAUSE_ON_SOMEONE_LEAVE
import com.yuroyami.syncplay.managers.datastore.valueBlockingly
import com.yuroyami.syncplay.managers.protocol.PacketCreator
import com.yuroyami.syncplay.managers.protocol.ProtocolCallback
import com.yuroyami.syncplay.models.Constants
import com.yuroyami.syncplay.utils.loggy
import com.yuroyami.syncplay.utils.timeStamper
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
import syncplaymobile.shared.generated.resources.room_rewinded
import syncplaymobile.shared.generated.resources.room_seeked
import syncplaymobile.shared.generated.resources.room_shared_playlist_changed
import syncplaymobile.shared.generated.resources.room_shared_playlist_updated
import syncplaymobile.shared.generated.resources.room_tls_not_supported
import syncplaymobile.shared.generated.resources.room_tls_supported
import syncplaymobile.shared.generated.resources.room_you_joined_room

class OnRoomEventManager(val viewmodel: RoomViewmodel) : AbstractManager(viewmodel), ProtocolCallback {

    override fun onSomeonePaused(pauser: String) {
        loggy("SYNCPLAY Protocol: Someone ($pauser) paused.")

        if (pauser != viewmodel.session.currentUsername) {
            viewmodel.actionManager.pausePlayback()
        }

        viewmodel.actionManager.broadcastMessage(
            message = { getString(Res.string.room_guy_paused, pauser, timeStamper(viewmodel.protocolManager.globalPositionMs)) },
            isChat = false
        )
    }

    override fun onSomeonePlayed(player: String) {
        loggy("SYNCPLAY Protocol: Someone ($player) unpaused.")

        if (player != viewmodel.session.currentUsername) {
            viewmodel.actionManager.playPlayback()
        }

        viewmodel.actionManager.broadcastMessage(message = { getString(Res.string.room_guy_played, player) }, isChat = false)
    }

    override fun onChatReceived(chatter: String, chatmessage: String) {
        loggy("SYNCPLAY Protocol: $chatter sent: $chatmessage")

        viewmodel.actionManager.broadcastMessage(message = { chatmessage }, isChat = true, chatter = chatter)
    }

    override fun onSomeoneJoined(joiner: String) {
        loggy("SYNCPLAY Protocol: $joiner joined the room.")

        viewmodel.actionManager.broadcastMessage(message = { getString(Res.string.room_guy_joined, joiner) }, isChat = false)
    }

    override fun onSomeoneLeft(leaver: String) {
        loggy("SYNCPLAY Protocol: $leaver left the room.")

        viewmodel.actionManager.broadcastMessage(message = { getString(Res.string.room_guy_left,leaver) }, isChat = false)

        /* If the setting is enabled, pause playback **/
        viewmodel.viewModelScope.launch(Dispatchers.Main) {
            if (viewmodel.player?.hasMedia() == true) {
                val pauseOnLeft = valueBlockingly(PREF_PAUSE_ON_SOMEONE_LEAVE, true)
                if (pauseOnLeft) {
                    viewmodel.actionManager.pausePlayback()
                }
            }
        }

        /* Rare cases where a user can see their own self disconnected */
        if (leaver == viewmodel.session.currentUsername) {
            onDisconnected()
        }
    }

    override fun onSomeoneSeeked(seeker: String, toPosition: Double) {
        loggy("SYNCPLAY Protocol: $seeker seeked to: $toPosition")

        val oldPosMs = viewmodel.protocolManager.globalPositionMs.toLong()
        val newPosMs = toPosition.toLong() * 1000L

        /* Saving seek so it can be undone on mistake */
        viewmodel.seeks.add(Pair(oldPosMs, newPosMs))

        if (seeker != viewmodel.session.currentUsername) {
            onMainThread {
                viewmodel.player?.seekTo(newPosMs)
            }
        }

        viewmodel.actionManager.broadcastMessage(message = { getString(Res.string.room_seeked,seeker, timeStamper(oldPosMs), timeStamper(newPosMs)) }, isChat = false)
    }

    override fun onSomeoneBehind(behinder: String, toPosition: Double) {
        loggy("SYNCPLAY Protocol: $behinder is behind. Rewinding to $toPosition")

        onMainThread {
            viewmodel.player?.seekTo((toPosition * 1000L).toLong())
        }

        viewmodel.actionManager.broadcastMessage(message = { getString(Res.string.room_rewinded,behinder) }, isChat = false)
    }

   override fun onReceivedList() {
        loggy("SYNCPLAY Protocol: Received list update.")
    }

    override fun onSomeoneLoadedFile(person: String, file: String?, fileduration: Double?) {
        loggy("SYNCPLAY Protocol: $person loaded: $file - Duration: $fileduration")

        viewmodel.actionManager.broadcastMessage(
            message = { getString(Res.string.room_isplayingfile,
                person,
                file ?: "",
                timeStamper(fileduration?.toLong()?.times(1000L) ?: 0)
            ) },
            isChat = false
        )

        viewmodel. checkFileMismatches()
    }

    override fun onPlaylistUpdated(user: String) {
        loggy("SYNCPLAY Protocol: Playlist updated by $user")

        /** Selecting first item on list **/
        if (viewmodel.session.sharedPlaylist.isNotEmpty() && viewmodel.session.spIndex.intValue == -1) {
            //changePlaylistSelection(0)
        }

        /** Telling user that the playlist has been updated/changed **/
        if (user == "") return
        viewmodel.actionManager.broadcastMessage(message = { getString(Res.string.room_shared_playlist_updated,user) }, isChat = false)
    }

   override fun onPlaylistIndexChanged(user: String, index: Int) {
        loggy("SYNCPLAY Protocol: Playlist index changed by $user to $index")

        /** Changing the selection for the user, to load the file at the given index **/
       viewmodel.viewModelScope.launch {
           viewmodel.playlistManager.changePlaylistSelection(index)
        }

        /** Telling user that the playlist selection/index has been changed **/
        if (user == "") return
       viewmodel.actionManager.broadcastMessage(message = { getString(Res.string.room_shared_playlist_changed,user) }, isChat = false)
    }

    override suspend fun onConnected() {
        loggy("SYNCPLAY Protocol: Connected!")

        /** Adjusting connection state */
        viewmodel.networkManager.state = Constants.CONNECTIONSTATE.STATE_CONNECTED

        /** Set as ready first-hand */
        if (viewmodel.media == null) {
            viewmodel.session.ready.value = viewmodel.setReadyDirectly
            viewmodel.networkManager.send<PacketCreator.Readiness> {
                this.isReady = viewmodel.setReadyDirectly
                manuallyInitiated = false
            }
        }


        /** Telling user that they're connected **/
        viewmodel.actionManager.broadcastMessage(message = { getString(Res.string.room_connected_to_server) }, isChat = false)

        /** Telling user which room they joined **/
        viewmodel.actionManager.broadcastMessage(message = { getString(Res.string.room_you_joined_room,viewmodel.session.currentRoom) }, isChat = false)

        /** Resubmit any ongoing file being played **/
        if (viewmodel.media != null) {
            viewmodel.networkManager.send<PacketCreator.File> {
                this@send.media = viewmodel.media
            }
        }

        /** Pass any messages that have been pending due to disconnection, then clear the queue */
        for (m in viewmodel.session.outboundQueue) {
            viewmodel.networkManager.transmitPacket(m)

        }
        viewmodel.session.outboundQueue.clear()
    }

    override fun onConnectionAttempt() {
        loggy("SYNCPLAY Protocol: Attempting connection...")

        /** Telling user that a connection attempt is on **/
        viewmodel.actionManager.broadcastMessage(
            message =
                { getString(Res.string.room_attempting_connect,
                    if (viewmodel.session.serverHost == "151.80.32.178") "syncplay.pl" else viewmodel.session.serverHost,
                    viewmodel.session.serverPort.toString()
                ) },
            isChat = false
        )
    }

    override fun onConnectionFailed() {
        loggy("SYNCPLAY Protocol: Connection failed :/")

        /** Adjusting connection state */
        viewmodel.networkManager.state = Constants.CONNECTIONSTATE.STATE_DISCONNECTED

        /** Telling user that connection has failed **/
        viewmodel.actionManager.broadcastMessage(
            message = { getString(Res.string.room_connection_failed) },
            isChat = false, isError = true
        )

        /** Attempting reconnection **/
        viewmodel.networkManager.reconnect()
    }

   override fun onDisconnected() {
        loggy("SYNCPLAY Protocol: Disconnected.")

        /** Adjusting connection state */
        viewmodel.networkManager.state = Constants.CONNECTIONSTATE.STATE_DISCONNECTED

        /** Telling user that the connection has been lost **/
       viewmodel.actionManager.broadcastMessage(message = { getString(Res.string.room_attempting_reconnection) }, isChat = false, isError = true)

        /** Attempting reconnection **/
        viewmodel.networkManager.reconnect()
    }

    override fun onTLSCheck() {
        loggy("SYNCPLAY Protocol: Checking TLS...")

        /** Telling user that the app is checking whether the chosen server supports TLS **/
        viewmodel.actionManager.broadcastMessage(message = { getString(Res.string.room_attempting_tls) }, isChat = false)
    }

    override suspend fun onReceivedTLS(supported: Boolean) {
        loggy("SYNCPLAY Protocol: Received TLS...")

        /** Deciding next step based on whether the server supports TLS or not **/
        if (supported) {
            viewmodel.actionManager.broadcastMessage(message = { getString(Res.string.room_tls_supported) }, isChat = false)
            viewmodel.networkManager.tls = Constants.TLS.TLS_YES
            viewmodel.networkManager.upgradeTls()
        } else {
            viewmodel.actionManager.broadcastMessage(message = { getString(Res.string.room_tls_not_supported) }, isChat = false, isError = true)
            viewmodel.networkManager.tls = Constants.TLS.TLS_NO
        }

        viewmodel.networkManager.send<PacketCreator.Hello> {
            username = viewmodel.session.currentUsername
            roomname = viewmodel.session.currentRoom
            serverPassword = viewmodel.session.currentPassword
        }
    }
}