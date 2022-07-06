package com.chromaticnoob.syncplay.room

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.*
import android.text.Html
import android.util.TypedValue.COMPLEX_UNIT_SP
import android.view.Gravity.END
import android.view.MenuItem
import android.view.View.*
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.widget.*
import android.widget.LinearLayout.HORIZONTAL
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.ColorInt
import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources.getDrawable
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.chromaticnoob.syncplay.databinding.ActivityRoomBinding
import com.chromaticnoob.syncplayprotocol.JsonSender.sendChat
import com.chromaticnoob.syncplayprotocol.JsonSender.sendEmptyList
import com.chromaticnoob.syncplayprotocol.JsonSender.sendFile
import com.chromaticnoob.syncplayprotocol.JsonSender.sendReadiness
import com.chromaticnoob.syncplayprotocol.JsonSender.sendState
import com.chromaticnoob.syncplayprotocol.ProtocolBroadcaster
import com.chromaticnoob.syncplayprotocol.SyncplayProtocol
import com.chromaticnoob.syncplayutils.SyncplayUtils
import com.chromaticnoob.syncplayutils.SyncplayUtils.getFileName
import com.chromaticnoob.syncplayutils.SyncplayUtils.hideSystemUI
import com.chromaticnoob.syncplayutils.SyncplayUtils.loggy
import com.chromaticnoob.syncplayutils.SyncplayUtils.timeStamper
import com.chromaticnoob.syncplayutils.utils.MediaFile
import com.chromaticnoob.syncplayutils.utils.Message
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import razerdp.basepopup.BasePopupWindow
import java.io.IOException
import java.util.Collections.singletonList
import kotlin.collections.set
import kotlin.math.roundToInt
import com.chromaticnoob.syncplay.R as rr

class RoomActivity : AppCompatActivity(), ProtocolBroadcaster {

    /* Declaring our ViewBinding global variables (much faster than findViewById) **/
    private lateinit var roomBinding: ActivityRoomBinding
    private lateinit var hudBinding: HudBinding

    /* This will initialize our protocol the first time it is needed */
    lateinit var p: SyncplayProtocol

    /*-- Declaring ExoPlayer variable --*/
    private var myExoPlayer: ExoPlayer? = null

    /*-- Declaring Playtracking variables **/
    private var lastAudioOverride: TrackSelectionOverride? = null
    private var lastSubtitleOverride: TrackSelectionOverride? = null
    private var seekTracker: Double = 0.0
    private var receivedSeek = false
    private var startFromPosition = (-3.0).toLong()

    /*-- UI-Related --*/
    private var lockedScreen = false
    private var seekButtonEnable: Boolean? = null
    private var cutOutMode: Boolean = true
    private var ccsize = 18f

    /* Specifying and creating threads for separate periodic tasks such as pinging */
    private val pingingThread = HandlerThread("pingingThread")

    /* Declaring Popup dialog variables which are used to show/dismiss different popups */
    private var disconnectedPopup: DisconnectedPopup? = null

    /**********************************************************************************************
     *                                  LIFECYCLE METHODS
     *********************************************************************************************/

    private val videoPickResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result?.resultCode == Activity.RESULT_OK) {
                p.file = MediaFile()
                p.file?.uri = result.data?.data
                p.file?.collectInfo(this@RoomActivity)
                Toast.makeText(
                    this,
                    string(rr.string.room_selected_vid, "${p.file?.fileName}"),
                    Toast.LENGTH_LONG
                ).show()
                Handler(Looper.getMainLooper()).postDelayed({ myExoPlayer?.seekTo(0L) }, 2000)
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
                        Toast.makeText(
                            this,
                            string(rr.string.room_selected_sub, filename),
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            this,
                            getString(rr.string.room_selected_sub_error),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        this,
                        getString(rr.string.room_sub_error_load_vid_first),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        /** Inflating and ViewBinding */
        roomBinding = ActivityRoomBinding.inflate(layoutInflater)
        val view = roomBinding.root
        setContentView(view)
        hudBinding = HudBinding.bind(findViewById(rr.id.vidplayerhud))

        /** Initializing our ViewModel, which is our protocol at the same time **/
        p = ViewModelProvider(this)[SyncplayProtocol::class.java]

        /** Extracting joining info from our intent **/
        val ourInfo = intent.getStringExtra("json")
        val joinInfo = GsonBuilder().create().fromJson(ourInfo, List::class.java) as List<*>

        /** Storing the join info into the protocol directly **/
        val serverHost = joinInfo[0] as String
        p.serverHost = if (serverHost == "") "151.80.32.178" else serverHost
        p.serverPort = (joinInfo[1] as Double).toInt()
        p.currentUsername = joinInfo[2] as String
        p.currentRoom = joinInfo[3] as String
        p.currentPassword = joinInfo[4] as String?
        loggy("${joinInfo[4]}")

        /** Adding the callback interface so we can respond to multiple syncplay events **/
        p.addBroadcaster(this)

        /** Storing SharedPreferences to apply some settings **/
        val sp = PreferenceManager.getDefaultSharedPreferences(this)

        /** Pref No.1 : Should the READY button be initially clicked ? **/
        roomBinding.syncplayReady.isChecked = sp.getBoolean("ready_firsthand", true).also {
            p.ready = it /* Telling our protocol about our readiness */
        }

        /** Pref No.2 : Rewind threshold **/
        p.rewindThreshold = sp.getInt("rewind_threshold", 12).toLong()

        /** Now, let's connect to the server, everything should be ready **/
        //val tls = sp.getBoolean("tls", false) /* We're not using this yet */
        if (!p.connected) {
            p.connect()
        }

        /** Let's apply Room UI Settings **/
        applyUISettings()

        /** Let's apply Cut-Out Mode on the get-go, user can turn it off later **/
        SyncplayUtils.cutoutMode(true, window)

        /** Launch the visible ping updater **/
        pingUpdater()

        /** Inject preference fragment **/
        supportFragmentManager.beginTransaction()
            .replace(rr.id.pseudo_popup_container, RoomSettingsFragment())
            .commit()

        /** Leaving the app for too long in the background might destroy the activity.
         * So, bringing it up back to the foreground would necessite calling onCreate() OR the
         * other function onRestoreInstanceState in lesser severe cases.
         */

        if (savedInstanceState != null) {
            restoreSyncplaySession(savedInstanceState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putDouble("last_position", p.currentVideoPosition)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        restoreSyncplaySession(savedInstanceState)
    }

    /** Responsible for restoring the session that was destroyed due to background restriction **/
    fun restoreSyncplaySession(sis: Bundle) {
        loggy("Loading saved instance state.")
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
                roomBinding.vidplayer.player = exoPlayer
            }

        /** Customizing ExoPlayer components **/
        myExoPlayer?.videoScalingMode = VIDEO_SCALING_MODE_SCALE_TO_FIT /* Starter scaling */

//        roomBinding.vidplayer.subtitleView?.background =
//            null /* Removing any bg color on subtitles */

        myExoPlayer?.playWhenReady = true /* Play once the media has been buffered */

        /** This listener is very important, without it, syncplay is nothing but a video player */
        /** TODO: Seperate the interface from the activity **/
        myExoPlayer?.addListener(object : Player.Listener {
            override fun onIsLoadingChanged(isLoading: Boolean) {
                super.onIsLoadingChanged(isLoading)
                if (!isLoading) {
                    val duration = (myExoPlayer?.duration!!.toDouble()) / 1000.0
                    if (duration != p.file?.fileDuration) {
                        p.file?.fileDuration = duration
                        p.sendPacket(sendFile(p.file!!))
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
        roomBinding.vidplayer.subtitleView?.also {
            it.setStyle(captionStyle)
            it.setFixedTextSize(COMPLEX_UNIT_SP, ccsize)
        }

        /** Clicking on the player hides the keyboard, and loses focus from the message box **/
        roomBinding.vidplayer.setOnClickListener {
            hideKb()
            roomBinding.syncplayINPUTBox.clearFocus()
        }

        /** Listening to ExoPlayer's UI Visibility **/
        roomBinding.vidplayer.setControllerVisibilityListener { visibility ->
            if (visibility == VISIBLE) {
                roomBinding.syncplayMESSAGERYCard.also {
                    it.clearAnimation()
                    it.alpha = 1f
                    it.visibility = VISIBLE
                }
                for (c in roomBinding.syncplayMESSAGERY.children) {
                    c.animate()
                        .alpha(1f)
                        .setDuration(1L)
                        .setInterpolator(AccelerateInterpolator())
                        .start()
                }
                roomBinding.syncplayVisiblitydelegate.visibility = VISIBLE
                val visib = if (seekButtonEnable == false) GONE else VISIBLE
                Handler(Looper.getMainLooper()).postDelayed({
                    hudBinding.exoFfwd.visibility = visib
                    hudBinding.exoRew.visibility = visib
                }, 10)
            } else {
                hideSystemUI(this, false)
                roomBinding.syncplayMESSAGERYCard.also {
                    it.clearAnimation()
                    it.alpha = 0f
                    it.visibility = GONE
                }
                if (!roomBinding.syncplayINPUTBox.hasFocus()) {
                    roomBinding.syncplayVisiblitydelegate.visibility = GONE
                    hideKb()
                }
            }
            roomBinding.syncplayOverviewCard.isVisible =
                (visibility == VISIBLE) && roomBinding.syncplayOverviewcheckbox.isChecked
        }

        /************************
         * Adding a  Video File *
         ************************/
        hudBinding.syncplayAddfile.setOnClickListener {
            val intent1 = Intent()
            intent1.type = "*/*"
            intent1.action = Intent.ACTION_OPEN_DOCUMENT
            videoPickResult.launch(intent1)
        }


        /**************************
         * Message Focus Listener *
         **************************/
        roomBinding.syncplayINPUTBox.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                roomBinding.syncplayINPUT.clearAnimation()
                roomBinding.syncplayINPUT.alpha = 1f
                roomBinding.syncplaySEND.clearAnimation()
                roomBinding.syncplaySEND.alpha = 1f
                roomBinding.syncplayINPUTBox.clearAnimation()
                roomBinding.syncplayINPUTBox.alpha = 1f
            }
        }

        /*****************
         * Send Messages *
         *****************/
        roomBinding.syncplaySEND.setOnClickListener {
            val msg: String = roomBinding.syncplayINPUTBox.text.toString()
                .also {
                    it.replace("\\", "")
                    if (it.length > 150) it.substring(0, 149)
                }
            if (msg != "" && msg != " ") {
                sendMessage(msg)
                roomBinding.syncplayINPUT.isErrorEnabled = false
            } else {
                roomBinding.syncplayINPUT.isErrorEnabled = true
                roomBinding.syncplayINPUT.error =
                    getString(com.chromaticnoob.syncplay.R.string.room_empty_message_error)
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
                    roomBinding.syncplayVisiblitydelegate.visibility = GONE
                    roomBinding.vidplayer.controllerHideOnTouch = false
                    roomBinding.vidplayer.controllerAutoShow = false
                    roomBinding.vidplayer.hideController()
                    roomBinding.syncplayerLockoverlay.visibility = VISIBLE
                    roomBinding.syncplayerLockoverlay.isFocusable = true
                    roomBinding.syncplayerLockoverlay.isClickable = true
                    roomBinding.syncplayerUnlock.visibility = VISIBLE
                    roomBinding.syncplayerUnlock.isFocusable = true
                    roomBinding.syncplayerUnlock.isClickable = true
                }
            }
        }

        roomBinding.syncplayerUnlock.setOnClickListener {
            lockedScreen = false
            runOnUiThread {
                roomBinding.vidplayer.controllerHideOnTouch = true
                roomBinding.vidplayer.showController()
                roomBinding.vidplayer.controllerAutoShow = true
                roomBinding.syncplayerLockoverlay.visibility = GONE
                roomBinding.syncplayerLockoverlay.isFocusable = false
                roomBinding.syncplayerLockoverlay.isClickable = false
                roomBinding.syncplayerUnlock.visibility = GONE
                roomBinding.syncplayerUnlock.isFocusable = false
                roomBinding.syncplayerUnlock.isClickable = false
            }
        }

        roomBinding.syncplayerLockoverlay.setOnClickListener {
            roomBinding.syncplayerUnlock.also {
                it.alpha = if (it.alpha == 0.35f) 0.05f else 0.35f
            }
        }

        /*****************
         * OverFlow Menu *
         *****************/
        hudBinding.syncplayMore.setOnClickListener { _ ->
            val popup = PopupMenu(
                this,
                hudBinding.syncplayAddfile
            ) /* It's more convenient to anchor it on lock button */

            val loadsubItem =
                popup.menu.add(
                    0,
                    0,
                    0,
                    getString(com.chromaticnoob.syncplay.R.string.room_overflow_sub)
                )

            val cutoutItem = popup.menu.add(
                0,
                1,
                1,
                getString(com.chromaticnoob.syncplay.R.string.room_overflow_cutout)
            )
            cutoutItem.isCheckable = true
            cutoutItem.isChecked = cutOutMode
            cutoutItem.isEnabled = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            val seekbuttonsItem = popup.menu.add(
                0,
                2,
                2,
                getString(com.chromaticnoob.syncplay.R.string.room_overflow_ff)
            )
            seekbuttonsItem.isCheckable = true
            val ffwdButton = hudBinding.exoFfwd
            val rwndButton = hudBinding.exoRew
            seekbuttonsItem.isChecked = seekButtonEnable != false
            val messagesItem = popup.menu.add(
                0,
                3,
                3,
                getString(com.chromaticnoob.syncplay.R.string.room_overflow_msghistory)
            )
            val uiItem = popup.menu.add(
                0,
                4,
                4,
                getString(com.chromaticnoob.syncplay.R.string.room_overflow_settings)
            )
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
                        roomBinding.vidplayer.performClick()
                        roomBinding.vidplayer.performClick() /* Double click to apply cut-out */
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
                        messageHistoryPopup()
                    }
                    uiItem -> {
                        roomBinding.pseudoPopupParent.visibility = VISIBLE
                    }
                }
                return@setOnMenuItemClickListener true
            }

            popup.show()
        }

        /********************
         * Room Information *
         ********************/
        roomBinding.syncplayOverviewcheckbox.setOnCheckedChangeListener { _, checked ->
            roomBinding.syncplayOverviewCard.isVisible = checked
            if (checked) {
                replenishUsers(roomBinding.syncplayOverview)
            }
        }

        /****************
         * Ready Button *
         ****************/
        roomBinding.syncplayReady.setOnCheckedChangeListener { _, b ->
            p.ready = b
            p.sendPacket(sendReadiness(b, true))
        }

        /*******************
         * Change Fit Mode *
         *******************/
        hudBinding.syncplayScreen.setOnClickListener {
            val currentresolution = roomBinding.vidplayer.resizeMode
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
                roomBinding.vidplayer.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                displayInfo(resolutions[0]!!)
            } else {
                roomBinding.vidplayer.resizeMode = nextRes
                displayInfo(resolutions[nextRes]!!)
            }

            roomBinding.vidplayer.performClick()
            roomBinding.vidplayer.performClick()
        }

        /** UI Settings' Popup Click Controllers **/
        roomBinding.popupDismisser.setOnClickListener {
            roomBinding.pseudoPopupParent.visibility = GONE
            applyUISettings()
        }
    }

    override fun onResume() {
        super.onResume()
        /** Activating Immersive Mode **/
        hideSystemUI(this, false)

        /** If there exists a file already, inject it again **/
        if (p.file != null) {
            injectVideo(roomBinding.vidplayer.player as ExoPlayer, p.file!!.uri!!)
            roomBinding.starterInfo.visibility = GONE

            /** And apply track choices again **/
            applyLastOverrides()
        }

        /** This is a simple trick to revive a dead socket **/
        p.sendPacket(sendReadiness(p.ready, true))
        p.sendPacket(sendEmptyList())
    }

    override fun onPause() {
        super.onPause()
        startFromPosition = roomBinding.vidplayer.player?.currentPosition!!
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
        //updatePosition = false
        myExoPlayer?.release()
    }

    /*********************************************************************************************
     *                                     CUSTOM FUNCTIONS                                      *
     ********************************************************************************************/
    private fun injectVideo(mp: ExoPlayer, mediaPath: Uri) {
        try {
            /** This is the builder responsible for building a MediaItem component for ExoPlayer **/
            val vidbuilder = MediaItem.Builder()

            /** Seeing if we have any loaded external sub **/
            if (p.file?.externalSub != null) {
                runOnUiThread {
                    hudBinding.exoSubtitle.setImageDrawable(
                        getDrawable(
                            this,
                            rr.drawable.ic_subtitles
                        )
                    )
                }
                vidbuilder.setUri(mediaPath)
                    .setSubtitleConfigurations(singletonList(p.file!!.externalSub!!))
            } else {
                vidbuilder.setUri(mediaPath)
            }

            /** Now finally, we build it **/
            val vid = vidbuilder.build()

            /** Injecting it into ExoPlayer and getting relevant info **/
            runOnUiThread {
                mp.setMediaItem(vid) /* This loads the media into ExoPlayer **/

                hudBinding.exoPlay.performClick()
                hudBinding.exoPause.performClick()
                /** The play/pause trick to fully load the vid **/

                p.currentVideoPosition = 0.0
                /** Goes back to the beginning */

                /** Seeing if we have to start over **/
                if (startFromPosition != (-3.0).toLong()) mp.seekTo(startFromPosition)
            }

            if (!p.connected) {
                p.sendPacket(sendFile(p.file!!))
            }
            vidPosUpdater() //Most important updater to maintain continuity
        } catch (e: IOException) {
            throw RuntimeException("Invalid asset folder")
        }
    }

    private fun pausePlayback() {
        runOnUiThread {
            myExoPlayer?.pause()
        }
    }

    private fun playPlayback() {
        runOnUiThread {
            myExoPlayer?.play()
        }
        hideSystemUI(this, false)
    }

    private fun sendPlayback(play: Boolean) {
        val clienttime = System.currentTimeMillis() / 1000.0
        p.sendPacket(
            sendState(null, clienttime, null, 0, 1, play, p)
        )
    }

    private fun sendMessage(message: String) {
        hideKb()
        if (roomBinding.syncplayMESSAGERY.visibility != VISIBLE) {
            roomBinding.syncplayVisiblitydelegate.visibility = GONE
        }
        p.sendPacket(
            sendChat(message)
        )
        roomBinding.syncplayINPUTBox.setText("")
    }

    private fun replenishMsgs(rltvLayout: RelativeLayout) {
        GlobalScope.launch(Dispatchers.Main) {
            rltvLayout.removeAllViews() /* First, we clean out the current messages */
            val isTimestampEnabled = PreferenceManager
                .getDefaultSharedPreferences(this@RoomActivity)
                .getBoolean("ui_timestamp", true)
            val maxMsgsCount = PreferenceManager
                .getDefaultSharedPreferences(this@RoomActivity)
                .getInt("msg_count", 12) /* We obtain max count, determined by user */

            val msgs = p.messageSequence.takeLast(maxMsgsCount)

            for (message in msgs) {
                val msgPosition: Int = msgs.indexOf(message)

                val txtview = TextView(this@RoomActivity)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    txtview.text =
                        Html.fromHtml(message.factorize(isTimestampEnabled, this@RoomActivity))
                } else {
                    txtview.text = Html.fromHtml(
                        message.factorize(isTimestampEnabled, this@RoomActivity),
                        Html.FROM_HTML_MODE_LEGACY
                    )
                }
                txtview.textSize = PreferenceManager.getDefaultSharedPreferences(this@RoomActivity)
                    .getInt("msg_size", 12).toFloat()

                val rltvParams: RelativeLayout.LayoutParams = RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
                rltvParams.addRule(RelativeLayout.ALIGN_PARENT_START, RelativeLayout.TRUE)
                rltvParams.marginStart = 4
                txtview.id = generateViewId()

                val msgFadeTimeout =
                    PreferenceManager.getDefaultSharedPreferences(this@RoomActivity)
                        .getInt("message_persistance", 3).toLong() * 1000
                if (message != msgs[0]) {
                    rltvParams.addRule(
                        RelativeLayout.BELOW,
                        rltvLayout.getChildAt(msgPosition - 1).id
                    )
                }
                rltvLayout.addView(txtview, msgPosition, rltvParams)

                //Animate
                roomBinding.syncplayMESSAGERY.also {
                    it.clearAnimation()
                    it.visibility = VISIBLE
                    it.alpha = 1f
                }
                if (!roomBinding.vidplayer.isControllerVisible) {
                    if (message == msgs.last()) {
                        txtview.clearAnimation()
                        txtview.alpha = 1f
                        txtview.animate()
                            .alpha(0f)
                            .setDuration(msgFadeTimeout)
                            .setInterpolator(AccelerateInterpolator())
                            .start()
                    } else {
                        txtview.clearAnimation()
                        txtview.alpha = 0f
                    }
                }
            }
        }
    }

    private fun replenishUsers(linearLayout: LinearLayout) {
        runOnUiThread {
            if (roomBinding.syncplayOverviewcheckbox.isChecked) {
                linearLayout.removeAllViews()
                val userList: MutableMap<String, MutableList<String>> = p.userList

                //Creating line for room-name:
                val roomnameView = TextView(this)
                roomnameView.text =
                    string(rr.string.room_details_current_room, p.currentRoom)
                roomnameView.isFocusable = false
                val linearlayout0 = LinearLayout(this)
                val linearlayoutParams0: LinearLayout.LayoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                linearlayout0.gravity = END
                linearlayout0.orientation = HORIZONTAL
                linearlayout0.addView(roomnameView)
                linearlayout0.isFocusable = false
                roomBinding.syncplayOverview.addView(linearlayout0, linearlayoutParams0)

                for (user in userList.keys) {
                    //First line of user
                    val userProperties = userList[user]!!
                    val usernameView = TextView(this)
                    usernameView.text = user
                    if (userProperties[0] == "0") {
                        usernameView.setTextColor(0xFFECBF39.toInt())
                        usernameView.setTypeface(usernameView.typeface, Typeface.BOLD)
                    }
                    val usernameReadiness: Boolean =
                        if (userProperties[1] == "true") true else if (userProperties[1] == "false") false else false
                    val userIconette = ImageView(this)
                    userIconette.setImageResource(rr.drawable.ic_user)
                    val userReadinessIcon = ImageView(this)
                    if (usernameReadiness) {
                        userReadinessIcon.setImageResource(rr.drawable.ready_1)
                    } else {
                        userReadinessIcon.setImageResource(rr.drawable.ready_0)
                    }

                    //Creating Linear Layout for 1st line
                    val linearlayout = LinearLayout(this)
                    val linearlayoutParams: LinearLayout.LayoutParams =
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    linearlayout.gravity = END
                    linearlayout.orientation = HORIZONTAL
                    linearlayout.addView(usernameView)
                    linearlayout.addView(userIconette)
                    linearlayout.addView(userReadinessIcon)
                    linearlayout.isFocusable = false
                    usernameView.isFocusable = false
                    userIconette.isFocusable = false
                    userReadinessIcon.isFocusable = false
                    roomBinding.syncplayOverview.addView(linearlayout, linearlayoutParams)

                    //Second line (File name)
                    val isThereFile = userList[user]?.get(2) != ""
                    val fileFirstLine =
                        if (isThereFile) userList[user]?.get(2) else getString(rr.string.room_details_nofileplayed)

                    val lineArrower = ImageView(this)
                    lineArrower.setImageResource(rr.drawable.ic_arrowleft)

                    val lineBlanker = ImageView(this)
                    lineBlanker.setImageResource(rr.drawable.ic_blanker)

                    val lineFile = TextView(this)
                    lineFile.text = fileFirstLine
                    lineFile.setTextSize(COMPLEX_UNIT_SP, 11f)

                    val linearlayout2 = LinearLayout(this)
                    val linearlayoutParams2: LinearLayout.LayoutParams =
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    linearlayout2.gravity = END
                    linearlayout2.orientation = HORIZONTAL
                    linearlayout2.addView(lineFile)
                    linearlayout2.addView(lineArrower)
                    linearlayout2.addView(lineBlanker)
                    linearlayout2.isFocusable = false
                    lineFile.isFocusable = false
                    lineArrower.isFocusable = false
                    lineBlanker.isFocusable = false
                    roomBinding.syncplayOverview.addView(linearlayout2, linearlayoutParams2)

                    //Third Line (file info)
                    if (isThereFile) {
                        val filesizegetter = if (userList[user]?.get(4)
                                ?.toIntOrNull() != null
                        ) (userList[user]!![4].toDouble() / 1000000.0).toFloat() else 0.0.toFloat()
                        val fileInfoLine = string(
                            rr.string.room_details_file_properties,
                            timeStamper(userList[user]?.get(3)?.toDouble()?.roundToInt()!!),
                            filesizegetter.toString()
                        )
                        val lineFileInfo = TextView(this)
                        lineFileInfo.text = fileInfoLine
                        lineFileInfo.setTextSize(COMPLEX_UNIT_SP, 11f)

                        val linearlayout3 = LinearLayout(this)
                        val linearlayoutParams3: LinearLayout.LayoutParams =
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                        linearlayout3.gravity = END
                        linearlayout3.orientation = HORIZONTAL
                        linearlayout3.addView(lineFileInfo)
                        linearlayout3.isFocusable = false
                        lineFileInfo.isFocusable = false
                        for (f in (0 until 2)) {
                            val lineBlanker3 = ImageView(this)
                            lineBlanker3.setImageResource(rr.drawable.ic_blanker)
                            linearlayout3.addView(lineBlanker3)
                            lineBlanker3.isFocusable = false
                        }
                        roomBinding.syncplayOverview.addView(linearlayout3, linearlayoutParams3)
                    }
                }
            }
        }
    }

    private fun subtitleSelect(ccButton: ImageButton) {
        p.file?.analyzeTracks(myExoPlayer!!)
        if (p.file == null) return
        if (p.file!!.subtitleTracks.isEmpty()) {
            displayInfo(getString(rr.string.room_sub_track_notfound))
            runOnUiThread {
                ccButton.setImageDrawable(getDrawable(this, rr.drawable.ic_subtitles_off))
            }
        } else {
            val popup = PopupMenu(this, ccButton)
            popup.menu.add(0, -999, 0, getString(rr.string.room_sub_track_disable))
            for (subtitleTrack in p.file!!.subtitleTracks) {
                /* Choosing a name for the sub track, a format's label is a good choice */
                val name = if (subtitleTrack.format?.label == null) {
                    getString(rr.string.room_track_track)
                } else {
                    subtitleTrack.format?.label!!
                }

                /* Now creating the popup menu item corresponding to the audio track */
                val item = popup.menu.add(
                    0,
                    p.file!!.subtitleTracks.indexOf(subtitleTrack),
                    0,
                    "$name [${(subtitleTrack.format?.language).toString().uppercase()}]"
                )

                /* Making the popup menu item checkable */
                item.isCheckable = true

                /* Now to see whether it should be checked or not (whether it's selected) */
                item.isChecked = subtitleTrack.selected
            }

            popup.setOnMenuItemClickListener { menuItem: MenuItem ->
                val builder = myExoPlayer?.trackSelector?.parameters?.buildUpon()

                /* First, clearing our subtitle track selection (This helps troubleshoot many issues */
                myExoPlayer?.trackSelector?.parameters =
                    builder?.clearOverridesOfType(C.TRACK_TYPE_TEXT)!!.build()
                lastSubtitleOverride = null

                /* Now, selecting our subtitle track should one be selected */
                if (menuItem.itemId != -999) {
                    lastSubtitleOverride = TrackSelectionOverride(
                        p.file!!.subtitleTracks[menuItem.itemId].trackGroup!!,
                        p.file!!.subtitleTracks[menuItem.itemId].index
                    )
                    myExoPlayer?.trackSelector?.parameters =
                        builder.addOverride(lastSubtitleOverride!!).build()
                }

                /** Show an info that audio track has been changed **/
                displayInfo(string(rr.string.room_sub_track_changed, menuItem.title.toString()))
                return@setOnMenuItemClickListener true
            }

            // Show the popup menu.
            popup.show()

        }
    }

    private fun audioSelect(audioButton: ImageButton) {
        p.file?.analyzeTracks(myExoPlayer!!)
        if (p.file == null) return
        if (p.file!!.audioTracks.isEmpty()) {
            displayInfo(getString(rr.string.room_audio_track_not_found)) /* Otherwise, no audio track found */
        } else {
            val popup =
                PopupMenu(this, audioButton) /* Creating a popup menu, anchored on Audio Button */

            /** Going through the entire audio track list, and populating the popup menu with each one of them **/
            for (audioTrack in p.file!!.audioTracks) {
                /* Choosing a name for the audio track, a format's label is a good choice */
                val name = if (audioTrack.format?.label == null) {
                    getString(rr.string.room_track_track)
                } else {
                    audioTrack.format?.label!!
                }

                /* Now creating the popup menu item corresponding to the audio track */
                val item = popup.menu.add(
                    0,
                    p.file!!.audioTracks.indexOf(audioTrack),
                    0,
                    "$name [${(audioTrack.format?.language).toString().uppercase()}]"
                )

                /* Making the popup menu item checkable */
                item.isCheckable = true

                /* Now to see whether it should be checked or not (whether it's selected) */
                item.isChecked = audioTrack.selected
            }

            popup.setOnMenuItemClickListener { menuItem: MenuItem ->

                val builder = myExoPlayer?.trackSelector?.parameters?.buildUpon()

                /* First, clearing our audio track selection */
                myExoPlayer?.trackSelector?.parameters =
                    builder?.clearOverridesOfType(C.TRACK_TYPE_AUDIO)!!.build()

                lastAudioOverride = TrackSelectionOverride(
                    p.file!!.audioTracks[menuItem.itemId].trackGroup!!,
                    p.file!!.audioTracks[menuItem.itemId].index
                )
                val newParams = builder.addOverride(lastAudioOverride!!).build()

                myExoPlayer?.trackSelector?.parameters = newParams

                /** Show an info that audio track has been changed **/
                displayInfo(string(rr.string.room_audio_track_changed, menuItem.title.toString()))
                return@setOnMenuItemClickListener true
            }

            // Show the popup menu.
            popup.show()
        }
    }

    private fun hideKb() {
        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.ime())
        roomBinding.syncplayINPUTBox.clearFocus()
    }

    /** The reason why we didn't create a custom HandlerThread for vidPosUpdater task, is because
     * the player should not be accessed from a thread other than the thread it is initialized on.
     *
     * In this case, I use it always on the main thread.
     */
    private fun vidPosUpdater() {
        Handler(Looper.getMainLooper()).postDelayed({
            if (myExoPlayer?.isCurrentMediaItemSeekable == true) {
                /* Informing my ViewModel about current vid position so it is retrieved for networking after */
                runOnUiThread {
                    val progress = (roomBinding.vidplayer.player?.currentPosition?.div(1000.0))
                    if (progress != null) {
                        p.currentVideoPosition = progress
                    }
                }
            }
            vidPosUpdater()
        }, 100)
    }

    private fun pingUpdater() {
        try {
            if (!pingingThread.isAlive) {
                pingingThread.start()
            }
            pingUpdaterCore()
        } catch (e: IllegalThreadStateException) {
            pingUpdaterCore()
        }
    }

    private fun pingUpdaterCore() {
        Handler(pingingThread.looper).postDelayed({
            if (p.socket.isConnected && !p.socket.isClosed && !p.socket.isInputShutdown) {
                p.ping = SyncplayUtils.pingIcmp("151.80.32.178", 32) * 1000.0
                runOnUiThread {
                    roomBinding.syncplayConnectionInfo.text =
                        string(rr.string.room_ping_connected, "${p.ping}")
                    when (p.ping) {
                        in (0.0..100.0) -> roomBinding.syncplaySignalIcon.setImageResource(rr.drawable.ping_3)
                        in (100.0..200.0) -> roomBinding.syncplaySignalIcon.setImageResource(rr.drawable.ping_2)
                        else -> roomBinding.syncplaySignalIcon.setImageResource(rr.drawable.ping_1)
                    }
                }
            } else {
                runOnUiThread {
                    roomBinding.syncplayConnectionInfo.text =
                        string(rr.string.room_ping_disconnected)
                    roomBinding.syncplaySignalIcon.setImageDrawable(
                        getDrawable(
                            this@RoomActivity,
                            rr.drawable.ic_unconnected
                        )
                    )
                }
                p.syncplayBroadcaster?.onDisconnected()
            }
            pingUpdater()
        }, 1000)
    }

    @UiThread
    private fun broadcastMessage(message: String, isChat: Boolean, chatter: String = "") {
        /** Messages are just a wrapper class for everything we need about a message
        So first, we initialize it, customize it, then add it to our long list of messages */
        val msg = Message()

        /** Check if it's a system or a user message **/
        if (isChat) {
            msg.sender = chatter
        }

        /** Check if the sender is also the main user, to determine colors **/
        if (chatter.lowercase() == p.currentUsername.lowercase()) {
            msg.isMainUser = true
        }

        /** Assigning the message content to the message **/
        msg.content =
            message /* Assigning the message content to the variable inside our instance */

        /** Adding the message instance to our message sequence **/
        p.messageSequence.add(msg)

        /** Refresh views **/
        replenishMsgs(roomBinding.syncplayMESSAGERY)
    }

    private fun displayInfo(msg: String) {
        roomBinding.syncplayInfoDelegate.clearAnimation()
        roomBinding.syncplayInfoDelegate.text = msg
        roomBinding.syncplayInfoDelegate.alpha = 1f
        roomBinding.syncplayInfoDelegate.animate()
            .alpha(0f)
            .setDuration(750L)
            .setInterpolator(AccelerateInterpolator())
            .start()
    }

    private fun messageHistoryPopup() {
        class DemoPopup(context: Context?) : BasePopupWindow(context) {
            init {
                setContentView(rr.layout.popup_messages)
                val rltvLayout = findViewById<RelativeLayout>(rr.id.syncplay_MESSAGEHISTORY)
                rltvLayout.removeAllViews()
                val msgs = p.messageSequence
                for (message in msgs) {
                    val msgPosition: Int = msgs.indexOf(message)

                    val txtview = TextView(this@RoomActivity)
                    txtview.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                        Html.fromHtml(
                            message.factorize(true, this@RoomActivity),
                            Html.FROM_HTML_MODE_LEGACY
                        ) else Html.fromHtml(message.factorize(true, this@RoomActivity))
                    txtview.textSize = 9F
                    val rltvParams: RelativeLayout.LayoutParams = RelativeLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    rltvParams.addRule(RelativeLayout.ALIGN_PARENT_START, RelativeLayout.TRUE)
                    txtview.id = generateViewId()
                    if (message != msgs[0]) {
                        rltvParams.addRule(
                            RelativeLayout.BELOW,
                            rltvLayout.getChildAt(msgPosition - 1).id
                        )
                    }
                    rltvLayout.addView(txtview, msgPosition, rltvParams)
                }

                val dismisser = findViewById<FrameLayout>(rr.id.messages_popup_dismisser)
                dismisser.setOnClickListener {
                    this.dismiss()
                }
            }
        }

        DemoPopup(this).setBlurBackgroundEnable(true).showPopupWindow()
    }

    inner class DisconnectedPopup(context: Context) : BasePopupWindow(context) {
        init {
            setContentView(rr.layout.popup_disconnected)
            val gif = findViewById<ImageView>(rr.id.disconnected_gif)
            Glide.with(context)
                .asGif()
                .load(rr.raw.spinner)
                .apply(RequestOptions.diskCacheStrategyOf(DiskCacheStrategy.NONE))
                .listener(object : RequestListener<GifDrawable?> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<GifDrawable?>?,
                        isFirstResource: Boolean
                    ): Boolean {
                        return false
                    }

                    override fun onResourceReady(
                        resource: GifDrawable?,
                        model: Any?,
                        target: Target<GifDrawable?>?,
                        dataSource: DataSource?,
                        isFirstResource: Boolean
                    ): Boolean {
                        resource?.setLoopCount(999999999)
                        return false
                    }
                })
                .into(gif)

            val dismisser = findViewById<FrameLayout>(rr.id.disconnected_popup_dismisser)
            dismisser.setOnClickListener {
                this.dismiss()
            }

        }
    }

    private fun disconnectedPopup() {
        runOnUiThread {
            disconnectedPopup = DisconnectedPopup(this).also {
                it.setBlurBackgroundEnable(true)
                it.showPopupWindow()
            }
        }
    }

    fun applyUISettings() {
        /* For settings: Timestamp,Message Count,Message Font Size */
        replenishMsgs(roomBinding.syncplayMESSAGERY)

        /* Holding a reference to SharedPreferences to use it later */
        val sp = PreferenceManager.getDefaultSharedPreferences(this)

        /* Applying "overview_alpha" setting */
        val alpha1 = sp.getInt("overview_alpha", 30) //between 0-255
        @ColorInt val alphaColor1 = ColorUtils.setAlphaComponent(Color.DKGRAY, alpha1)
        roomBinding.syncplayOverviewCard.setCardBackgroundColor(alphaColor1)

        /* Applying MESSAGERY Alpha **/
        val alpha2 = sp.getInt("messagery_alpha", 0) //between 0-255
        @ColorInt val alphaColor2 = ColorUtils.setAlphaComponent(Color.DKGRAY, alpha2)
        roomBinding.syncplayMESSAGERYCard.setCardBackgroundColor(alphaColor2)

        /* Applying Subtitle Size setting */
        ccsize = sp.getInt("subtitle_size", 18).toFloat()
    }

    /*********************************************************************************************
     *                                        CALLBACKS                                          *
     ********************************************************************************************/
    override fun onSomeonePaused(pauser: String) {
        if (pauser != p.currentUsername) pausePlayback()
        broadcastMessage(
            string(
                rr.string.room_guy_paused,
                pauser,
                timeStamper(p.currentVideoPosition.roundToInt())
            ), false
        )
    }

    override fun onSomeonePlayed(player: String) {
        if (player != p.currentUsername) playPlayback()
        broadcastMessage(string(rr.string.room_guy_played, player), false)
    }

    override fun onChatReceived(chatter: String, chatmessage: String) {
        broadcastMessage(chatmessage, true, chatter)
    }

    override fun onSomeoneSeeked(seeker: String, toPosition: Double) {
        runOnUiThread {
            if (seeker != p.currentUsername) {
                broadcastMessage(
                    string(
                        rr.string.room_seeked,
                        seeker,
                        timeStamper((p.currentVideoPosition).roundToInt()),
                        timeStamper(toPosition.roundToInt())
                    ), false
                )
                receivedSeek = true
                myExoPlayer?.seekTo((toPosition * 1000.0).toLong())
            } else {
                broadcastMessage(
                    string(
                        rr.string.room_seeked,
                        seeker,
                        timeStamper((seekTracker).roundToInt()),
                        timeStamper(toPosition.roundToInt())
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
        replenishUsers(roomBinding.syncplayOverview)
        broadcastMessage(string(rr.string.room_guy_left, leaver), false)

        /* If the setting is enabled, pause playback **/
        val pauseOnLeft = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean("pause_if_someone_left", true)
        if (pauseOnLeft) {
            pausePlayback()
        }

        /* Rare cases where a user can see his own self disconnected */
        if (leaver == p.currentUsername) {
            p.syncplayBroadcaster?.onDisconnected()
        }
    }

    override fun onSomeoneJoined(joiner: String) {
        replenishUsers(roomBinding.syncplayOverview)
        broadcastMessage(string(rr.string.room_guy_joined, joiner), false)
    }

    override fun onReceivedList() {
        replenishUsers(roomBinding.syncplayOverview)
    }

    override fun onSomeoneLoadedFile(
        person: String,
        file: String?,
        fileduration: String?,
        filesize: String?
    ) {
        replenishUsers(roomBinding.syncplayOverview)
        broadcastMessage(
            string(
                rr.string.room_isplayingfile,
                person,
                file ?: "",
                timeStamper(fileduration?.toDoubleOrNull()?.roundToInt() ?: 0)
            ),
            false
        )
    }

    override fun onDisconnected() {
        broadcastMessage(string(rr.string.room_attempting_reconnection), false)
        disconnectedPopup()
        p.connected = false
        p.connect()
    }

    override fun onJoined() {
        broadcastMessage(string(rr.string.room_you_joined_room, p.currentRoom), false)
    }

    override fun onConnectionFailed() {
        broadcastMessage(string(rr.string.room_connection_failed), false)
        p.connect()
    }

    override fun onReconnected() {
        broadcastMessage(string(rr.string.room_connected_to_server), false)
        replenishUsers(roomBinding.syncplayOverview)
        runOnUiThread {
            disconnectedPopup?.dismiss() /* Dismiss any disconnection popup, if they exist */
        }

        /** Resubmit any ongoing file being played **/
        if (p.file != null) {
            p.sendPacket(sendFile(p.file!!))
        }
    }

    override fun onConnectionAttempt() {
        val server =
            if (p.serverHost == "151.80.32.178") "syncplay.pl" else p.serverHost
        broadcastMessage(
            string(
                rr.string.room_attempting_connect,
                server,
                p.serverPort.toString()
            ), false
        )
    }

    override fun onBackPressed() {
        super.onBackPressed()
        p.removeBroadcaster()
        p.socket.close()
        finish()
    }

    /** Functions to grab a localized string from resources, format it according to arguments **/
    fun string(id: Int, vararg stuff: String): String {
        return String.format(resources.getString(id), *stuff)
    }

    private fun applyLastOverrides() {
        p.file?.analyzeTracks(myExoPlayer!!)

        if (myExoPlayer != null) {
            if (myExoPlayer!!.trackSelector != null) {
                val builder = myExoPlayer!!.trackSelector!!.parameters.buildUpon()

                var newParams = builder.build()

                if (lastAudioOverride != null) {
                    newParams = newParams.buildUpon().addOverride(lastAudioOverride!!).build()
                }
                if (lastSubtitleOverride != null) {
                    newParams = newParams.buildUpon().addOverride(lastSubtitleOverride!!).build()
                }

                myExoPlayer?.trackSelector?.parameters = newParams
            }
        }

    }
}

