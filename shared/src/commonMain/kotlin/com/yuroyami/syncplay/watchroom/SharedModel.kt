package com.yuroyami.syncplay.watchroom

import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import cafe.adriel.lyricist.Lyricist
import com.yuroyami.syncplay.home.HomeCallback
import com.yuroyami.syncplay.lyricist.Stringies
import com.yuroyami.syncplay.lyricist.Strings
import com.yuroyami.syncplay.models.Constants
import com.yuroyami.syncplay.models.JoinInfo
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.models.TrackChoices
import com.yuroyami.syncplay.player.BasePlayer
import com.yuroyami.syncplay.player.PlayerUtils.pausePlayback
import com.yuroyami.syncplay.player.PlayerUtils.playPlayback
import com.yuroyami.syncplay.protocol.JsonSender
import com.yuroyami.syncplay.protocol.ProtocolCallback
import com.yuroyami.syncplay.protocol.SyncplayProtocol
import com.yuroyami.syncplay.settings.DataStoreKeys
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_PAUSE_ON_SOMEONE_LEAVE
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_TLS_ENABLE
import com.yuroyami.syncplay.settings.valueBlockingly
import com.yuroyami.syncplay.ui.LifecycleWatchdog
import com.yuroyami.syncplay.utils.PlaylistUtils
import com.yuroyami.syncplay.utils.RoomUtils.broadcastMessage
import com.yuroyami.syncplay.utils.loggy
import com.yuroyami.syncplay.utils.timeStamper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

var lyricist: Lyricist<Strings>  = Lyricist("en", Stringies)

var homeCallback: HomeCallback? = null
var viewmodel: SpViewModel? = null

class SpViewModel {
    lateinit var p: SyncplayProtocol //If it is not initialized, it means we're in Solo Mode

    val viewmodelScope = CoroutineScope(Dispatchers.Default)

    var roomCallback: RoomCallback? = null

    var player: BasePlayer? = null
    var media: MediaFile? = null

    var wentForFilePick = false

    var setReadyDirectly = false
    val seeks = mutableListOf<Pair<Long, Long>>()

    var startupSlide = false

    /* Related to playback status */
    val isNowPlaying = mutableStateOf(false)
    val timeFull = mutableLongStateOf(0L)
    val timeCurrent = mutableLongStateOf(0L)

    val hasVideoG = mutableStateOf(false)
    val hudVisibilityState = mutableStateOf(true)
    val pipMode = mutableStateOf(false)

    var currentTrackChoices: TrackChoices = TrackChoices()

    var pingUpdateJob: Job? = null
    var playerTrackerJob: Job? = null

    internal val isSoloMode: Boolean
        get() = !::p.isInitialized

    //TODO Lifecycle Stuff

    var background = false
    var lifecycleWatchdog = object: LifecycleWatchdog {

        override fun onResume() {
            background = false
        }

        override fun onStop() {
            if (!pipMode.value) {
                background = true
                viewmodel?.player?.pause()
            }
        }

        override fun onCreate() {}

        override fun onStart() {
            background = false
        }

        override fun onPause() {}
    }
}

/** Returns whether we're in Solo Mode, by checking if our protocol is initialized */
val isSoloMode: Boolean
    get() = viewmodel?.isSoloMode == true

fun prepareProtocol(joinInfo: JoinInfo) {
    if (!joinInfo.soloMode) {

        viewmodel?.setReadyDirectly = valueBlockingly(DataStoreKeys.PREF_READY_FIRST_HAND, true)

        with (viewmodel!!) {
            p.syncplayCallback = object : ProtocolCallback {
                override fun onSomeonePaused(pauser: String) {
                    loggy("SYNCPLAY Protocol: Someone ($pauser) paused.", 1001)

                    if (pauser != p.session.currentUsername) {
                        pausePlayback()
                    }
                    broadcastMessage(
                        message = lyricist.strings.roomGuyPaused.invoke(pauser, timeStamper(p.currentVideoPosition.toLong())),
                        isChat = false
                    )
                }

                override fun onSomeonePlayed(player: String) {
                    loggy("SYNCPLAY Protocol: Someone ($player) unpaused.", 1002)

                    if (player != p.session.currentUsername) {
                        playPlayback()
                    }

                    broadcastMessage(message = lyricist.strings.roomGuyPlayed.invoke(player), isChat = false)
                }

                override fun onChatReceived(chatter: String, chatmessage: String) {
                    loggy("SYNCPLAY Protocol: $chatter sent: $chatmessage", 1003)

                    broadcastMessage(message = chatmessage, isChat = true, chatter = chatter)
                }

                override fun onSomeoneJoined(joiner: String) {
                    loggy("SYNCPLAY Protocol: $joiner joined the room.", 1004)

                    broadcastMessage(message = lyricist.strings.roomGuyJoined(joiner), isChat = false)
                }

                override fun onSomeoneLeft(leaver: String) {
                    loggy("SYNCPLAY Protocol: $leaver left the room.", 1005)

                    broadcastMessage(message = lyricist.strings.roomGuyLeft(leaver), isChat = false)

                    /* If the setting is enabled, pause playback **/
                    if (viewmodel?.player?.hasMedia() == true) {
                        val pauseOnLeft = valueBlockingly(PREF_PAUSE_ON_SOMEONE_LEAVE, true)
                        if (pauseOnLeft) {
                            pausePlayback()
                        }
                    }

                    /* Rare cases where a user can see his own self disconnected */
                    if (leaver == p.session.currentUsername) {
                        p.syncplayCallback?.onDisconnected()
                    }
                }

                override fun onSomeoneSeeked(seeker: String, toPosition: Double) {
                    loggy("SYNCPLAY Protocol: $seeker seeked to: $toPosition", 1006)

                    val oldPos = p.currentVideoPosition.toLong()
                    val newPos = toPosition.toLong()

                    /* Saving seek so it can be undone on mistake */
                    viewmodel?.seeks?.add(Pair(oldPos * 1000, newPos * 1000))

                    broadcastMessage(message = lyricist.strings.roomSeeked(seeker, timeStamper(oldPos), timeStamper(newPos)), isChat = false)

                    if (seeker != p.session.currentUsername) {
                        viewmodel?.player?.seekTo((toPosition * 1000.0).toLong())
                    }
                }

                override fun onSomeoneBehind(behinder: String, toPosition: Double) {
                    loggy("SYNCPLAY Protocol: $behinder is behind. Rewinding to $toPosition", 1007)

                    viewmodel?.player?.seekTo((toPosition * 1000.0).toLong())

                    broadcastMessage(message = lyricist.strings.roomRewinded(behinder), isChat = false)
                }

                override fun onReceivedList() {
                    loggy("SYNCPLAY Protocol: Received list update.", 1008)

                }

                override fun onSomeoneLoadedFile(person: String, file: String?, fileduration: Double?) {
                    loggy("SYNCPLAY Protocol: $person loaded: $file - Duration: $fileduration", 1009)

                    broadcastMessage(
                        message =
                        lyricist.strings.roomIsplayingfile(
                            person,
                            file ?: "",
                            timeStamper(fileduration?.toLong() ?: 0)
                        ),
                        isChat = false
                    )
                }

                override fun onPlaylistUpdated(user: String) {
                    loggy("SYNCPLAY Protocol: Playlist updated by $user", 1010)

                    /** Selecting first item on list **/
                    if (p.session.sharedPlaylist.size != 0 && p.session.spIndex.intValue == -1) {
                        //changePlaylistSelection(0)
                    }

                    /** Telling user that the playlist has been updated/changed **/
                    if (user == "") return
                    broadcastMessage(message = lyricist.strings.roomSharedPlaylistUpdated(user), isChat = false)
                }

                override fun onPlaylistIndexChanged(user: String, index: Int) {
                    loggy("SYNCPLAY Protocol: Playlist index changed by $user to $index", 1011)

                    /** Changing the selection for the user, to load the file at the given index **/
                    viewmodelScope.launch {
                        PlaylistUtils.changePlaylistSelection(index)
                    }

                    /** Telling user that the playlist selection/index has been changed **/
                    if (user == "") return
                    broadcastMessage(message = lyricist.strings.roomSharedPlaylistChanged(user), isChat = false)
                }

                override fun onConnected() {
                    loggy("SYNCPLAY Protocol: Connected!", 1012)

                    /** Adjusting connection state */
                    p.state = Constants.CONNECTIONSTATE.STATE_CONNECTED

                    /** Set as ready first-hand */
                    if (viewmodel?.media == null && viewmodel != null) {
                        p.ready = viewmodel!!.setReadyDirectly
                        p.sendPacket(JsonSender.sendReadiness(viewmodel!!.setReadyDirectly, false))
                    }


                    /** Telling user that they're connected **/
                    broadcastMessage(message = lyricist.strings.roomConnectedToServer, isChat = false)

                    /** Telling user which room they joined **/
                    broadcastMessage(message = lyricist.strings.roomYouJoinedRoom(p.session.currentRoom), isChat = false)

                    /** Resubmit any ongoing file being played **/
                    if (viewmodel?.media != null) {
                        p.sendPacket(JsonSender.sendFile(viewmodel?.media!!))
                    }

                    /** Pass any messages that have been pending due to disconnection, then clear the queue */
                    for (m in p.session.outboundQueue) {
                        p.sendPacket(m)
                    }
                    p.session.outboundQueue.clear()
                }

                override fun onConnectionAttempt() {
                    loggy("SYNCPLAY Protocol: Attempting connection...", 1013)

                    /** Telling user that a connection attempt is on **/
                    broadcastMessage(
                        message =
                        lyricist.strings.roomAttemptingConnect(
                            if (p.session.serverHost == "151.80.32.178") "syncplay.pl" else p.session.serverHost,
                            p.session.serverPort.toString()
                        ),
                        isChat = false
                    )
                }

                override fun onConnectionFailed() {
                    loggy("SYNCPLAY Protocol: Connection failed :/", 1014)

                    /** Adjusting connection state */
                    p.state = Constants.CONNECTIONSTATE.STATE_DISCONNECTED

                    /** Telling user that connection has failed **/
                    broadcastMessage(
                        message = lyricist.strings.roomConnectionFailed,
                        isChat = false, isError = true
                    )

                    /** Attempting reconnection **/
                    p.reconnect()
                }

                override fun onDisconnected() {
                    loggy("SYNCPLAY Protocol: Disconnected.", 1015)


                    /** Adjusting connection state */
                    p.state = Constants.CONNECTIONSTATE.STATE_DISCONNECTED

                    /** Telling user that the connection has been lost **/
                    broadcastMessage(message = lyricist.strings.roomAttemptingReconnection, isChat = false, isError = true)

                    /** Attempting reconnection **/
                    p.reconnect()
                }

                override fun onTLSCheck() {
                    loggy("SYNCPLAY Protocol: Checking TLS...", 1016)

                    /** Telling user that the app is checking whether the chosen server supports TLS **/
                    broadcastMessage(lyricist.strings.roomAttemptingTls, isChat = false)
                }

                override fun onReceivedTLS(supported: Boolean) {
                    loggy("SYNCPLAY Protocol: Received TLS...", 1017)

                    /** Deciding next step based on whether the server supports TLS or not **/
                    if (supported) {
                        broadcastMessage(lyricist.strings.roomTlsSupported, isChat = false)
                        p.tls = Constants.TLS.TLS_YES
                        p.upgradeTls()
                    } else {
                        broadcastMessage(lyricist.strings.roomTlsNotSupported, isChat = false, isError = true)
                        p.tls = Constants.TLS.TLS_NO
                    }
                    p.sendPacket(
                        JsonSender.sendHello(
                            p.session.currentUsername,
                            p.session.currentRoom,
                            p.session.currentPassword
                        )
                    )
                }
            }

            /** Getting information from joining info argument **/
            p.session.serverHost = joinInfo.address
            p.session.serverPort = joinInfo.port
            p.session.currentUsername = joinInfo.username
            p.session.currentRoom = joinInfo.roomname
            p.session.currentPassword = joinInfo.password

            /** Connecting */
            val tls = valueBlockingly(PREF_TLS_ENABLE, default = true)
            if (tls && p.supportsTLS()) {
                p.syncplayCallback?.onTLSCheck()
                p.tls = Constants.TLS.TLS_ASK
            }
            p.connect()
        }
    }
}
