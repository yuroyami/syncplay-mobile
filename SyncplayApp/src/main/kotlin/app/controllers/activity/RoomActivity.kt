package app.controllers.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import app.HudBinding
import app.databinding.ActivityRoomBinding
import app.popups.DisconnectedPopup
import app.popups.LoadURLPopup
import app.popups.StarterHintPopup
import app.protocol.JsonSender
import app.protocol.JsonSender.sendFile
import app.protocol.ProtocolCallback
import app.protocol.SyncplayProtocol
import app.sharedplaylist.SharedPlaylistUtils.addFileToPlaylist
import app.sharedplaylist.SharedPlaylistUtils.addFolderToPlaylist
import app.sharedplaylist.SharedPlaylistUtils.changePlaylistSelection
import app.utils.ExoPlayerUtils.applyLastOverrides
import app.utils.ExoPlayerUtils.initializeExo
import app.utils.ExoPlayerUtils.injectVideo
import app.utils.ExoPlayerUtils.pausePlayback
import app.utils.ExoPlayerUtils.playPlayback
import app.utils.ListenerUtils.initListeners
import app.utils.MiscUtils
import app.utils.MiscUtils.getFileName
import app.utils.MiscUtils.hideSystemUI
import app.utils.MiscUtils.loggy
import app.utils.MiscUtils.timeStamper
import app.utils.RoomUtils.checkFileMismatches
import app.utils.RoomUtils.pingUpdate
import app.utils.RoomUtils.string
import app.utils.RoomUtils.vidPosUpdater
import app.utils.UIUtils.applyUISettings
import app.utils.UIUtils.attachTooltip
import app.utils.UIUtils.broadcastMessage
import app.utils.UIUtils.replenishUsers
import app.utils.UIUtils.showPopup
import app.utils.UIUtils.toasty
import app.wrappers.Constants.STATE_CONNECTED
import app.wrappers.Constants.STATE_DISCONNECTED
import app.wrappers.MediaFile
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride
import com.google.android.exoplayer2.util.MimeTypes
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import app.R as rr

open class RoomActivity : AppCompatActivity(), ProtocolCallback {
    /* Declaring our ViewBinding global variables (much faster than findViewById) **/
    lateinit var binding: ActivityRoomBinding
    lateinit var hudBinding: HudBinding

    /* This will initialize our protocol the first time it is needed */
    lateinit var p: SyncplayProtocol

    /*-- Declaring ExoPlayer variable --*/
    var myExoPlayer: ExoPlayer? = null

    /*-- Declaring Playtracking variables **/
    var lastAudioOverride: TrackSelectionOverride? = null
    var lastSubtitleOverride: TrackSelectionOverride? = null
    var seekTracker: Double = 0.0
    var receivedSeek = false
    var startFromPosition = (-3.0).toLong()

    /*-- UI-Related --*/
    var lockedScreen = false
    var seekButtonEnable: Boolean? = null
    var cutOutMode: Boolean = true
    var ccsize = 18f
    var activePseudoPopup = 0 /* See 'Constants' class for enum values */

    /* Declaring Popup dialog variables which are used to show/dismiss different popups */
    private lateinit var disconnectedPopup: DisconnectedPopup
    lateinit var urlPopup: LoadURLPopup

    /**********************************************************************************************
     *                                  LIFECYCLE METHODS
     *********************************************************************************************/

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        /** Inflating and ViewBinding */
        binding = ActivityRoomBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hudBinding = HudBinding.bind(findViewById(rr.id.vidplayerhud))

        /** Storing SharedPreferences to apply some settings **/
        val sp = PreferenceManager.getDefaultSharedPreferences(this)

        /** Initializing our ViewModel, which is our protocol at the same time **/
        p = ViewModelProvider(this)[SyncplayProtocol::class.java]

        /** Fetching the TLS setting (whether the user wanna connect via TLS) */
        val tls = sp.getBoolean("tls", false)

        /** Extracting joining info from our intent **/
        val ourInfo = intent.getStringExtra("json")
        val joinInfo = GsonBuilder().create().fromJson(ourInfo, List::class.java) as List<*>

        /** Storing the join info into the protocol directly **/
        val serverHost = joinInfo[0] as String
        p.session.serverHost = if (serverHost == "") "151.80.32.178" else serverHost
        p.session.serverPort = (joinInfo[1] as Double).toInt()
        p.session.currentUsername = joinInfo[2] as String
        p.session.currentRoom = joinInfo[3] as String
        p.session.currentPassword = joinInfo[4] as String?
        loggy("${joinInfo[4]}")

        /** Adding the callback interface so we can respond to multiple syncplay events **/
        p.setBroadcaster(this)

        /** Pref No.1 : Should the READY button be initially clicked ? **/
        binding.syncplayReady.isChecked = sp.getBoolean("ready_firsthand", true).also {
            p.ready = it /* Telling our protocol about our readiness */
        }

        /** Pref No.2 : Rewind threshold **/
        p.rewindThreshold = sp.getInt("rewind_threshold", 12).toLong()

        /** Now, let's connect to the server, everything should be ready **/
        if (p.channel == null) {
            /* If user has TLS on, we check for TLS from the server (Opportunistic TLS) */
            if (tls) {
                p.syncplayBroadcaster?.onTLSCheck()
                p.sendPacket(JsonSender.sendTLS())
            } else {
                /* Otherwise, just a plain connection will suffice */
                p.connect()
            }
        }

        /** Preparing our popups ahead of time **/
        disconnectedPopup = DisconnectedPopup(this)
        urlPopup = LoadURLPopup(this, null)

        /** Showing starter hint if it's not permanently disabled */
        val dontShowHint = sp.getBoolean("dont_show_starter_hint", false)
        if (!dontShowHint) {
            showPopup(StarterHintPopup(this), true)
        }

        /** Let's apply Room UI Settings **/
        applyUISettings()

        /** Let's apply Cut-Out Mode on the get-go, user can turn it off later **/
        MiscUtils.cutoutMode(true, window)

        /** Attaching tooltip longclick listeners to all the buttons using an extensive method */
        for (child in hudBinding.buttonRowOne.children) {
            if (child is ImageButton) {
                child.attachTooltip(child.contentDescription.toString())
            }
        }

        /** Launch the visible ping updater **/
        pingUpdate()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putDouble("last_position", p.currentVideoPosition)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch(Dispatchers.Main) {
            /** We initialize ExoPlayer components here, right after onStart() and not onCreate() **/
            initializeExo()

            /** Attaching ClickListeners */
            initListeners()
        }

        /** Launching the video position tracker */
        vidPosUpdater()
    }

    override fun onResume() {
        super.onResume()
        /** Activating Immersive Mode **/
        hideSystemUI(this, false)

        /** If there exists a file already, prepare it for playback **/
        if (p.file != null) {
            myExoPlayer?.prepare()

            /** Applying track choices again so the player doesn't forget about track choices **/
            applyLastOverrides()
        }
    }

    override fun onPause() {
        super.onPause()
        /* Remembering the position to play again from when the user returns to this application after this pause */
        startFromPosition = binding.vidplayer.player?.currentPosition!!
    }

    override fun onStop() {
        super.onStop()
        myExoPlayer?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        myExoPlayer?.release()
    }


    val videoPickResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result?.resultCode == Activity.RESULT_OK) {
                lifecycleScope.launch(Dispatchers.IO) {
                    p.file = MediaFile()
                    p.file?.uri = result.data?.data
                    p.file?.collectInfo(this@RoomActivity)
                    checkFileMismatches(p)
                    injectVideo(p.file!!.uri!!.toString())
                    toasty(string(rr.string.room_selected_vid, "${p.file?.fileName}"))
                }
            }
        }

    val subtitlePickResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result?.resultCode == Activity.RESULT_OK) {
                if (p.file != null) {
                    val path = result.data?.data!!
                    val filename = getFileName(path).toString()
                    val extension = filename.substring(filename.length - 4)
                    val mimeType =
                        if (extension.contains("srt")) MimeTypes.APPLICATION_SUBRIP
                        else if ((extension.contains("ass"))
                            || (extension.contains("ssa"))
                        ) MimeTypes.TEXT_SSA
                        else if (extension.contains("ttml")) MimeTypes.APPLICATION_TTML
                        else if (extension.contains("vtt")) MimeTypes.TEXT_VTT else ""
                    if (mimeType != "") {
                        p.file!!.externalSub = MediaItem.SubtitleConfiguration.Builder(path)
                            .setUri(path)
                            .setMimeType(mimeType)
                            .setLanguage(null)
                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                            .build()
                        toasty(string(rr.string.room_selected_sub, filename))
                    } else {
                        toasty(getString(rr.string.room_selected_sub_error))
                    }
                } else {
                    toasty(getString(rr.string.room_sub_error_load_vid_first))
                }
            }

        }

    val sharedFileResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result?.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data ?: return@registerForActivityResult
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)
                addFileToPlaylist(uri)
            }
        }

    val sharedFolderResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result?.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data ?: return@registerForActivityResult
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)
                addFolderToPlaylist(uri)
                //sharedplaylistPopup.update()
            }
        }

    /*********************************      CALLBACKS      ****************************************/

    override fun onSomeonePaused(pauser: String) {
        if (pauser != p.session.currentUsername) pausePlayback()
        broadcastMessage(
            string(
                rr.string.room_guy_paused,
                pauser,
                timeStamper(p.currentVideoPosition.roundToInt())
            ), false
        )
    }

    override fun onSomeonePlayed(player: String) {
        if (player != p.session.currentUsername) playPlayback()

        broadcastMessage(string(rr.string.room_guy_played, player), false)
    }

    override fun onChatReceived(chatter: String, chatmessage: String) {
        broadcastMessage(chatmessage, true, chatter)
    }

    override fun onSomeoneSeeked(seeker: String, toPosition: Double) {
        lifecycleScope.launch(Dispatchers.Main) {
            if (seeker != p.session.currentUsername) {
                val oldPos = timeStamper((p.currentVideoPosition).roundToInt())
                val newPos = timeStamper(toPosition.roundToInt())
                if (oldPos == newPos) return@launch
                broadcastMessage(string(rr.string.room_seeked, seeker, oldPos, newPos), false)
                receivedSeek = true
                myExoPlayer?.seekTo((toPosition * 1000.0).toLong())
            } else {
                val oldPos = timeStamper((seekTracker).roundToInt())
                val newPos = timeStamper(toPosition.roundToInt())
                if (oldPos == newPos) return@launch
                broadcastMessage(string(rr.string.room_seeked, seeker, oldPos, newPos), false)
            }
        }

    }

    override fun onSomeoneBehind(behinder: String, toPosition: Double) {
        lifecycleScope.launch(Dispatchers.Main) {
            myExoPlayer?.seekTo((toPosition * 1000.0).toLong())
        }
        broadcastMessage(string(rr.string.room_rewinded, behinder), false)
    }

    override fun onSomeoneLeft(leaver: String) {
        replenishUsers(binding.syncplayOverview)
        broadcastMessage(string(rr.string.room_guy_left, leaver), false)

        /* If the setting is enabled, pause playback **/
        val pauseOnLeft = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean("pause_if_someone_left", true)
        if (pauseOnLeft) {
            pausePlayback()
        }

        /* Rare cases where a user can see his own self disconnected */
        if (leaver == p.session.currentUsername) {
            p.syncplayBroadcaster?.onDisconnected()
        }
    }

    override fun onSomeoneJoined(joiner: String) {
        replenishUsers(binding.syncplayOverview)
        broadcastMessage(string(rr.string.room_guy_joined, joiner), false)
    }

    override fun onReceivedList() {
        replenishUsers(binding.syncplayOverview)
    }

    override fun onSomeoneLoadedFile(person: String, file: String?, fileduration: Double?) {
        replenishUsers(binding.syncplayOverview)
        broadcastMessage(
            string(
                rr.string.room_isplayingfile,
                person,
                file ?: "",
                timeStamper(fileduration?.roundToInt() ?: 0)
            ),
            false
        )
    }

    override fun onConnected() {
        /** Adjusting connection state */
        p.state = STATE_CONNECTED

        /** Telling user that they're connected **/
        broadcastMessage(string(rr.string.room_connected_to_server), false)

        /** Updating userlist **/
        replenishUsers(binding.syncplayOverview)

        /** Dismissing the 'Disconnected' popup since it's irrelevant at this point **/
        lifecycleScope.launch(Dispatchers.Main) {
            disconnectedPopup.dismiss() /* Dismiss any disconnection popup, if they exist */
        }

        /** Telling user which room they joined **/
        broadcastMessage(string(rr.string.room_you_joined_room, p.session.currentRoom), false)

        /** Resubmit any ongoing file being played **/
        if (p.file != null) {
            p.sendPacket(sendFile(p.file!!, this))
        }

        /** Pass any messages that have been pending due to disconnection, then clear the queue */
        for (m in p.session.outboundQueue) {
            p.sendPacket(m)
        }
        p.session.outboundQueue.clear()
    }

    override fun onConnectionFailed() {
        /** Adjusting connection state */
        p.state = STATE_DISCONNECTED

        /** Telling user that connection has failed **/
        broadcastMessage(string(rr.string.room_connection_failed), false)

        /** Attempting reconnection **/
        p.reconnect()
    }

    override fun onConnectionAttempt() {
        /** Telling user that a connection attempt is on **/
        broadcastMessage(string(rr.string.room_attempting_connect, if (p.session.serverHost == "151.80.32.178") "syncplay.pl" else p.session.serverHost, p.session.serverPort.toString()), false)
    }

    override fun onDisconnected() {
        /** Adjusting connection state */
        p.state = STATE_DISCONNECTED

        /** Telling user that the connection has been lost **/
        broadcastMessage(string(rr.string.room_attempting_reconnection), false)

        /** Showing a popup that informs the user about their DISCONNECTED state **/
        showPopup(disconnectedPopup, true)

        /** Attempting reconnection **/
        p.reconnect()
    }

    override fun onPlaylistUpdated(user: String) {
        /** Updating Shared Playlist UI **/
        //sharedplaylistPopup.update()

        /** Selecting first item on list **/
        if (p.session.sharedPlaylist.size != 0 && p.session.sharedPlaylistIndex == -1) {
            changePlaylistSelection(0)
        }

        /** Telling user that the playlist has been updated/changed **/
        if (user == "") return
        broadcastMessage(string(rr.string.room_shared_playlist_updated, user), isChat = false)
    }

    override fun onPlaylistIndexChanged(user: String, index: Int) {
        /** Updating Shared Playlist UI **/
        //sharedplaylistPopup.update()

        /** Changing the selection for the user, to load the file at the given index **/
        changePlaylistSelection(index)

        /** Telling user that the playlist selection/index has been changed **/
        if (user == "") return
        broadcastMessage(string(rr.string.room_shared_playlist_changed, user), isChat = false)
    }

    override fun onTLSCheck() {
        /** Telling user that the app is checking whether the chosen server supports TLS **/
        broadcastMessage("Checking whether server supports TLS", isChat = false)
    }

    override fun onReceivedTLS(supported: Boolean) {
        /** Deciding next step based on whether the server supports TLS or not **/
        if (supported) {
            p.cert = resources.openRawResource(app.R.raw.cert)
            broadcastMessage("Server supports TLS !", isChat = false)
        } else {
            broadcastMessage("Server does not support TLS.", isChat = false)
        }
        p.useTLS = supported
        p.connect()

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

