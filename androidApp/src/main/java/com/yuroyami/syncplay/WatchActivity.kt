package com.yuroyami.syncplay

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.yuroyami.syncplay.datastore.DataStoreKeys
import com.yuroyami.syncplay.datastore.DataStoreKeys.DATASTORE_GLOBAL_SETTINGS
import com.yuroyami.syncplay.datastore.DataStoreKeys.DATASTORE_MISC_PREFS
import com.yuroyami.syncplay.datastore.obtainBoolean
import com.yuroyami.syncplay.datastore.obtainString
import com.yuroyami.syncplay.utils.UIUtils.cutoutMode
import com.yuroyami.syncplay.utils.UIUtils.hideSystemUI
import com.yuroyami.syncplay.utils.changeLanguage
import com.yuroyami.syncplay.watchroom.RoomUI
import com.yuroyami.syncplay.watchroom.isSoloMode
import com.yuroyami.syncplay.watchroom.setReadyDirectly
import kotlinx.coroutines.runBlocking
//import com.yuroyami.syncplay.BuildConfig

class WatchActivity : ComponentActivity() {

    /* High-level player wrapper (currently wraps exoplayer and mpv) */
    //var player: HighLevelPlayer? = null
    var engine = mutableStateOf("")

    //var media: MediaFile? = null

    //var currentTrackChoices: TrackChoices = TrackChoices()

    //lateinit var binding: WatchActivityBinding

    /** Now, onto overriding lifecycle methods */
    override fun onCreate(sis: Bundle?) {
        /** Applying saved language */
        val lang = runBlocking { DATASTORE_GLOBAL_SETTINGS.obtainString(DataStoreKeys.PREF_DISPLAY_LANG, "en") }
        changeLanguage(lang = lang, context = this)

        super.onCreate(sis)

        /** Telling Android that it should keep the screen on */
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        /** Enabling fullscreen mode (hiding system UI) */
        hideSystemUI(false)
        cutoutMode(true)

        val flavor = "noLibs" //fixme: BuildConfig.FLAVOR
        engine.value = if (flavor == "noLibs") "exo" else runBlocking {
            DATASTORE_MISC_PREFS.obtainString(DataStoreKeys.MISC_PLAYER_ENGINE, "mpv")
        }

        //TODO: setupPlayer()

        /** Set ready first hand */
        setReadyDirectly = runBlocking { DATASTORE_GLOBAL_SETTINGS.obtainBoolean(DataStoreKeys.PREF_READY_FIRST_HAND, true) }

        val isSoloMode = isSoloMode() //One time execution

        /** Setting content view, making everything visible */
        setContent {
            RoomUI(isSoloMode)
        }

        //trackProgress()

        //TODO: show hint on how to add video
        //TODO: attach tooltips to buttons

        /** Starting ping update */
        //pingUpdate()

        /** Now connecting to the server */
        if (!isSoloMode) {
            val tls = false /* sp.getBoolean("tls", false) */ //Fetching the TLS setting (whether the user wanna connect via TLS)
            /* if (p.channel == null) {
                /* If user has TLS on, we check for TLS from the server (Opportunistic TLS) */
                if (tls) {
                    p.syncplayBroadcaster?.onTLSCheck()
                    p.tls = Constants.TLS.TLS_ASK
                }
                p.connect()
            }*/
        }
    }


//    /** Last checkpoint after executing activityresults. This means the activity is fully ready. */
//    @SuppressLint("UnspecifiedRegisterReceiverFlag")
//    override fun onResume() {
//        super.onResume()
//        val filter = IntentFilter(ACTION_PIP_PAUSE_PLAY)
//        registerReceiver(pipBroadcastReceiver, filter)
//
//        hideSystemUI(false)
//
//        /** Applying track choices again so the player doesn't forget about track choices **/
//        reapplyTrackChoices()
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        unregisterReceiver(pipBroadcastReceiver)
//    }
//
//    /** the onStart() follows the onCreate(), it means all the UI is ready
//     * It precedes any activity results. onCreate -> onStart -> ActivityResults -> onResume */
//    override fun onStart() {
//        super.onStart()
//
//
//        /** Loading subtitle appearance */
//        lifecycleScope.launch(Dispatchers.Main) {
//            val ccsize = DataStoreKeys.DATASTORE_INROOM_PREFERENCES.obtainInt(PREF_INROOM_PLAYER_SUBTITLE_SIZE, 16)
//            retweakSubtitleAppearance(ccsize.toFloat())
//        }
//    }
//
//    fun setupPlayer() {
//        runBlocking { lifecycleScope.launch(Dispatchers.Main) {
//            player = HighLevelPlayer.create(this@WatchActivity, engine.value)
//        }}
//    }
//
//    fun unalphizePlayer(engine: String) {
//        when (engine) {
//            "exo" -> {
//                binding.exoview.alpha = 1f
//            }
//            "mpv" -> {
//                binding.mpvview.alpha = 1f
//            }
//        }
//    }
//
//    override fun onSomeonePaused(pauser: String) {
//        if (pauser != p.session.currentUsername) pausePlayback()
//        broadcastMessage(
//            message = string(R.string.room_guy_paused, pauser, timeStamper(p.currentVideoPosition.toLong())),
//            isChat = false
//        )
//    }
//
//    override fun onSomeonePlayed(player: String) {
//        if (player != p.session.currentUsername) playPlayback()
//        broadcastMessage(message = string(R.string.room_guy_played, player), isChat = false)
//    }
//
//    override fun onChatReceived(chatter: String, chatmessage: String) {
//        broadcastMessage(message = chatmessage, isChat = true, chatter = chatter)
//    }
//
//    override fun onSomeoneSeeked(seeker: String, toPosition: Double) {
//        lifecycleScope.launch(Dispatchers.Main) {
//            val oldPos = p.currentVideoPosition.toLong()
//            val newPos = toPosition.toLong()
//            if (oldPos == newPos) return@launch
//
//            /* Saving seek so it can be undone on mistake */
//            seeks.add(Pair(oldPos * 1000, newPos * 1000))
//
//            broadcastMessage(string(R.string.room_seeked, seeker, timeStamper(oldPos), timeStamper(newPos)), false)
//
//            if (seeker != p.session.currentUsername) {
//                player?.seekTo((toPosition * 1000.0).toLong())
//            }
//        }
//
//    }
//
//    override fun onSomeoneBehind(behinder: String, toPosition: Double) {
//        lifecycleScope.launch(Dispatchers.Main) {
//            player?.seekTo((toPosition * 1000.0).toLong())
//        }
//        broadcastMessage(string(R.string.room_rewinded, behinder), false)
//    }
//
//    override fun onSomeoneLeft(leaver: String) {
//        broadcastMessage(string(R.string.room_guy_left, leaver), false)
//
//        /* If the setting is enabled, pause playback **/
//        val pauseOnLeft = runBlocking { DATASTORE_GLOBAL_SETTINGS.obtainBoolean(PREF_PAUSE_ON_SOMEONE_LEAVE, true) }
//        if (pauseOnLeft) {
//            pausePlayback()
//        }
//
//        /* Rare cases where a user can see his own self disconnected */
//        if (leaver == p.session.currentUsername) {
//            p.syncplayBroadcaster?.onDisconnected()
//        }
//    }
//
//    override fun onSomeoneJoined(joiner: String) {
//        broadcastMessage(string(R.string.room_guy_joined, joiner), false)
//    }
//
//    override fun onReceivedList() {
//
//    }
//
//    override fun onSomeoneLoadedFile(person: String, file: String?, fileduration: Double?) {
//        broadcastMessage(
//            string(
//                R.string.room_isplayingfile,
//                person,
//                file ?: "",
//                timeStamper(fileduration?.toLong() ?: 0)
//            ),
//            false
//        )
//    }
//
//    override fun onConnected() {
//        /** Adjusting connection state */
//        p.state = Constants.CONNECTIONSTATE.STATE_CONNECTED
//
//        /** Dismissing the 'Disconnected' popup since it's irrelevant at this point **/
//        /* lifecycleScope.launch(Dispatchers.Main) {
//            disconnectedPopup.dismiss() /* Dismiss any disconnection popup, if they exist */
//        } */
//
//        /** Set as ready first-hand */
//        if (media == null) {
//            p.sendPacket(JsonSender.sendReadiness(setReadyDirectly, true))
//        }
//
//        /** Telling user that they're connected **/
//        broadcastMessage(message = string(R.string.room_connected_to_server), isChat = false)
//
//        /** Telling user which room they joined **/
//        broadcastMessage(message = string(R.string.room_you_joined_room, p.session.currentRoom), isChat = false)
//
//        /** Resubmit any ongoing file being played **/
//        if (media != null) {
//            p.sendPacket(JsonSender.sendFile(media!!))
//        }
//
//        /** Pass any messages that have been pending due to disconnection, then clear the queue */
//        for (m in p.session.outboundQueue) {
//            p.sendPacket(m)
//        }
//        p.session.outboundQueue.clear()
//    }
//
//    override fun onConnectionFailed() {
//        /** Adjusting connection state */
//        p.state = Constants.CONNECTIONSTATE.STATE_DISCONNECTED
//
//        /** Telling user that connection has failed **/
//        broadcastMessage(
//            message = string(R.string.room_connection_failed),
//            isChat = false
//        )
//
//        /** Attempting reconnection **/
//        p.reconnect()
//    }
//
//    override fun onConnectionAttempt() {
//        /** Telling user that a connection attempt is on **/
//        broadcastMessage(
//            message = string(
//                R.string.room_attempting_connect,
//                if (p.session.serverHost == "151.80.32.178") "syncplay.pl" else p.session.serverHost,
//                p.session.serverPort.toString()
//            ),
//            isChat = false
//        )
//    }
//
//    override fun onDisconnected() {
//        /** Adjusting connection state */
//        p.state = Constants.CONNECTIONSTATE.STATE_DISCONNECTED
//
//        /** Telling user that the connection has been lost **/
//        broadcastMessage(string(R.string.room_attempting_reconnection), false)
//
//        /** Showing a popup that informs the user about their DISCONNECTED state **/
//        //TODO: showPopup(disconnectedPopup, true)
//
//        /** Attempting reconnection **/
//        p.reconnect()
//    }
//
//    override fun onPlaylistUpdated(user: String) {
//        /** Selecting first item on list **/
//        if (p.session.sharedPlaylist.size != 0 && p.session.sharedPlaylistIndex == -1) {
//            //changePlaylistSelection(0)
//        }
//
//        /** Telling user that the playlist has been updated/changed **/
//        if (user == "") return
//        broadcastMessage(string(R.string.room_shared_playlist_updated, user), isChat = false)
//    }
//
//    override fun onPlaylistIndexChanged(user: String, index: Int) {
//        /** Changing the selection for the user, to load the file at the given index **/
//        changePlaylistSelection(index)
//
//        /** Telling user that the playlist selection/index has been changed **/
//        if (user == "") return
//        broadcastMessage(string(R.string.room_shared_playlist_changed, user), isChat = false)
//    }
//
//    override fun onTLSCheck() {
//        /** Telling user that the app is checking whether the chosen server supports TLS **/
//        broadcastMessage("Checking whether server supports TLS", isChat = false)
//    }
//
//    override fun onReceivedTLS(supported: Boolean) {
//        /** Deciding next step based on whether the server supports TLS or not **/
//        if (supported) {
//            //p.cert = resources.openRawResource(R.raw.cert)
//            broadcastMessage("Server supports TLS !", isChat = false)
//            p.tls = Constants.TLS.TLS_YES
//            p.connect()
//        } else {
//            broadcastMessage("Server does not support TLS.", isChat = false)
//            p.tls = Constants.TLS.TLS_NO
//            p.sendPacket(
//                JsonSender.sendHello(
//                    p.session.currentUsername,
//                    p.session.currentRoom,
//                    p.session.currentPassword
//                )
//            )
//        }
//    }
//
//
//    override fun onBackPressed() {
//        //super.onBackPressed()
//        terminate()
//    }
//
//    fun terminate() {
//        p.setBroadcaster(null)
//        p.channel?.close()
//        if (player?.ismpvInit == true) {
//            MPVLib.removeObserver(player?.observer)
//            MPVLib.destroy()
//        }
//        finish()
//    }
//
//    /** Let's inform Jetpack Compose that we entered picture in picture, to adjust some UI settings */
//    @RequiresApi(Build.VERSION_CODES.O)
//    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
//        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
//        pipMode.value = isInPictureInPictureMode
//    }
//
//    /** If user leaves the app by any standard means, then we initiate picture-in-picture mode */
//    override fun onUserLeaveHint() {
//        super.onUserLeaveHint()
//
//        if (!wentForFilePick) {
//            initiatePIPmode()
//        }
//    }
//
//    fun initiatePIPmode() {
//        val isPipAllowed = runBlocking {
//            DATASTORE_INROOM_PREFERENCES.obtainBoolean(PREF_INROOM_PIP, true)
//        } && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
//
//        if (!isPipAllowed) return
//
//        moveTaskToBack(true)
//
//        updatePiPParams()
//
//        enterPictureInPictureMode()
//        hudVisibilityState.value = false
//    }
//
//    fun updatePiPParams() {
//        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
//            return
//
//        val intent = Intent(ACTION_PIP_PAUSE_PLAY)
//        val pendingIntent = PendingIntent.getBroadcast(
//            this, 6969, intent,
//            PendingIntent.FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)
//
//        val action = if (player?.isInPlayState() == true) {
//            RemoteAction(Icon.createWithResource(this, R.drawable.ic_pause),
//                "Play", "", pendingIntent)
//        } else {
//            RemoteAction(Icon.createWithResource(this, R.drawable.ic_play),
//                "Pause", "", pendingIntent)
//        }
//
//        val params = with(PictureInPictureParams.Builder()) {
//            setActions(if (hasVideoG.value) listOf(action) else listOf())
//        }
//
//        try {
//            setPictureInPictureParams(params.build())
//        } catch (_: IllegalArgumentException) { }
//    }
//
//    private val pipBroadcastReceiver = object : BroadcastReceiver() {
//        override fun onReceive(context: Context?, intent: Intent?) {
//            intent?.let {
//                if (it.action == ACTION_PIP_PAUSE_PLAY) {
//                    val pausePlayValue = it.getIntExtra("pause_zero_play_one", -1)
//
//                    if (pausePlayValue == 1) {
//                        playPlayback()
//                    } else {
//                        pausePlayback()
//                    }
//                }
//            }
//        }
//    }
//
//    val ACTION_PIP_PAUSE_PLAY = "action_pip_pause_play"
}