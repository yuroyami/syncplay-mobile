package com.reddnek.syncplay.room

import android.app.Activity
import android.content.Intent
import android.content.Intent.createChooser
import android.graphics.Color
import android.graphics.Typeface
import android.os.*
import android.util.TypedValue.COMPLEX_UNIT_SP
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.animation.AccelerateInterpolator
import android.widget.ImageButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.C.SELECTION_FLAG_DEFAULT
import com.google.android.exoplayer2.C.VIDEO_SCALING_MODE_SCALE_TO_FIT
import com.google.android.exoplayer2.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.CaptionStyleCompat
import com.google.android.exoplayer2.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
import com.google.android.exoplayer2.util.MimeTypes
import com.google.gson.GsonBuilder
import com.reddnek.syncplay.databinding.ActivityRoomBinding
import com.reddnek.syncplay.popups.*
import com.reddnek.syncplay.protocol.JsonSender.sendEmptyList
import com.reddnek.syncplay.protocol.JsonSender.sendFile
import com.reddnek.syncplay.protocol.JsonSender.sendReadiness
import com.reddnek.syncplay.protocol.JsonSender.sendState
import com.reddnek.syncplay.protocol.ProtocolBroadcaster
import com.reddnek.syncplay.protocol.SyncplayProtocol
import com.reddnek.syncplay.utils.ExoPlayerUtils.applyLastOverrides
import com.reddnek.syncplay.utils.ExoPlayerUtils.audioSelect
import com.reddnek.syncplay.utils.ExoPlayerUtils.injectVideo
import com.reddnek.syncplay.utils.ExoPlayerUtils.pausePlayback
import com.reddnek.syncplay.utils.ExoPlayerUtils.playPlayback
import com.reddnek.syncplay.utils.ExoPlayerUtils.subtitleSelect
import com.reddnek.syncplay.utils.RoomUtils.checkFileMismatches
import com.reddnek.syncplay.utils.RoomUtils.pingUpdate
import com.reddnek.syncplay.utils.RoomUtils.sendMessage
import com.reddnek.syncplay.utils.RoomUtils.sendPlayback
import com.reddnek.syncplay.utils.RoomUtils.string
import com.reddnek.syncplay.utils.RoomUtils.vidPosUpdater
import com.reddnek.syncplay.utils.SharedPlaylistUtils.addFileToPlaylist
import com.reddnek.syncplay.utils.SharedPlaylistUtils.addFolderToPlaylist
import com.reddnek.syncplay.utils.SharedPlaylistUtils.changePlaylistSelection
import com.reddnek.syncplay.utils.SyncplayUtils
import com.reddnek.syncplay.utils.SyncplayUtils.getFileName
import com.reddnek.syncplay.utils.SyncplayUtils.hideSystemUI
import com.reddnek.syncplay.utils.SyncplayUtils.loggy
import com.reddnek.syncplay.utils.SyncplayUtils.timeStamper
import com.reddnek.syncplay.utils.UIUtils.applyUISettings
import com.reddnek.syncplay.utils.UIUtils.attachTooltip
import com.reddnek.syncplay.utils.UIUtils.broadcastMessage
import com.reddnek.syncplay.utils.UIUtils.displayInfo
import com.reddnek.syncplay.utils.UIUtils.hideKb
import com.reddnek.syncplay.utils.UIUtils.replenishUsers
import com.reddnek.syncplay.utils.UIUtils.showPopup
import com.reddnek.syncplay.utils.UIUtils.toasty
import com.reddnek.syncplay.wrappers.MediaFile
import kotlin.collections.set
import kotlin.math.roundToInt
import com.reddnek.syncplay.R as rr

open class RoomActivity : AppCompatActivity(), ProtocolBroadcaster {
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
    private var seekTracker: Double = 0.0
    private var receivedSeek = false
    var startFromPosition = (-3.0).toLong()

    /*-- UI-Related --*/
    private var lockedScreen = false
    private var seekButtonEnable: Boolean? = null
    private var cutOutMode: Boolean = true
    var ccsize = 18f

    /* Specifying and creating threads for separate periodic tasks such as pinging */
    val pingingThread = HandlerThread("pingingThread")

    /* Declaring Popup dialog variables which are used to show/dismiss different popups */
    private lateinit var disconnectedPopup: DisconnectedPopup
    lateinit var sharedplaylistPopup: SharedPlaylistPopup
    lateinit var messageHistoryPopup: MessageHistoryPopup
    lateinit var urlPopup: LoadURLPopup


    /**********************************************************************************************
     *                                  LIFECYCLE METHODS
     *********************************************************************************************/

    private val videoPickResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result?.resultCode == Activity.RESULT_OK) {
                p.file = MediaFile()
                p.file?.uri = result.data?.data
                p.file?.collectInfo(this@RoomActivity)
                toasty(string(rr.string.room_selected_vid, "${p.file?.fileName}"))
                Handler(Looper.getMainLooper()).postDelayed({ myExoPlayer?.seekTo(0L) }, 2000)
                checkFileMismatches(p)
            }
        }

    private val subtitlePickResult =
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
                            .setSelectionFlags(SELECTION_FLAG_DEFAULT)
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
                applicationContext.contentResolver.takePersistableUriPermission(uri, takeFlags)
                addFileToPlaylist(uri)
            }
        }

    val sharedFolderResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result?.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data ?: return@registerForActivityResult
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                applicationContext.contentResolver.takePersistableUriPermission(uri, takeFlags)
                addFolderToPlaylist(uri)
                //sharedplaylistPopup.update()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        /** Inflating and ViewBinding */
        binding = ActivityRoomBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        hudBinding = HudBinding.bind(findViewById(rr.id.vidplayerhud))

        /** Initializing our ViewModel, which is our protocol at the same time **/
        p = ViewModelProvider(this)[SyncplayProtocol::class.java]

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
        p.addBroadcaster(this)

        /** Storing SharedPreferences to apply some settings **/
        val sp = PreferenceManager.getDefaultSharedPreferences(this)

        /** Pref No.1 : Should the READY button be initially clicked ? **/
        binding.syncplayReady.isChecked = sp.getBoolean("ready_firsthand", true).also {
            p.ready = it /* Telling our protocol about our readiness */
        }

        /** Pref No.2 : Rewind threshold **/
        p.rewindThreshold = sp.getInt("rewind_threshold", 12).toLong()

        /** Now, let's connect to the server, everything should be ready **/
        //val tls = sp.getBoolean("tls", false) /* We're not using this yet */
        if (!p.connected) {
            p.connect()
        }

        /** Preparing our popups ahead of time **/
        disconnectedPopup = DisconnectedPopup(this)
        sharedplaylistPopup = SharedPlaylistPopup(this)
        sharedplaylistPopup.update()
        messageHistoryPopup = MessageHistoryPopup(this)
        urlPopup = LoadURLPopup(this)

        /** Showing starter hint if it's not permanently disabled */
        val dontShowHint = sp.getBoolean("dont_show_starter_hint", false)
        if (!dontShowHint) {
            showPopup(StarterHintPopup(this), true)
        }

        /** Let's apply Room UI Settings **/
        applyUISettings()

        /** Let's apply Cut-Out Mode on the get-go, user can turn it off later **/
        SyncplayUtils.cutoutMode(true, window)

        /** Launch the visible ping updater **/
        pingUpdate()

        /** Inject in-room settings fragment **/
        supportFragmentManager.beginTransaction()
            .replace(rr.id.pseudo_popup_container, RoomSettingsFragment())
            .commit()

        /** Attaching tooltip longclick listeners to all the buttons using an extensive method */
        for (child in hudBinding.buttonRowOne.children) {
            if (child is ImageButton) {
                child.attachTooltip(child.contentDescription.toString())
            }
        }

        /** Leaving the app for too long in the background might destroy the activity.
         * So, bringing it up back to the foreground would necessite calling onCreate() OR the
         * other function onRestoreInstanceState in lesser severe cases.
         */
        if (savedInstanceState != null) {
            p.sendPacket(sendEmptyList())
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putDouble("last_position", p.currentVideoPosition)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        p.sendPacket(sendEmptyList())
    }

    override fun onStart() {
        super.onStart()
        /** We initialize ExoPlayer components here, right after onStart() and not onCreate() **/

        /** First, we hold a reference to preferences, we will use it quite a few times **/
        val sp = PreferenceManager.getDefaultSharedPreferences(this)

        /** LoadControl variables and building (Buffering Controller) **/
        val useCustomBuffer = sp.getBoolean("use_custom_buffer_boolean", false)
        val maxBuffer = if (useCustomBuffer) (sp.getInt("player_max_buffer", 50) * 1000) else 50000
        var minBuffer = if (useCustomBuffer) (sp.getInt("player_min_buffer", 15) * 1000) else 15000
        val playbackBuffer =
            if (useCustomBuffer) (sp.getInt("player_playback_buffer", 2500)) else 2000
        if (minBuffer < (playbackBuffer + 500)) minBuffer = playbackBuffer + 500
        val loadControl = DefaultLoadControl
            .Builder()
            .setBufferDurationsMs(
                minBuffer,
                maxBuffer,
                playbackBuffer,
                playbackBuffer + 500
            )
            .build()

        /** Now, on to building Exoplayer itself using the components we have **/
        myExoPlayer = ExoPlayer.Builder(this)
            .setLoadControl(loadControl) /* We use the custom LoadControl we initialized before */
            .setRenderersFactory(
                DefaultRenderersFactory(this).setExtensionRendererMode(
                    EXTENSION_RENDERER_MODE_PREFER /* We prefer extensions, such as FFmpeg */
                )
            )
            .build()
            .also { exoPlayer ->
                binding.vidplayer.player = exoPlayer
            }

        /** Customizing ExoPlayer components **/
        myExoPlayer?.videoScalingMode = VIDEO_SCALING_MODE_SCALE_TO_FIT /* Starter scaling */

        myExoPlayer?.playWhenReady = true /* Play once the media has been buffered */

        binding.vidplayer.setShutterBackgroundColor(Color.TRANSPARENT)
        binding.vidplayer.videoSurfaceView?.visibility = View.GONE

        /** This listener is very important, without it, syncplay is nothing but a video player */
        /** TODO: Seperate the interface from the activity **/
        myExoPlayer?.addListener(object : Player.Listener {
            override fun onIsLoadingChanged(isLoading: Boolean) {
                super.onIsLoadingChanged(isLoading)
                if (!isLoading) {
                    val duration = ((myExoPlayer?.duration?.toDouble())?.div(1000.0)) ?: 0.0
                    if (duration != p.file?.fileDuration) {
                        p.file?.fileDuration = duration
                        p.sendPacket(sendFile(p.file ?: return, this@RoomActivity))
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)
                if (myExoPlayer?.mediaItemCount != 0) {
                    when (isPlaying) {
                        true -> {
                            if (myExoPlayer?.playbackState != ExoPlayer.STATE_BUFFERING) {
                                sendPlayback(true)
                                p.paused = false
                            }
                        }
                        false -> {
                            if (myExoPlayer?.playbackState != ExoPlayer.STATE_BUFFERING) {
                                sendPlayback(false)
                                p.paused = true
                            }
                        }
                    }
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int
            ) {
                super.onPositionDiscontinuity(oldPosition, newPosition, reason)
                if (myExoPlayer?.mediaItemCount != 0) {
                    seekTracker = oldPosition.positionMs / 1000.0
                    if (reason == 1) {
                        if (!receivedSeek) {
                            val clienttime =
                                (System.currentTimeMillis() / 1000.0)
                            p.sendPacket(
                                sendState(
                                    null,
                                    clienttime,
                                    true,
                                    newPosition.positionMs,
                                    1,
                                    play = myExoPlayer?.isPlaying,
                                    p
                                )
                            )
                        } else receivedSeek = false
                    }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                super.onTracksChanged(tracks)
                /** Repopulate audio and subtitle track lists with the new analysis of tracks **/
                p.file?.analyzeTracks(myExoPlayer!!)
            }
        })

        /** Defining the subtitle appearance, and inserting it into ExoPlayer **/
        val captionStyle = CaptionStyleCompat(
            Color.WHITE,
            Color.TRANSPARENT,
            Color.TRANSPARENT,
            EDGE_TYPE_DROP_SHADOW,
            Color.BLACK,
            Typeface.DEFAULT_BOLD
        )
        binding.vidplayer.subtitleView?.also {
            it.setStyle(captionStyle)
            it.setFixedTextSize(COMPLEX_UNIT_SP, ccsize)
        }

        /** Clicking on the player hides the keyboard, and loses focus from the message box **/
        binding.vidplayer.setOnClickListener {
            hideKb()
            binding.syncplayINPUTBox.clearFocus()
        }

        /** Listening to ExoPlayer's UI Visibility **/
        binding.vidplayer.setControllerVisibilityListener { visibility ->
            if (visibility == VISIBLE) {
                binding.syncplayMESSAGERYOpacitydelegate.also {
                    it.clearAnimation()
                    it.alpha = 1f
                    it.visibility = VISIBLE
                }
                binding.syncplayMESSAGERY.also {
                    it.clearAnimation()
                    it.alpha = 1f
                    it.visibility = VISIBLE
                }
                for (c in binding.syncplayMESSAGERY.children) {
                    c.animate()
                        .alpha(1f)
                        .setDuration(1L)
                        .setInterpolator(AccelerateInterpolator())
                        .start()
                }
                binding.syncplayVisiblitydelegate.visibility = VISIBLE
                val visib = if (seekButtonEnable == false) GONE else VISIBLE
                Handler(Looper.getMainLooper()).postDelayed({
                    hudBinding.exoFfwd.visibility = visib
                    hudBinding.exoRew.visibility = visib
                }, 10)
            } else {
                hideSystemUI(this, false)
                binding.syncplayMESSAGERYOpacitydelegate.also {
                    it.clearAnimation()
                    it.alpha = 0f
                    it.visibility = GONE
                }
                binding.syncplayMESSAGERY.also {
                    it.clearAnimation()
                    it.alpha = 0f
                    it.visibility = GONE
                }
                if ((!binding.syncplayINPUTBox.hasFocus())
                    || binding.syncplayINPUTBox.text.toString().trim() == ""
                ) {
                    binding.syncplayVisiblitydelegate.visibility = GONE
                    hideKb()
                }
            }
            binding.syncplayOverviewCard.isVisible =
                (visibility == VISIBLE) && binding.syncplayOverviewcheckbox.isChecked
        }

        /************************
         * Adding a  Video File *
         ************************/
        hudBinding.syncplayAddfile.setOnClickListener {
            val popup = PopupMenu(this, hudBinding.syncplayAddfile)

            val offlineItem = popup.menu.add(0, 0, 0, getString(rr.string.room_addmedia_offline))

            val onlineItem = popup.menu.add(0, 1, 1, getString(rr.string.room_addmedia_online))

            popup.setOnMenuItemClickListener {
                when (it) {
                    offlineItem -> {
                        val intent1 = Intent()
                        intent1.type = "video/*"
                        intent1.action = Intent.ACTION_OPEN_DOCUMENT
                        val intentWrapper = createChooser(intent1, "Select a video with")
                        videoPickResult.launch(intentWrapper)
                    }
                    onlineItem -> {
                        showPopup(urlPopup, true)
                    }
                }
                return@setOnMenuItemClickListener true
            }
            popup.show()
        }


        /**************************
         * Message Focus Listener *
         **************************/
        binding.syncplayINPUTBox.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.syncplayINPUT.clearAnimation()
                binding.syncplayINPUT.alpha = 1f
                binding.syncplaySEND.clearAnimation()
                binding.syncplaySEND.alpha = 1f
                binding.syncplayINPUTBox.clearAnimation()
                binding.syncplayINPUTBox.alpha = 1f
            }
        }

        /*****************
         * Send Messages *
         *****************/
        binding.syncplaySEND.setOnClickListener {
            val msg: String = binding.syncplayINPUTBox.text.toString()
                .also {
                    it.replace("\\", "")
                    if (it.length > 150) it.substring(0, 149)
                }
            if (msg.trim().isNotEmpty()) {
                sendMessage(msg)
                binding.syncplayINPUT.isErrorEnabled = false
            } else {
                binding.syncplayINPUT.isErrorEnabled = true
                binding.syncplayINPUT.error =
                    getString(com.reddnek.syncplay.R.string.room_empty_message_error)
            }
        }

        /*******************
         * Subtitles track *
         *******************/
        hudBinding.exoSubtitle.setOnClickListener {
            if (myExoPlayer?.mediaItemCount != 0) {
                subtitleSelect(it as ImageButton)
            }
        }

        /***************
         * Audio Track *
         ***************/
        hudBinding.exoAudioTrack.setOnClickListener {
            if (myExoPlayer?.mediaItemCount != 0) {
                audioSelect(it as ImageButton)
            }
        }

        /***************
         * Lock Screen *
         ***************/
        hudBinding.syncplayLock.setOnClickListener {
            if (lockedScreen) {
                lockedScreen = false
            } else {
                lockedScreen = true
                runOnUiThread {
                    binding.syncplayVisiblitydelegate.visibility = GONE
                    binding.vidplayer.controllerHideOnTouch = false
                    binding.vidplayer.controllerAutoShow = false
                    binding.vidplayer.hideController()
                    binding.syncplayerLockoverlay.visibility = VISIBLE
                    binding.syncplayerLockoverlay.isFocusable = true
                    binding.syncplayerLockoverlay.isClickable = true
                    binding.syncplayerUnlock.visibility = VISIBLE
                    binding.syncplayerUnlock.isFocusable = true
                    binding.syncplayerUnlock.isClickable = true
                }
            }
        }

        binding.syncplayerUnlock.setOnClickListener {
            lockedScreen = false
            runOnUiThread {
                binding.vidplayer.controllerHideOnTouch = true
                binding.vidplayer.showController()
                binding.vidplayer.controllerAutoShow = true
                binding.syncplayerLockoverlay.visibility = GONE
                binding.syncplayerLockoverlay.isFocusable = false
                binding.syncplayerLockoverlay.isClickable = false
                binding.syncplayerUnlock.visibility = GONE
                binding.syncplayerUnlock.isFocusable = false
                binding.syncplayerUnlock.isClickable = false
            }
        }

        binding.syncplayerLockoverlay.setOnClickListener {
            binding.syncplayerUnlock.also {
                it.alpha = if (it.alpha == 0.35f) 0.05f else 0.35f
            }
        }

        /*****************
         * OverFlow Menu *
         *****************/
        hudBinding.syncplayMore.setOnClickListener { _ ->
            val popup = PopupMenu(this, hudBinding.syncplayAddfile)

            val loadsubItem = popup.menu.add(0, 0, 0, getString(rr.string.room_overflow_sub))

            val cutoutItem = popup.menu.add(0, 1, 1, getString(rr.string.room_overflow_cutout))
            cutoutItem.isCheckable = true
            cutoutItem.isChecked = cutOutMode
            cutoutItem.isEnabled = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)

            val seekbuttonsItem = popup.menu.add(0, 2, 2, getString(rr.string.room_overflow_ff))
            seekbuttonsItem.isCheckable = true
            val ffwdButton = hudBinding.exoFfwd
            val rwndButton = hudBinding.exoRew
            seekbuttonsItem.isChecked = seekButtonEnable != false

            val messagesItem =
                popup.menu.add(0, 3, 3, getString(rr.string.room_overflow_msghistory))

            val uiItem = popup.menu.add(0, 4, 4, getString(rr.string.room_overflow_settings))

            //val adjustItem = popup.menu.add(0,5,5,"Change Username or Room")

            popup.setOnMenuItemClickListener {
                when (it) {
                    loadsubItem -> {
                        val intent2 = Intent()
                        intent2.type = "*/*"
                        intent2.action = Intent.ACTION_OPEN_DOCUMENT
                        subtitlePickResult.launch(intent2)
                    }
                    cutoutItem -> {
                        cutOutMode = !cutOutMode
                        SyncplayUtils.cutoutMode(cutOutMode, window)
                        binding.vidplayer.performClick()
                        binding.vidplayer.performClick() /* Double click to apply cut-out */
                    }
                    seekbuttonsItem -> {
                        if (seekButtonEnable == true || seekButtonEnable == null) {
                            seekButtonEnable = false
                            ffwdButton.visibility = GONE
                            rwndButton.visibility = GONE
                        } else {
                            seekButtonEnable = true
                            ffwdButton.visibility = VISIBLE
                            rwndButton.visibility = VISIBLE
                        }
                    }
                    messagesItem -> {
                        showPopup(messageHistoryPopup, true)
                    }
                    uiItem -> {
                        binding.pseudoPopupParent.visibility = VISIBLE
                    }
                }
                return@setOnMenuItemClickListener true
            }

            popup.show()
        }

        /********************
         * Room Information *
         ********************/
        binding.syncplayOverviewcheckbox.setOnCheckedChangeListener { _, checked ->
            binding.syncplayOverviewCard.isVisible = checked
            if (checked) {
                replenishUsers(binding.syncplayOverview)
            }
        }

        /****************
         * Ready Button *
         ****************/
        binding.syncplayReady.setOnCheckedChangeListener { _, b ->
            p.ready = b
            p.sendPacket(sendReadiness(b, true))
        }

        /*******************
         * Change Fit Mode *
         *******************/
        hudBinding.syncplayScreen.setOnClickListener {
            val currentresolution = binding.vidplayer.resizeMode
            val resolutions = mutableMapOf<Int, String>()
            resolutions[AspectRatioFrameLayout.RESIZE_MODE_FIT] =
                getString(rr.string.room_scaling_fit_screen)
            resolutions[AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH] =
                getString(rr.string.room_scaling_fixed_width)
            resolutions[AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT] =
                getString(rr.string.room_scaling_fixed_height)
            resolutions[AspectRatioFrameLayout.RESIZE_MODE_FILL] =
                getString(rr.string.room_scaling_fill_screen)
            resolutions[AspectRatioFrameLayout.RESIZE_MODE_ZOOM] =
                getString(rr.string.room_scaling_zoom)

            val nextRes = currentresolution + 1
            if (nextRes == 5) {
                binding.vidplayer.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                displayInfo(resolutions[0]!!)
            } else {
                binding.vidplayer.resizeMode = nextRes
                displayInfo(resolutions[nextRes]!!)
            }

            binding.vidplayer.performClick()
            binding.vidplayer.performClick()
        }

        /** Shared Playlist */
        hudBinding.syncplaySharedPlaylist.setOnClickListener {
            showPopup(sharedplaylistPopup, true)
        }

        /** UI Settings' Popup Click Controllers **/
        binding.popupDismisser.setOnClickListener {
            binding.pseudoPopupParent.visibility = GONE
            applyUISettings()
        }

        vidPosUpdater() //Most important updater to maintain continuity

    }

    override fun onResume() {
        super.onResume()
        /** Activating Immersive Mode **/
        hideSystemUI(this, false)

        /** If there exists a file already, inject it again **/
        if (p.file != null) {
            injectVideo(p.file!!.uri!!.toString())

            /** And apply track choices again **/
            applyLastOverrides()
        }

        /** This is a simple trick to revive a dead socket **/
        p.sendPacket(sendReadiness(p.ready, true))
        p.sendPacket(sendEmptyList())
    }

    override fun onPause() {
        super.onPause()
        startFromPosition = binding.vidplayer.player?.currentPosition!!
        //updatePosition = false
    }

    override fun onStop() {
        super.onStop()

        //updatePosition = false
        myExoPlayer?.stop()
//        myMediaPlayer?.run {
//            roomViewModel.currentVideoPosition = this.currentPosition.toDouble()
//            myPlayerWindow = this.currentWindowIndex
//            playWhenReady = this.playWhenReady
//            release()
//        }
//        myMediaPlayer = null

    }

    override fun onDestroy() {
        super.onDestroy()
        myExoPlayer?.release()
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

        /* We have to deal with annoying "unpause" message prior to every seek */
//        val m1 = p.session.messageSequence.last().content
//        val m2 = string(rr.string.room_seeked, player, "", "")
//        val m3 = player.length + 5
//        val m4 = (m1.substring(0, m3) == m2.substring(0, m3))

        broadcastMessage(string(rr.string.room_guy_played, player), false)
    }

    override fun onChatReceived(chatter: String, chatmessage: String) {
        broadcastMessage(chatmessage, true, chatter)
    }

    override fun onSomeoneSeeked(seeker: String, toPosition: Double) {
        runOnUiThread {
            if (seeker != p.session.currentUsername) {
                val oldPos = timeStamper((p.currentVideoPosition).roundToInt())
                val newPos = timeStamper(toPosition.roundToInt())
                if (oldPos == newPos) return@runOnUiThread
                broadcastMessage(
                    string(
                        rr.string.room_seeked,
                        seeker,
                        oldPos,
                        newPos
                    ), false
                )
                receivedSeek = true
                myExoPlayer?.seekTo((toPosition * 1000.0).toLong())
            } else {
                val oldPos = timeStamper((seekTracker).roundToInt())
                val newPos = timeStamper(toPosition.roundToInt())
                if (oldPos == newPos) return@runOnUiThread
                broadcastMessage(
                    string(
                        rr.string.room_seeked,
                        seeker,
                        oldPos,
                        newPos
                    ), false
                )
            }
        }

    }

    override fun onSomeoneBehind(behinder: String, toPosition: Double) {
        runOnUiThread {
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

    override fun onDisconnected() {
        broadcastMessage(string(rr.string.room_attempting_reconnection), false)
        showPopup(disconnectedPopup, true)
        p.connected = false
        p.connect()
    }

    override fun onJoined() {
        broadcastMessage(string(rr.string.room_you_joined_room, p.session.currentRoom), false)
    }

    override fun onConnectionFailed() {
        broadcastMessage(string(rr.string.room_connection_failed), false)
        p.connect()
    }

    override fun onReconnected() {
        broadcastMessage(string(rr.string.room_connected_to_server), false)
        replenishUsers(binding.syncplayOverview)
        runOnUiThread {
            disconnectedPopup.dismiss() /* Dismiss any disconnection popup, if they exist */
        }

        /** Resubmit any ongoing file being played **/
        if (p.file != null) {
            p.sendPacket(sendFile(p.file!!, this))
        }
    }

    override fun onConnectionAttempt() {
        val server =
            if (p.session.serverHost == "151.80.32.178") "syncplay.pl" else p.session.serverHost
        broadcastMessage(
            string(
                rr.string.room_attempting_connect,
                server,
                p.session.serverPort.toString()
            ), false
        )
    }

    override fun onPlaylistUpdated(user: String) {
        sharedplaylistPopup.update()
        if (p.session.sharedPlaylist.size != 0 && p.session.sharedPlaylistIndex == -1) {
            changePlaylistSelection(0)
        }
        if (user == "") return
        broadcastMessage(string(rr.string.room_shared_playlist_updated, user), isChat = false)

    }

    override fun onPlaylistIndexChanged(user: String, index: Int) {
        sharedplaylistPopup.update()
        changePlaylistSelection(index)
        if (user == "") return
        broadcastMessage(string(rr.string.room_shared_playlist_changed, user), isChat = false)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        p.removeBroadcaster()
        p.socket.close()
        finish()
    }

}

