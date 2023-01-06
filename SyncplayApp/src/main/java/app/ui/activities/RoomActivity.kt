package app.ui.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import app.HudBinding
import app.popups.DisconnectedPopup
import app.popups.LoadURLPopup
import app.protocol.SyncplayProtocol
import app.sharedplaylist.SHPCallback
import app.utils.ExoUtils.buildExo
import app.utils.ListenerUtils.initListeners
import app.utils.RoomUtils.pingUpdate
import app.utils.UIUtils.applyUISettings
import app.wrappers.Constants
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RoomActivity : ComponentActivity() {

    /* Declaring our ViewBinding global variables (much faster than findViewById) **/
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
    var activePseudoPopup: Constants.POPUP? = null /* See 'Constants' class for enum values */

    /* Declaring Popup dialog variables which are used to show/dismiss different popups */
    private lateinit var disconnectedPopup: DisconnectedPopup
    lateinit var urlPopup: LoadURLPopup

    /* Shared Playlist Callback */
    var sharedPlaylistCallback: SHPCallback? = null

    /**********************************************************************************************
     *                                  LIFECYCLE METHODS
     *********************************************************************************************/
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /** Telling Android that it should keep the screen on */
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        /** Initializing our ViewModel, which is our protocol at the same time **/
        p = ViewModelProvider(this)[SyncplayProtocol::class.java]

        /** Storing the join info into the protocol directly **/
        p.session.serverHost = intent.getStringExtra("INFO_ADDRESS") ?: ""
        p.session.serverPort = intent.getIntExtra("INFO_PORT", 0)
        p.session.currentUsername = intent.getStringExtra("INFO_USERNAME") ?: ""
        p.session.currentRoom = intent.getStringExtra("INFO_ROOMNAME") ?: ""
        p.session.currentPassword = intent.getStringExtra("INFO_PASSWORD") ?: ""

        /** Adding the callback interface so we can respond to multiple syncplay events **/
        //p.setBroadcaster(this)

        /** Pref No.1 : Should the READY button be initially clicked ? **/
//        binding.syncplayReady.isChecked = sp.getBoolean("ready_firsthand", true).also {
//            p.ready = it /* Telling our protocol about our readiness */
//        }

        /** Fetching the TLS setting (whether the user wanna connect via TLS) */
        val tls = false /* sp.getBoolean("tls", false) */

        /** Now, let's connect to the server, everything should be ready **/
        if (p.channel == null) {
            /* If user has TLS on, we check for TLS from the server (Opportunistic TLS) */
            if (tls) {
                p.syncplayBroadcaster?.onTLSCheck()
                p.tls = Constants.TLS.TLS_ASK
            }
            p.connect()
        }

        /** Preparing our popups ahead of time **/
        disconnectedPopup = DisconnectedPopup(this)
        urlPopup = LoadURLPopup(this, null)

        /** Showing starter hint if it's not permanently disabled */
//        val dontShowHint = sp.getBoolean("dont_show_starter_hint", false)
//        if (!dontShowHint) {
//            showPopup(StarterHintPopup(this), true)
//        }

        /** Let's apply Room UI Settings **/
        applyUISettings()

        /** Let's apply Cut-Out Mode on the get-go, user can turn it off later **/
        //MiscUtils.cutoutMode(true, window)

//        /** Attaching tooltip longclick listeners to all the buttons using an extensive method */
//        for (child in hudBinding.buttonRowOne.children) {
//            if (child is ImageButton) {
//                child.attachTooltip(child.contentDescription.toString())
//            }
//        }

        /** Launch the visible ping updater **/
        pingUpdate()
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch(Dispatchers.Main) {
            /** We initialize ExoPlayer components here, right after onStart() and not onCreate() **/
            myExoPlayer = buildExo()

            /** Attaching ClickListeners */
            initListeners()
        }

        /** Launching the video position tracker */
        //vidPosUpdater()
    }

    val sharedFileResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result?.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data ?: return@registerForActivityResult
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)
                //addFileToPlaylist(uri)
            }
        }

    val sharedFolderResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result?.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data ?: return@registerForActivityResult
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)
                //addFolderToPlaylist(uri)
                sharedPlaylistCallback?.onUpdate()
            }
        }

    /*********************************      CALLBACKS      ****************************************/

    fun bindSHPCallback(callback: SHPCallback?) {
        this.sharedPlaylistCallback = callback
    }
}

