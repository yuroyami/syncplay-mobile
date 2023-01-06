package app.ui.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import app.R
import app.databinding.ExoplayerBinding
import app.protocol.JsonSender
import app.protocol.ProtocolCallback
import app.protocol.SyncplayProtocol
import app.ui.activities.WatchActivityUI.RoomUI
import app.utils.ExoUtils.applyLastOverrides
import app.utils.ExoUtils.buildExo
import app.utils.ExoUtils.pausePlayback
import app.utils.ExoUtils.playPlayback
import app.utils.ExoUtils.retweakSubtitleAppearance
import app.utils.ExoUtils.setupEventListeners
import app.utils.ExoUtils.trackProgress
import app.utils.MiscUtils.cutoutMode
import app.utils.MiscUtils.hideSystemUI
import app.utils.MiscUtils.string
import app.utils.MiscUtils.timeStamper
import app.utils.RoomUtils.broadcastMessage
import app.wrappers.Constants
import app.wrappers.MediaFile
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride
import com.google.android.exoplayer2.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WatchActivity : ComponentActivity(), ProtocolCallback {

    /*-- Our main exoplayer --*/
    lateinit var myExoPlayer: ExoPlayer
    lateinit var playerView: PlayerView
    var media: MediaFile? = null

    /* Variables to control ExoPlayer's HUD */
    val exoPlaying = mutableStateOf(false)
    val timeFull = mutableStateOf(0L)
    val timeCurrent = mutableStateOf(0L)

    /* Our syncplay protocol (which extends a ViewModel to control LiveData) */
    lateinit var p: SyncplayProtocol //If it is not initialized, it means we're in Solo Mode

    val videoSurfaceVisibility = mutableStateOf(false)

    var lastAudioOverride: TrackSelectionOverride? = null
    var lastSubtitleOverride: TrackSelectionOverride? = null

    /** Returns whether we're in Solo Mode, by checking if our protocol is initialized */
    fun isSoloMode(): Boolean {
        return !::p.isInitialized
    }

    /** Now, onto overriding lifecycle methods */
    override fun onCreate(sis: Bundle?) {
        super.onCreate(sis)

        /** Initializing our ViewModel, which is our protocol at the same time **/
        p = ViewModelProvider(this)[SyncplayProtocol::class.java]
        p.setBroadcaster(this)

        /** Getting information from intent **/
        p.session.serverHost = intent.getStringExtra("INFO_ADDRESS") ?: ""
        p.session.serverPort = intent.getIntExtra("INFO_PORT", 0)
        p.session.currentUsername = intent.getStringExtra("INFO_USERNAME") ?: ""
        p.session.currentRoom = intent.getStringExtra("INFO_ROOMNAME") ?: ""
        p.session.currentPassword = intent.getStringExtra("INFO_PASSWORD") ?: ""

        /** Enabling fullscreen mode (hiding system UI) */
        hideSystemUI(true)
        cutoutMode(true)

        /** Creating a XML PlayerView for ExoPlayer (which will be wrapped by Compose) */
        playerView = ExoplayerBinding.inflate(layoutInflater).root

        /** Setting content view, making everything visible */
        setContent {
            RoomUI()
        }

        //TODO: fetch readiness-first-hand and apply it
        //TODO: show hint on how to add video
        //TODO: Apply in-room settings
        //TODO: attach tooltips to buttons
        //TODO: start ping update

        /** Now connecting to the server */
        val tls = false /* sp.getBoolean("tls", false) */ //Fetching the TLS setting (whether the user wanna connect via TLS)
        if (p.channel == null) {
            /* If user has TLS on, we check for TLS from the server (Opportunistic TLS) */
            if (tls) {
                p.syncplayBroadcaster?.onTLSCheck()
                p.tls = Constants.TLS.TLS_ASK
            }
            p.connect()
        }

    }

    override fun onResume() {
        super.onResume()

        hideSystemUI(true)

        /** Applying track choices again so the player doesn't forget about track choices **/
        applyLastOverrides()
    }

    override fun onStart() {
        super.onStart()

        /** Initializing our ExoPlayer */
        myExoPlayer = buildExo()
        setupEventListeners()
        playerView.player = myExoPlayer

        trackProgress()

        /** Loading subtitle appearance */
        retweakSubtitleAppearance()
    }

    override fun onPause() {
        super.onPause()
        myExoPlayer.pause()
    }

    override fun onStop() {
        super.onStop()
        myExoPlayer.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        myExoPlayer.stop()
    }

    override fun onSomeonePaused(pauser: String) {
        if (pauser != p.session.currentUsername) pausePlayback()
        broadcastMessage(
            message = string(R.string.room_guy_paused, pauser, timeStamper(p.currentVideoPosition.toLong())),
            isChat = false
        )
    }

    override fun onSomeonePlayed(player: String) {
        if (player != p.session.currentUsername) playPlayback()
        broadcastMessage(message = string(R.string.room_guy_played, player), isChat = false)
    }

    override fun onChatReceived(chatter: String, chatmessage: String) {
        broadcastMessage(message = chatmessage, isChat = true, chatter = chatter)
    }

    override fun onSomeoneSeeked(seeker: String, toPosition: Double) {
        lifecycleScope.launch(Dispatchers.Main) {
            val oldPos = timeStamper(p.currentVideoPosition.toLong())
            val newPos = timeStamper(toPosition.toLong())
            if (oldPos == newPos) return@launch

            broadcastMessage(string(R.string.room_seeked, seeker, oldPos, newPos), false)

            if (seeker != p.session.currentUsername) {
                myExoPlayer.seekTo((toPosition * 1000.0).toLong())
            }
        }

    }

    override fun onSomeoneBehind(behinder: String, toPosition: Double) {
        lifecycleScope.launch(Dispatchers.Main) {
            myExoPlayer.seekTo((toPosition * 1000.0).toLong())
        }
        broadcastMessage(string(R.string.room_rewinded, behinder), false)
    }

    override fun onSomeoneLeft(leaver: String) {
        //replenishUsers(binding.syncplayOverview)
        broadcastMessage(string(R.string.room_guy_left, leaver), false)

        /* If the setting is enabled, pause playback **/
        val pauseOnLeft = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean("pause_if_someone_left", true)
        if (pauseOnLeft) {
            //TODO pausePlayback()
        }

        /* Rare cases where a user can see his own self disconnected */
        if (leaver == p.session.currentUsername) {
            p.syncplayBroadcaster?.onDisconnected()
        }
    }

    override fun onSomeoneJoined(joiner: String) {
        //replenishUsers(binding.syncplayOverview)
        broadcastMessage(string(R.string.room_guy_joined, joiner), false)
    }

    override fun onReceivedList() {
        //replenishUsers(binding.syncplayOverview)
    }

    override fun onSomeoneLoadedFile(person: String, file: String?, fileduration: Double?) {
        //replenishUsers(binding.syncplayOverview)
        broadcastMessage(
            string(
                R.string.room_isplayingfile,
                person,
                file ?: "",
                timeStamper(fileduration?.toLong() ?: 0)
            ),
            false
        )
    }

    override fun onConnected() {
        /** Adjusting connection state */
        p.state = Constants.CONNECTIONSTATE.STATE_CONNECTED

        /** Telling user that they're connected **/
        broadcastMessage(message = string(R.string.room_connected_to_server), isChat = false)

        /** Updating userlist **/
        //replenishUsers(binding.syncplayOverview)

        /** Dismissing the 'Disconnected' popup since it's irrelevant at this point **/
        lifecycleScope.launch(Dispatchers.Main) {
            //disconnectedPopup.dismiss() /* Dismiss any disconnection popup, if they exist */
        }

        /** Telling user which room they joined **/
        broadcastMessage(message = string(R.string.room_you_joined_room, p.session.currentRoom), isChat = false)

        /** Resubmit any ongoing file being played **/
        if (media != null) {
            p.sendPacket(JsonSender.sendFile(media!!, this))
        }

        /** Pass any messages that have been pending due to disconnection, then clear the queue */
        for (m in p.session.outboundQueue) {
            p.sendPacket(m)
        }
        p.session.outboundQueue.clear()
    }

    override fun onConnectionFailed() {
        /** Adjusting connection state */
        p.state = Constants.CONNECTIONSTATE.STATE_DISCONNECTED

        /** Telling user that connection has failed **/
        broadcastMessage(
            message = string(R.string.room_connection_failed),
            isChat = false
        )

        /** Attempting reconnection **/
        p.reconnect()
    }

    override fun onConnectionAttempt() {
        /** Telling user that a connection attempt is on **/
        broadcastMessage(
            message = string(
                R.string.room_attempting_connect,
                if (p.session.serverHost == "151.80.32.178") "syncplay.pl" else p.session.serverHost,
                p.session.serverPort.toString()
            ),
            isChat = false
        )
    }

    override fun onDisconnected() {
        /** Adjusting connection state */
        p.state = Constants.CONNECTIONSTATE.STATE_DISCONNECTED

        /** Telling user that the connection has been lost **/
        broadcastMessage(string(R.string.room_attempting_reconnection), false)

        /** Showing a popup that informs the user about their DISCONNECTED state **/
        //TODO: showPopup(disconnectedPopup, true)

        /** Attempting reconnection **/
        p.reconnect()
    }

    override fun onPlaylistUpdated(user: String) {
        /** Updating Shared Playlist UI **/
        //sharedPlaylistCallback?.onUpdate()

        /** Selecting first item on list **/
        if (p.session.sharedPlaylist.size != 0 && p.session.sharedPlaylistIndex == -1) {
            //changePlaylistSelection(0)
        }

        /** Telling user that the playlist has been updated/changed **/
        if (user == "") return
        broadcastMessage(string(R.string.room_shared_playlist_updated, user), isChat = false)
    }

    override fun onPlaylistIndexChanged(user: String, index: Int) {
        /** Updating Shared Playlist UI **/
        //sharedPlaylistCallback?.onUpdate()

        /** Changing the selection for the user, to load the file at the given index **/
        //changePlaylistSelection(index)

        /** Telling user that the playlist selection/index has been changed **/
        if (user == "") return
        broadcastMessage(string(R.string.room_shared_playlist_changed, user), isChat = false)
    }

    override fun onTLSCheck() {
        /** Telling user that the app is checking whether the chosen server supports TLS **/
        broadcastMessage("Checking whether server supports TLS", isChat = false)
    }

    override fun onReceivedTLS(supported: Boolean) {
        /** Deciding next step based on whether the server supports TLS or not **/
        if (supported) {
            p.cert = resources.openRawResource(R.raw.cert)
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

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        p.setBroadcaster(null)
        p.channel?.close()
        super.onBackPressed()
        finishAndRemoveTask()
    }

}