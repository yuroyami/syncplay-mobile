package com.yuroyami.syncplay.managers

import androidx.lifecycle.viewModelScope
import com.yuroyami.syncplay.AbstractManager
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.PREF_PAUSE_ON_SOMEONE_LEAVE
import com.yuroyami.syncplay.managers.datastore.DatastoreManager.Companion.value
import com.yuroyami.syncplay.managers.protocol.ProtocolCallback
import com.yuroyami.syncplay.managers.protocol.creator.PacketOut
import com.yuroyami.syncplay.managers.protocol.handler.Set
import com.yuroyami.syncplay.managers.protocol.handler.Set.ControllerAuthResponse
import com.yuroyami.syncplay.models.Constants
import com.yuroyami.syncplay.utils.loggy
import com.yuroyami.syncplay.utils.timeStamper
import com.yuroyami.syncplay.viewmodels.RoomViewmodel
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
 * Manages callbacks from the Syncplay protocol - handles incoming events from the server.
 *
 * This is the "receiving actions" counterpart to [RoomActionManager] which handles "sending actions".
 * Implements [ProtocolCallback] to process all protocol events such as playback changes, user
 * activity, connection state, and playlist updates.
 *
 * Each callback typically performs two actions:
 * 1. Updates local state or triggers local playback changes
 * 2. Broadcasts a user-facing message about the event
 *
 * @property viewmodel The parent RoomViewModel that owns this manager
 */
class OnRoomEventManager(val viewmodel: RoomViewmodel) : AbstractManager(viewmodel), ProtocolCallback {

    /**
     * Reference to the action manager for broadcasting messages and controlling local playback.
     */
    val broadcaster = viewmodel.actionManager

    /**
     * Called when another user pauses playback.
     *
     * Pauses local playback if the pauser is not the current user, and broadcasts
     * a message showing who paused and at what timestamp.
     *
     * @param pauser Username of the user who paused
     */
    override fun onSomeonePaused(pauser: String) {
        loggy("SYNCPLAY Protocol: Someone ($pauser) paused.")

        if (pauser != viewmodel.session.currentUsername) {
            broadcaster.pausePlayback()
        }

        broadcaster.broadcastMessage(
            message = { getString(Res.string.room_guy_paused, pauser, timeStamper(viewmodel.protocolManager.globalPositionMs)) },
            isChat = false
        )
    }

    /**
     * Called when another user resumes playback.
     *
     * Resumes local playback if the player is not the current user, and broadcasts
     * a message showing who resumed.
     *
     * @param player Username of the user who resumed playback
     */
    override fun onSomeonePlayed(player: String) {
        loggy("SYNCPLAY Protocol: Someone ($player) unpaused.")

        if (player != viewmodel.session.currentUsername) {
            broadcaster.playPlayback()
        }

        broadcaster.broadcastMessage(message = { getString(Res.string.room_guy_played, player) }, isChat = false)
    }

    /**
     * Called when a chat message is received from another user.
     *
     * Displays the message in the chat interface.
     *
     * @param chatter Username of the message sender
     * @param chatmessage The message content
     */
    override fun onChatReceived(chatter: String, chatmessage: String) {
        loggy("SYNCPLAY Protocol: $chatter sent: $chatmessage")

        broadcaster.broadcastMessage(message = { chatmessage }, isChat = true, chatter = chatter)
    }

    /**
     * Called when a user joins the room.
     *
     * Broadcasts a notification message about the new user.
     *
     * @param joiner Username of the user who joined
     */
    override fun onSomeoneJoined(joiner: String) {
        loggy("SYNCPLAY Protocol: $joiner joined the room.")

        broadcaster.broadcastMessage(message = { getString(Res.string.room_guy_joined, joiner) }, isChat = false)
    }

    /**
     * Called when a user leaves the room.
     *
     * Broadcasts a notification message and optionally pauses playback based on user preferences.
     * Handles special cases like room switching and self-disconnection.
     *
     * @param leaver Username of the user who left
     */
    override fun onSomeoneLeft(leaver: String) {
        if (viewmodel.protocolManager.isRoomChanging) {
            //This occurs when we are switching to a managed room
            //and the server would still be sending messages about the old room
            viewmodel.protocolManager.isRoomChanging = false
            return
        }

        loggy("SYNCPLAY Protocol: $leaver left the room.")

        broadcaster.broadcastMessage(message = { getString(Res.string.room_guy_left,leaver) }, isChat = false)

        /* If the setting is enabled, pause playback **/
        viewmodel.viewModelScope.launch(Dispatchers.Main) {
            if (viewmodel.player.hasMedia()) {
                val pauseOnLeft = value(PREF_PAUSE_ON_SOMEONE_LEAVE, true)
                if (pauseOnLeft) {
                    broadcaster.pausePlayback()
                }
            }
        }

        /* Rare cases where a user can see their own self disconnected */
        if (leaver == viewmodel.session.currentUsername) {
            onDisconnected()
        }
    }

    /**
     * Called when another user seeks to a different position.
     *
     * Performs the local seek operation (unless it's our own seek) and broadcasts
     * a message showing the old and new positions. Records the seek for potential undo.
     *
     * @param seeker Username of the user who seeked
     * @param toPosition The new position in seconds
     */
    override fun onSomeoneSeeked(seeker: String, toPosition: Double) {
        if (seeker == viewmodel.session.currentUsername) return

        loggy("SYNCPLAY Protocol: $seeker seeked to: $toPosition")

        //val oldPosMs = viewmodel.protocolManager.globalPositionMs.toLong()
        onMainThread {
            val oldPosMs = viewmodel.player.currentPositionMs()
            val newPosMs = toPosition.toLong() * 1000L

            /* Saving seek so it can be undone on mistake */
            viewmodel.seeks.add(Pair(oldPosMs, newPosMs))

            if (seeker != viewmodel.session.currentUsername) {
                viewmodel.player.seekTo(newPosMs)
            }

            broadcaster.broadcastMessage(message = { getString(Res.string.room_seeked, seeker, timeStamper(oldPosMs), timeStamper(newPosMs)) }, isChat = false)
        }
    }

    /**
     * Called when the server detects a user is behind and rewinds playback to sync.
     *
     * Seeks to the specified position to keep users synchronized.
     *
     * @param behinder Username of the user who was behind
     * @param toPosition The position in seconds to rewind to
     */
    override fun onSomeoneBehind(behinder: String, toPosition: Double) {
        loggy("SYNCPLAY Protocol: $behinder is behind. Rewinding to $toPosition")

        onMainThread {
            viewmodel.player.seekTo((toPosition * 1000L).toLong())
        }

        broadcaster.broadcastMessage(message = { getString(Res.string.room_rewinded,behinder) }, isChat = false)
    }

    /**
     * Called when the server sends an updated user list.
     *
     * Currently only logs the event. User list is updated elsewhere in the protocol handler.
     */
    override fun onReceivedList() {
        loggy("SYNCPLAY Protocol: Received list update.")
    }

    /**
     * Called when a user loads a new media file.
     *
     * Broadcasts which file was loaded and its duration, then checks for file mismatches
     * between users.
     *
     * @param person Username of the user who loaded the file
     * @param file Filename or path, if available
     * @param fileduration File duration in seconds, if available
     */
    override fun onSomeoneLoadedFile(person: String, file: String?, fileduration: Double?) {
        loggy("SYNCPLAY Protocol: $person loaded: $file - Duration: $fileduration")

        broadcaster.broadcastMessage(
            message = { getString(Res.string.room_isplayingfile,
                person,
                file ?: "",
                timeStamper(fileduration?.toLong()?.times(1000L) ?: 0)
            ) },
            isChat = false
        )

        viewmodel. checkFileMismatches()
    }

    /**
     * Called when the shared playlist is updated (files added/removed).
     *
     * Automatically selects the first item if the playlist was previously empty.
     * Broadcasts a notification about who updated the playlist.
     *
     * @param user Username of the user who updated the playlist (empty string for server updates)
     */
    override fun onPlaylistUpdated(user: String) {
        loggy("SYNCPLAY Protocol: Playlist updated by $user")

        /** Selecting first item on list **/
        if (viewmodel.session.sharedPlaylist.isNotEmpty() && viewmodel.session.spIndex.intValue == -1) {
            //changePlaylistSelection(0)
        }

        /** Telling user that the playlist has been updated/changed **/
        if (user == "") return
        broadcaster.broadcastMessage(message = { getString(Res.string.room_shared_playlist_updated,user) }, isChat = false)
    }

    /**
     * Called when the shared playlist's selected index changes.
     *
     * Changes the local playlist selection to match and loads the file at that index.
     * Broadcasts a notification about who changed the selection.
     *
     * @param user Username of the user who changed the index (empty string for server changes)
     * @param index The new playlist index (0-based)
     */
    override fun onPlaylistIndexChanged(user: String, index: Int) {
        loggy("SYNCPLAY Protocol: Playlist index changed by $user to $index")

        /** Changing the selection for the user, to load the file at the given index **/
        viewmodel.viewModelScope.launch {
            viewmodel.playlistManager.changePlaylistSelection(index)
        }

        /** Telling user that the playlist selection/index has been changed **/
        if (user == "") return
        broadcaster.broadcastMessage(message = { getString(Res.string.room_shared_playlist_changed,user) }, isChat = false)
    }

    /**
     * Called when successfully connected to the Syncplay server.
     *
     * Updates connection state, sets ready status, broadcasts connection messages,
     * resubmits current media file, and processes any pending outbound messages.
     */
    override suspend fun onConnected() {
        loggy("SYNCPLAY Protocol: Connected!")

        /** Adjusting connection state */
        viewmodel.networkManager.state = Constants.CONNECTIONSTATE.STATE_CONNECTED

        /** Set as ready first-hand */
        if (viewmodel.media == null) {
            viewmodel.session.ready.value = viewmodel.setReadyDirectly
            viewmodel.networkManager.send<PacketOut.Readiness> {
                this.isReady = viewmodel.setReadyDirectly
                manuallyInitiated = false
            }
        }


        /** Telling user that they're connected **/
        broadcaster.broadcastMessage(message = { getString(Res.string.room_connected_to_server) }, isChat = false)

        /** Telling user which room they joined **/
        broadcaster.broadcastMessage(message = { getString(Res.string.room_you_joined_room,viewmodel.session.currentRoom) }, isChat = false)

        /** Resubmit any ongoing file being played **/
        if (viewmodel.media != null) {
            viewmodel.networkManager.send<PacketOut.File> {
                this@send.media = viewmodel.media
            }
        }

        /** Pass any messages that have been pending due to disconnection, then clear the queue */
        for (m in viewmodel.session.outboundQueue) {
            viewmodel.networkManager.transmitPacket(m)

        }
        viewmodel.session.outboundQueue.clear()
    }

    /**
     * Called when beginning a connection attempt to the server.
     *
     * Broadcasts a message showing which server and port is being connected to.
     */
    override fun onConnectionAttempt() {
        loggy("SYNCPLAY Protocol: Attempting connection...")

        /** Telling user that a connection attempt is on **/
        broadcaster.broadcastMessage(
            message =
                { getString(Res.string.room_attempting_connect,
                    if (viewmodel.session.serverHost == "151.80.32.178") "syncplay.pl" else viewmodel.session.serverHost,
                    viewmodel.session.serverPort.toString()
                ) },
            isChat = false
        )
    }

    /**
     * Called when a connection attempt fails.
     *
     * Updates connection state, broadcasts an error message, and initiates reconnection.
     */
    override fun onConnectionFailed() {
        loggy("SYNCPLAY Protocol: Connection failed :/")

        /** Adjusting connection state */
        viewmodel.networkManager.state = Constants.CONNECTIONSTATE.STATE_DISCONNECTED

        /** Telling user that connection has failed **/
        broadcaster.broadcastMessage(
            message = { getString(Res.string.room_connection_failed) },
            isChat = false, isError = true
        )

        /** Attempting reconnection **/
        viewmodel.networkManager.reconnect()
    }

    /**
     * Called when disconnected from the server after being connected.
     *
     * Updates connection state, broadcasts a reconnection message, and attempts to reconnect.
     */
    override fun onDisconnected() {
        loggy("SYNCPLAY Protocol: Disconnected.")

        /** Adjusting connection state */
        viewmodel.networkManager.state = Constants.CONNECTIONSTATE.STATE_DISCONNECTED

        /** Telling user that the connection has been lost **/
        broadcaster.broadcastMessage(message = { getString(Res.string.room_attempting_reconnection) }, isChat = false, isError = true)

        /** Attempting reconnection **/
        viewmodel.networkManager.reconnect()
    }

    /**
     * Called when checking if the server supports TLS encryption.
     *
     * Broadcasts a message that TLS support is being checked.
     */
    override fun onTLSCheck() {
        loggy("SYNCPLAY Protocol: Checking TLS...")

        /** Telling user that the app is checking whether the chosen server supports TLS **/
        broadcaster.broadcastMessage(message = { getString(Res.string.room_attempting_tls) }, isChat = false)
    }

    /**
     * Called when the server responds to the TLS support check.
     *
     * If supported, upgrades the connection to TLS. Either way, proceeds with the Hello handshake.
     *
     * @param supported Whether the server supports TLS encryption
     */
    override suspend fun onReceivedTLS(supported: Boolean) {
        loggy("SYNCPLAY Protocol: Received TLS...")

        /** Deciding next step based on whether the server supports TLS or not **/
        if (supported) {
            broadcaster.broadcastMessage(message = { getString(Res.string.room_tls_supported) }, isChat = false)
            viewmodel.networkManager.tls = Constants.TLS.TLS_YES
            viewmodel.networkManager.upgradeTls()
        } else {
            broadcaster.broadcastMessage(message = { getString(Res.string.room_tls_not_supported) }, isChat = false, isError = true)
            viewmodel.networkManager.tls = Constants.TLS.TLS_NO
        }

        viewmodel.networkManager.send<PacketOut.Hello> {
            username = viewmodel.session.currentUsername
            roomname = viewmodel.session.currentRoom
            serverPassword = viewmodel.session.currentPassword
        }
    }

    /**
     * Called when a new managed (controlled) room is created.
     *
     * Updates the session with the new room name and operator password, and broadcasts
     * the information to the user.
     *
     * TODO: Copy +room:password to clipboard!
     *
     * @param data Information about the newly created controlled room
     */
    override fun onNewControlledRoom(data: Set.NewControlledRoom) {
        //TODO: Copy +room:password to clipboard!
        viewmodel.session.currentRoom = data.roomName
        viewmodel.session.currentOperatorPassword = data.password

        broadcaster.broadcastMessage(
            message = {
                getString(Res.string.room_on_newcontrolledroom, data.roomName, data.password)
            }, isChat = false
        )
    }

    /**
     * Called when a user attempts to authenticate as a room operator (controller).
     *
     * Broadcasts whether authentication succeeded or failed
     */
    override fun onHandleControllerAuth(data: ControllerAuthResponse) {
        val user = data.user ?: viewmodel.session.currentUsername

        viewmodel.networkManager.sendAsync<PacketOut.EmptyList>()

        broadcaster.broadcastMessage(
            message = {
                getString(
                    when (data.success) {
                        true -> Res.string.room_on_controller_auth_success
                        false -> Res.string.room_on_controller_auth_failed
                    },
                    user
                )
            },
            isChat = false,
            isError = !data.success
        )
    }
}