package com.yuroyami.syncplay.watchroom

import com.yuroyami.syncplay.MR
import com.yuroyami.syncplay.datastore.DataStoreKeys.DATASTORE_GLOBAL_SETTINGS
import com.yuroyami.syncplay.datastore.DataStoreKeys.PREF_PAUSE_ON_SOMEONE_LEAVE
import com.yuroyami.syncplay.datastore.obtainBoolean
import com.yuroyami.syncplay.models.Constants
import com.yuroyami.syncplay.models.JoinInfo
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.models.TrackChoices
import com.yuroyami.syncplay.player.BasePlayer
import com.yuroyami.syncplay.player.ENGINE
import com.yuroyami.syncplay.protocol.JsonSender
import com.yuroyami.syncplay.protocol.ProtocolCallback
import com.yuroyami.syncplay.protocol.SyncplayProtocol
import com.yuroyami.syncplay.utils.CommonUtils.loggy
import com.yuroyami.syncplay.utils.RoomUtils.broadcastMessage
import com.yuroyami.syncplay.utils.timeStamper
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.runBlocking

lateinit var p: SyncplayProtocol //If it is not initialized, it means we're in Solo Mode
lateinit var engine: ENGINE
var player: BasePlayer? = null
var media: MediaFile? = null

var currentTrackChoices: TrackChoices = TrackChoices()

/** Returns whether we're in Solo Mode, by checking if our protocol is initialized */
fun isSoloMode(): Boolean {
    return !::p.isInitialized
}


fun prepareProtocol(joinInfo: JoinInfo) {
    if (!joinInfo.soloMode) {
        /** Initializing our ViewModel, which is our protocol at the same time **/
        p = SyncplayProtocol()
        p.syncplayCallback = object : ProtocolCallback {
            override fun onSomeonePaused(pauser: String) {
                loggy("SYNCPLAY Protocol: Someone ($pauser) paused.")

                if (pauser != p.session.currentUsername) {
                    //TODO: pausePlayback()
                }
                broadcastMessage(
                    messageComposite = { stringResource(MR.strings.room_guy_paused, pauser, timeStamper(p.currentVideoPosition.toLong())) },
                    isChat = false
                )
            }

            override fun onSomeonePlayed(player: String) {
                loggy("SYNCPLAY Protocol: Someone ($player) unpaused.")

                if (player != p.session.currentUsername) {
                    //TODO: playPlayback()
                }

                broadcastMessage(messageComposite = { stringResource(MR.strings.room_guy_played, player) }, isChat = false)
            }

            override fun onChatReceived(chatter: String, chatmessage: String) {
                loggy("SYNCPLAY Protocol: $chatter sent: $chatmessage")

                broadcastMessage(message = chatmessage, isChat = true, chatter = chatter)
            }

            override fun onSomeoneJoined(joiner: String) {
                loggy("SYNCPLAY Protocol: $joiner joined the room.")

                broadcastMessage(messageComposite = { stringResource(MR.strings.room_guy_joined, joiner) }, isChat = false)
            }

            override fun onSomeoneLeft(leaver: String) {
                loggy("SYNCPLAY Protocol: $leaver left the room.")

                broadcastMessage(messageComposite = { stringResource(MR.strings.room_guy_left, leaver) }, isChat = false)

                /* If the setting is enabled, pause playback **/
                val pauseOnLeft = runBlocking { DATASTORE_GLOBAL_SETTINGS.obtainBoolean(PREF_PAUSE_ON_SOMEONE_LEAVE, true) }
                if (pauseOnLeft) {
                    //TODO: pausePlayback()
                }

                /* Rare cases where a user can see his own self disconnected */
                if (leaver == p.session.currentUsername) {
                    p.syncplayCallback?.onDisconnected()
                }
            }

            override fun onSomeoneSeeked(seeker: String, toPosition: Double) {
                loggy("SYNCPLAY Protocol: $seeker seeked to: $toPosition")

                    val oldPos = p.currentVideoPosition.toLong()
                    val newPos = toPosition.toLong()
                    if (oldPos == newPos) return

                    /* Saving seek so it can be undone on mistake */
                    seeks.add(Pair(oldPos * 1000, newPos * 1000))

                    broadcastMessage(messageComposite = { stringResource(MR.strings.room_seeked, seeker, timeStamper(oldPos), timeStamper(newPos)) }, isChat = false)

                    if (seeker != p.session.currentUsername) {
                        //player?.seekTo((toPosition * 1000.0).toLong())
                    }
            }

            override fun onSomeoneBehind(behinder: String, toPosition: Double) {
                loggy("SYNCPLAY Protocol: $behinder is behind. Rewinding to $toPosition")

                //TODO: player?.seekTo((toPosition * 1000.0).toLong())

                broadcastMessage(messageComposite = { stringResource(MR.strings.room_rewinded, behinder) }, isChat = false)
            }

            override fun onReceivedList() {
                loggy("SYNCPLAY Protocol: Received list update.")

            }

            override fun onSomeoneLoadedFile(person: String, file: String?, fileduration: Double?) {
                loggy("SYNCPLAY Protocol: $person loaded: $file - Duration: $fileduration")

                broadcastMessage(
                    messageComposite = {
                        stringResource(
                            MR.strings.room_isplayingfile,
                            person,
                            file ?: "",
                            timeStamper(fileduration?.toLong() ?: 0)
                        )
                    },
                    isChat = false
                )
            }

            override fun onPlaylistUpdated(user: String) {
                loggy("SYNCPLAY Protocol: Playlist updated by $user")

                /** Selecting first item on list **/
                if (p.session.sharedPlaylist.size != 0 && p.session.sharedPlaylistIndex == -1) {
                    //changePlaylistSelection(0)
                }

                /** Telling user that the playlist has been updated/changed **/
                if (user == "") return
                broadcastMessage(messageComposite = { stringResource(MR.strings.room_shared_playlist_updated, user) }, isChat = false)
            }

            override fun onPlaylistIndexChanged(user: String, index: Int) {
                loggy("SYNCPLAY Protocol: Playlist index changed by $user to $index")

                /** Changing the selection for the user, to load the file at the given index **/
                //changePlaylistSelection(index)

                /** Telling user that the playlist selection/index has been changed **/
                if (user == "") return
                broadcastMessage(messageComposite = { stringResource(MR.strings.room_shared_playlist_changed, user) }, isChat = false)
            }

            override fun onConnected() {
                loggy("SYNCPLAY Protocol: Connected!")

                /** Adjusting connection state */
                p.state = Constants.CONNECTIONSTATE.STATE_CONNECTED

                /** Dismissing the 'Disconnected' popup since it's irrelevant at this point **/
                /* lifecycleScope.launch(Dispatchers.Main) {
                    disconnectedPopup.dismiss() /* Dismiss any disconnection popup, if they exist */
                } */

                /** Set as ready first-hand */
                if (media == null) {
                    p.sendPacket(JsonSender.sendReadiness(setReadyDirectly, true))
                }

                /** Telling user that they're connected **/
                broadcastMessage(messageComposite = { stringResource(MR.strings.room_connected_to_server) }, isChat = false)

                /** Telling user which room they joined **/
                broadcastMessage(messageComposite = { stringResource(MR.strings.room_you_joined_room, p.session.currentRoom) }, isChat = false)

                /** Resubmit any ongoing file being played **/
                if (media != null) {
                    p.sendPacket(JsonSender.sendFile(media!!))
                }

                /** Pass any messages that have been pending due to disconnection, then clear the queue */
                for (m in p.session.outboundQueue) {
                    p.sendPacket(m)
                }
                p.session.outboundQueue.clear()
            }

            override fun onConnectionAttempt() {
                loggy("SYNCPLAY Protocol: Attempting connection...")

                /** Telling user that a connection attempt is on **/
                broadcastMessage(
                    messageComposite = {
                        stringResource(
                            MR.strings.room_attempting_connect,
                            if (p.session.serverHost == "151.80.32.178") "syncplay.pl" else p.session.serverHost,
                            p.session.serverPort.toString()
                        )
                    },
                    isChat = false
                )
            }

            override fun onConnectionFailed() {
                loggy("SYNCPLAY Protocol: Connection failed :/")

                /** Adjusting connection state */
                p.state = Constants.CONNECTIONSTATE.STATE_DISCONNECTED

                /** Telling user that connection has failed **/
                broadcastMessage(
                    messageComposite = { stringResource(MR.strings.room_connection_failed) },
                    isChat = false
                )

                /** Attempting reconnection **/
                p.reconnect()
            }

            override fun onDisconnected() {
                loggy("SYNCPLAY Protocol: Disconnected.")


                /** Adjusting connection state */
                p.state = Constants.CONNECTIONSTATE.STATE_DISCONNECTED

                /** Telling user that the connection has been lost **/
                broadcastMessage(messageComposite = { stringResource(MR.strings.room_attempting_reconnection) }, isChat = false)

                /** Showing a popup that informs the user about their DISCONNECTED state **/
                //TODO: showPopup(disconnectedPopup, true)

                /** Attempting reconnection **/
                p.reconnect()
            }

            override fun onTLSCheck() {
                loggy("SYNCPLAY Protocol: Checking TLS...")

                /** Telling user that the app is checking whether the chosen server supports TLS **/
                broadcastMessage("Checking whether server supports TLS", isChat = false)
            }

            override fun onReceivedTLS(supported: Boolean) {
                loggy("SYNCPLAY Protocol: Received TLS...")

                /** Deciding next step based on whether the server supports TLS or not **/
                if (supported) {
                    //p.cert = resources.openRawResource(R.raw.cert)
                    broadcastMessage("Server supports TLS !", isChat = false)
                    p.tls = Constants.TLS.TLS_YES
                    p.connect()
                } else {
                    broadcastMessage("Server does not support TLS.", isChat = false)
                    p.tls = Constants.TLS.TLS_NO
                    p.sendPacket(
                        JsonSender.sendHello(
                            p.session.currentUsername,
                            p.session.currentRoom,
                            p.session.currentPassword
                        )
                    )
                }
            }

        }

        /** Getting information from joining info argument **/
        p.session.serverHost = joinInfo.address
        p.session.serverPort = joinInfo.port
        p.session.currentUsername = joinInfo.username
        p.session.currentRoom = joinInfo.roomname
        p.session.currentPassword = joinInfo.password

        /** Connecting */
        p.connect()
    }
}
