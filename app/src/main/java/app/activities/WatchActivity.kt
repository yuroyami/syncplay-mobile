package app.activities

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.TrackSelectionOverride
import app.R
import app.activities.WatchActivityUI.RoomUI
import app.databinding.WatchActivityBinding
import app.datastore.DataStoreKeys
import app.datastore.DataStoreKeys.DATASTORE_GLOBAL_SETTINGS
import app.datastore.DataStoreKeys.DATASTORE_MISC_PREFS
import app.datastore.DataStoreKeys.MISC_NIGHTMODE
import app.datastore.DataStoreKeys.PREF_INROOM_PLAYER_SUBTITLE_SIZE
import app.datastore.DataStoreKeys.PREF_PAUSE_ON_SOMEONE_LEAVE
import app.datastore.DataStoreUtils.booleanFlow
import app.datastore.DataStoreUtils.ds
import app.datastore.DataStoreUtils.obtainBoolean
import app.datastore.DataStoreUtils.obtainInt
import app.datastore.DataStoreUtils.obtainString
import app.player.highlevel.HighLevelPlayer
import app.protocol.JsonSender
import app.protocol.ProtocolCallback
import app.protocol.SyncplayProtocol
import app.ui.AppTheme
import app.utils.MiscUtils.changeLanguage
import app.utils.MiscUtils.cutoutMode
import app.utils.MiscUtils.hideSystemUI
import app.utils.MiscUtils.string
import app.utils.MiscUtils.timeStamper
import app.utils.PlayerUtils.pausePlayback
import app.utils.PlayerUtils.playPlayback
import app.utils.PlayerUtils.reapplyTrackSelections
import app.utils.PlayerUtils.retweakSubtitleAppearance
import app.utils.PlayerUtils.trackProgress
import app.utils.RoomUtils.broadcastMessage
import app.utils.RoomUtils.pingUpdate
import app.utils.SharedPlaylistUtils.changePlaylistSelection
import app.wrappers.Constants
import app.wrappers.MediaFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking


class WatchActivity : ComponentActivity(), ProtocolCallback {

    /* Global UI variables */
    var hasVideoG = mutableStateOf(false)

    /* High-level player wrapper (currently wraps exoplayer and mpv) */
    var player: HighLevelPlayer? = null
    var engine = mutableStateOf("")

    var media: MediaFile? = null

    val seeks = mutableListOf<Pair<Long, Long>>()

    /* Variables to control ExoPlayer's HUD */
    val isNowPlaying = mutableStateOf(false)
    val timeFull = mutableLongStateOf(0L)
    val timeCurrent = mutableLongStateOf(0L)

    val hudVisibilityState = mutableStateOf(true)
    val pipMode = mutableStateOf(false)

    /* Our syncplay protocol (which extends a ViewModel to control LiveData) */
    lateinit var p: SyncplayProtocol //If it is not initialized, it means we're in Solo Mode

    var lastAudioOverride: TrackSelectionOverride? = null
    var lastSubtitleOverride: TrackSelectionOverride? = null

    val fadingMsg = mutableStateOf(false)

    var startupSlide = false

    /** Returns whether we're in Solo Mode, by checking if our protocol is initialized */
    fun isSoloMode(): Boolean {
        return !::p.isInitialized
    }

    lateinit var binding: WatchActivityBinding
    /** Now, onto overriding lifecycle methods */
    override fun onCreate(sis: Bundle?) {
        /** Applying saved language */
        val lang = runBlocking { DATASTORE_GLOBAL_SETTINGS.obtainString(DataStoreKeys.PREF_DISPLAY_LANG, "en") }
        changeLanguage(lang = lang, appCompatWay = false, recreateActivity = false, showToast = false)

        super.onCreate(sis)
        binding = WatchActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        /** Telling Android that it should keep the screen on */
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        /** Checking whether we're in solo mode or not. If we are, we don't initialize any protocol */
        val soloMode = intent.getBooleanExtra("SOLO_MODE", false)
        if (!soloMode) {

            /** Initializing our ViewModel, which is our protocol at the same time **/
            p = ViewModelProvider(this)[SyncplayProtocol::class.java]
            p.setBroadcaster(this)

            /** Getting information from intent **/
            p.session.serverHost = intent.getStringExtra("INFO_ADDRESS") ?: ""
            p.session.serverPort = intent.getIntExtra("INFO_PORT", 0)
            p.session.currentUsername = intent.getStringExtra("INFO_USERNAME") ?: ""
            p.session.currentRoom = intent.getStringExtra("INFO_ROOMNAME") ?: ""
            p.session.currentPassword = intent.getStringExtra("INFO_PASSWORD") ?: ""
        }

        /** Enabling fullscreen mode (hiding system UI) */
        hideSystemUI(false)
        cutoutMode(true)

        engine.value = runBlocking {
            DATASTORE_MISC_PREFS.obtainString(DataStoreKeys.MISC_PLAYER_ENGINE, "exo")
        }

        setupPlayer()

        /** Setting content view, making everything visible */
        binding.compose.setContent {
            val nightMode = DATASTORE_MISC_PREFS.ds().booleanFlow(MISC_NIGHTMODE, true).collectAsState(initial = true)

            AppTheme(!nightMode.value) {
                RoomUI()
            }
        }

        trackProgress()

        //TODO: fetch readiness-first-hand and apply it
        //TODO: show hint on how to add video
        //TODO: attach tooltips to buttons

        /** Starting ping update */
        pingUpdate()

        /** Now connecting to the server */
        if (!soloMode) {
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
    }

    /** the onStart() follows the onCreate(), it means all the UI is ready
     * It precedes any activity results. onCreate -> onStart -> ActivityResults -> onResume */
    override fun onStart() {
        super.onStart()


        /** Loading subtitle appearance */
        lifecycleScope.launch(Dispatchers.Main) {
            val ccsize = DataStoreKeys.DATASTORE_INROOM_PREFERENCES.obtainInt(PREF_INROOM_PLAYER_SUBTITLE_SIZE, 16)
            retweakSubtitleAppearance(ccsize.toFloat())
        }
    }

    fun setupPlayer() {
        runBlocking { lifecycleScope.launch(Dispatchers.Main) {
            player = HighLevelPlayer.create(this@WatchActivity, engine.value)

            hasVideoG.value = true
        }}
    }

    fun unalphizePlayer(engine: String) {
        when (engine) {
            "exo" -> {
                binding.exoview.alpha = 1f
            }
            "mpv" -> {
                binding.mpvview.alpha = 1f
            }
        }
    }

    override fun onStop() {
        super.onStop()
        //exoplayer?.release()
    }

    /** Last checkpoint after executing activityresults. This means the activity is fully ready. */
    override fun onResume() {
        super.onResume()
        hideSystemUI(false)

        /** Applying track choices again so the player doesn't forget about track choices **/
        reapplyTrackSelections()
    }

    override fun onSomeonePaused(pauser: String) {
        if (pauser != p.session.currentUsername) pausePlayback()
        broadcastMessage(
            message = string(R.string.room_guy_paused, pauser, timeStamper(p.currentVideoPosition.toLong())),
            isChat = false
        )

        fadingMsg.value = true
    }

    override fun onSomeonePlayed(player: String) {
        if (player != p.session.currentUsername) playPlayback()
        broadcastMessage(message = string(R.string.room_guy_played, player), isChat = false)
    }

    override fun onChatReceived(chatter: String, chatmessage: String) {
        broadcastMessage(message = chatmessage, isChat = true, chatter = chatter)

        /** We animate the fading message when the HUD is locked or hidden */
        if (chatter != p.session.currentUsername) {
            fadingMsg.value = true
        }
    }

    override fun onSomeoneSeeked(seeker: String, toPosition: Double) {
        lifecycleScope.launch(Dispatchers.Main) {
            val oldPos = p.currentVideoPosition.toLong()
            val newPos = toPosition.toLong()
            if (oldPos == newPos) return@launch

            /* Saving seek so it can be undone on mistake */
            seeks.add(Pair(oldPos * 1000, newPos * 1000))

            broadcastMessage(string(R.string.room_seeked, seeker, timeStamper(oldPos), timeStamper(newPos)), false)

            if (seeker != p.session.currentUsername) {
                player?.seekTo((toPosition * 1000.0).toLong())
            }
        }

    }

    override fun onSomeoneBehind(behinder: String, toPosition: Double) {
        lifecycleScope.launch(Dispatchers.Main) {
            player?.seekTo((toPosition * 1000.0).toLong())
        }
        broadcastMessage(string(R.string.room_rewinded, behinder), false)
    }

    override fun onSomeoneLeft(leaver: String) {
        broadcastMessage(string(R.string.room_guy_left, leaver), false)

        /* If the setting is enabled, pause playback **/
        val pauseOnLeft = runBlocking { DATASTORE_GLOBAL_SETTINGS.obtainBoolean(PREF_PAUSE_ON_SOMEONE_LEAVE, true) }
        if (pauseOnLeft) {
            pausePlayback()
        }

        /* Rare cases where a user can see his own self disconnected */
        if (leaver == p.session.currentUsername) {
            p.syncplayBroadcaster?.onDisconnected()
        }
    }

    override fun onSomeoneJoined(joiner: String) {
        broadcastMessage(string(R.string.room_guy_joined, joiner), false)
    }

    override fun onReceivedList() {

    }

    override fun onSomeoneLoadedFile(person: String, file: String?, fileduration: Double?) {
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

        /** Dismissing the 'Disconnected' popup since it's irrelevant at this point **/
        lifecycleScope.launch(Dispatchers.Main) {
            //disconnectedPopup.dismiss() /* Dismiss any disconnection popup, if they exist */
        }

        /** Telling user which room they joined **/
        broadcastMessage(message = string(R.string.room_you_joined_room, p.session.currentRoom), isChat = false)

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
        /** Selecting first item on list **/
        if (p.session.sharedPlaylist.size != 0 && p.session.sharedPlaylistIndex == -1) {
            //changePlaylistSelection(0)
        }

        /** Telling user that the playlist has been updated/changed **/
        if (user == "") return
        broadcastMessage(string(R.string.room_shared_playlist_updated, user), isChat = false)
    }

    override fun onPlaylistIndexChanged(user: String, index: Int) {
        /** Changing the selection for the user, to load the file at the given index **/
        changePlaylistSelection(index)

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

    override fun onBackPressed() {
        initiatePIPmode()
    }

    fun terminate(backPressBehavior: Boolean = false) {
        p.setBroadcaster(null)
        p.channel?.close()
        finish()
    }

    /** Let's inform Jetpack Compose that we entered picture in picture, to adjust some UI settings */
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipMode.value = isInPictureInPictureMode
    }

    /** If user leaves the app by any standard means, then we initiate picture-in-picture mode */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        //initiatePIPmode()
    }

    fun initiatePIPmode() {
        moveTaskToBack(true)

        val intent = Intent(this, this::class.java)
        val pauseplayPI = PendingIntent.getActivity(this, 1010101, intent, PendingIntent.FLAG_IMMUTABLE)


        val remoteActions = listOf<RemoteAction>(
            /* RemoteAction(
                Icon.createWithResource(this, R.drawable.ic_pause),
                "Pause", "", pauseplayPI
            ) */
        )

        val params = PictureInPictureParams.Builder().setActions(remoteActions).build()
        enterPictureInPictureMode(params)
        hudVisibilityState.value = false
    }
}