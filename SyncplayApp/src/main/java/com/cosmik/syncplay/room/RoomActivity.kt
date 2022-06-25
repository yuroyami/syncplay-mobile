package com.cosmik.syncplay.room

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.util.TypedValue.COMPLEX_UNIT_SP
import android.view.Gravity.END
import android.view.Menu.NONE
import android.view.MenuItem
import android.view.View.*
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.widget.*
import android.widget.LinearLayout.HORIZONTAL
import androidx.annotation.ColorInt
import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources.getDrawable
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.children
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
import com.cosmik.syncplay.databinding.ActivityRoomBinding
import com.cosmik.syncplay.protocol.SPBroadcaster
import com.cosmik.syncplay.protocol.SPWrappers.sendChat
import com.cosmik.syncplay.protocol.SPWrappers.sendFile
import com.cosmik.syncplay.protocol.SPWrappers.sendReadiness
import com.cosmik.syncplay.protocol.SPWrappers.sendState
import com.cosmik.syncplay.protocol.SyncplayProtocol
import com.cosmik.syncplay.toolkit.SyncplayUtils
import com.cosmik.syncplay.toolkit.SyncplayUtils.getFileName
import com.cosmik.syncplay.toolkit.SyncplayUtils.hideSystemUI
import com.cosmik.syncplay.toolkit.SyncplayUtils.timeStamper
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.C.SELECTION_FLAG_DEFAULT
import com.google.android.exoplayer2.C.VIDEO_SCALING_MODE_SCALE_TO_FIT
import com.google.android.exoplayer2.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.CaptionStyleCompat
import com.google.android.exoplayer2.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.common.collect.Lists
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import razerdp.basepopup.BasePopupWindow
import java.io.IOException
import kotlin.collections.set
import kotlin.math.roundToInt
import com.cosmik.syncplay.R as rr

/*----------------------------------------------*/
@SuppressLint("SetTextI18n")
class RoomActivity : AppCompatActivity(), SPBroadcaster {

    /* Declaring our ViewBinding global variables **/
    private var _binding: ActivityRoomBinding? = null
    private val binding get() = _binding!!

    /* This will initialize our protocol the first time it is needed */
    lateinit var protocol: SyncplayProtocol

    /*-- Declaring ExoPlayer-specific variables --*/
    private var myMediaPlayer: ExoPlayer? = null
    private lateinit var trackSelec: DefaultTrackSelector
    private lateinit var paramBuilder: DefaultTrackSelector.ParametersBuilder

    /*-- Declaring Playtracking variables **/
    private var ccTracker: Int = -999
    private var audioTracker: Int = -999
    private var seekTracker: Double = 0.0
    private var receivedSeek = false
    private var updatePosition = false
    private var startFromPosition = (-3.0).toLong()

    /*-- Saving video uri and its sub in a global variable allows us to reload them any time --*/
    private var gottenFile: Uri? = null
    private var gottenSub: MediaItem.SubtitleConfiguration? = null

    /*-- UI-Related --*/
    private var lockedScreen = false
    private var seekButtonEnable: Boolean? = null
    private var cutOutMode: Boolean = true
    private var ccsize = 18f

    /**********************************************************************************************
     *                                        LIFECYCLE METHODS
     *********************************************************************************************/

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityRoomBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        /** Initializing our ViewModel, which is our protocol at the same time **/
        protocol = ViewModelProvider(this)[SyncplayProtocol::class.java]

        /** Extracting joining info from our intent **/
        val ourInfo = intent.getStringExtra("json")
        val joinInfo = GsonBuilder().create().fromJson(ourInfo, List::class.java) as List<*>

        /** Adding the callback interface so we can respond to multiple syncplay events **/
        protocol.addBroadcaster(this)

        /** Storing the join info into the protocol directly **/
        protocol.serverHost = "151.80.32.178"
        protocol.serverPort = (joinInfo[0] as Double).toInt()
        protocol.currentUsername = joinInfo[1] as String
        protocol.currentRoom = joinInfo[2] as String

        /** Storing SharedPreferences to apply some settings **/
        val sp = PreferenceManager.getDefaultSharedPreferences(this)

        /** Pref No.1 : Should the READY button be initially clicked ? **/
        binding.syncplayReady.isChecked = sp.getBoolean("ready_firsthand", true).also {
            protocol.ready = it /* Telling our protocol that we're ready */
        }

        /** Pref No.2 : Rewind threshold **/
        protocol.rewindThreshold = sp.getInt("rewind_threshold", 12).toLong()

        /** Now, let's connect to the server, everything should be ready **/
        //val tls = sp.getBoolean("tls", false) /* We're not using this yet */
        if (!protocol.connected) {
            protocol.connect(
                protocol.serverHost,
                protocol.serverPort
            )
        }

        /** Let's apply Room UI Settings **/
        applyUISettings()

        /** Let's apply Cut-Out Mode on the get-go, user can turn it off later **/
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window?.attributes?.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        /** Launch the visible ping updater **/
        pingStatusUpdater()

        /** Inject preference fragment **/
        supportFragmentManager.beginTransaction()
            .replace(rr.id.pseudo_popup_container, RoomSettingsFragment())
            .commit()
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

        /** TrackSelector building **/
        trackSelec = DefaultTrackSelector(this).also {
            it.parameters =
                DefaultTrackSelector.ParametersBuilder(this)
                    .build()

        }


        /** Now, on to building Exoplayer itself using the components we have **/
        myMediaPlayer = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelec) /* We use the trackselector we initialized before */
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
        myMediaPlayer?.videoScalingMode = VIDEO_SCALING_MODE_SCALE_TO_FIT /* Starter scaling */

        binding.vidplayer.subtitleView?.background = null /* Removing any bg color on subtitles */

        myMediaPlayer?.playWhenReady = true /* Play once the media has been buffered */

        /** This listener is very important, without it, syncplay is nothing but a video player */
        /** TODO: Seperate the interface from the activity **/
        myMediaPlayer?.addListener(object : Player.Listener {
            override fun onIsLoadingChanged(isLoading: Boolean) {
                super.onIsLoadingChanged(isLoading)
                if (!isLoading) {
                    val length = (myMediaPlayer?.duration!!.toDouble()) / 1000.0
                    if (length != protocol.currentVideoLength) {
                        protocol.currentVideoLength =
                            (myMediaPlayer?.duration!!.toDouble()) / 1000.0
                        protocol.sendPacket(
                            sendFile(
                                protocol.currentVideoLength,
                                protocol.currentVideoName,
                                protocol.currentVideoSize
                            ),
                            protocol.serverHost,
                            protocol.serverPort
                        )
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)
                if (myMediaPlayer?.mediaItemCount != 0) {
                    when (isPlaying) {
                        true -> {
                            if (myMediaPlayer?.playbackState != ExoPlayer.STATE_BUFFERING) {
                                sendPlayback(true)
                                protocol.paused = false
                            }
                        }
                        false -> {
                            if (myMediaPlayer?.playbackState != ExoPlayer.STATE_BUFFERING) {
                                sendPlayback(false)
                                protocol.paused = true
                            }
                        }
                    }
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int
            ) {
                super.onPositionDiscontinuity(oldPosition, newPosition, reason)
                if (myMediaPlayer?.mediaItemCount != 0) {
                    seekTracker = oldPosition.positionMs / 1000.0
                    if (reason == 1) {
                        if (!receivedSeek) {
                            val clienttime =
                                (System.currentTimeMillis() / 1000.0)
                            protocol.sendPacket(
                                sendState(
                                    null,
                                    clienttime,
                                    true,
                                    newPosition.positionMs,
                                    1,
                                    play = myMediaPlayer?.isPlaying,
                                    protocol
                                ),
                                protocol.serverHost,
                                protocol.serverPort
                            )
                        } else receivedSeek = false
                    }
                }
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
                    findViewById<ImageButton>(R.id.exo_ffwd).visibility = visib
                    findViewById<ImageButton>(R.id.exo_rew).visibility = visib
                }, 10)


            } else {
                hideSystemUI(this, false)
                binding.syncplayMESSAGERY.also {
                    it.clearAnimation()
                    it.alpha = 0f
                    it.visibility = GONE
                }
                if (!binding.syncplayINPUTBox.hasFocus()) {
                    binding.syncplayVisiblitydelegate.visibility = GONE
                    hideKb()
                }
            }
        }

        /************************
         * Adding a  Video File *
         ************************/
        findViewById<ImageButton>(rr.id.syncplay_addfile).setOnClickListener {
            val intent1 = Intent()
            intent1.type = "*/*"
            intent1.action = Intent.ACTION_OPEN_DOCUMENT
            startActivityForResult(intent1, 90909)
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
            if (msg != "" && msg != " ") {
                sendMessage(msg)
                binding.syncplayINPUT.isErrorEnabled = false
            } else {
                binding.syncplayINPUT.isErrorEnabled = true
                binding.syncplayINPUT.error =
                    getString(com.cosmik.syncplay.R.string.room_empty_message_error)
            }
        }

        /*******************
         * Subtitles track *
         *******************/
        findViewById<ImageButton>(R.id.exo_subtitle).setOnClickListener {
            if (trackSelec.currentMappedTrackInfo != null) {
                ccSelect(it as ImageButton)
            }
        }

        /***************
         * Audio Track *
         ***************/
        findViewById<ImageButton>(R.id.exo_audio_track).setOnClickListener {
            if (trackSelec.currentMappedTrackInfo != null) {
                audioSelect(it as ImageButton)
            }
        }

        /***************
         * Lock Screen *
         ***************/
        findViewById<ImageButton>(rr.id.syncplay_lock).setOnClickListener {
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
        findViewById<ImageButton>(rr.id.syncplay_more).setOnClickListener { overflow ->
            val popup = PopupMenu(this, overflow)

            val loadsubItem =
                popup.menu.add(0, 0, 0, getString(com.cosmik.syncplay.R.string.room_overflow_sub))

            val cutoutItem = popup.menu.add(
                0,
                1,
                1,
                getString(com.cosmik.syncplay.R.string.room_overflow_cutout)
            )
            cutoutItem.isCheckable = true
            cutoutItem.isChecked = cutOutMode
            cutoutItem.isEnabled = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            val seekbuttonsItem =
                popup.menu.add(0, 2, 2, getString(com.cosmik.syncplay.R.string.room_overflow_ff))
            seekbuttonsItem.isCheckable = true
            val ffwdButton = findViewById<ImageButton>(R.id.exo_ffwd)
            val rwndButton = findViewById<ImageButton>(R.id.exo_rew)
            seekbuttonsItem.isChecked = seekButtonEnable != false
            val messagesItem = popup.menu.add(
                0,
                3,
                3,
                getString(com.cosmik.syncplay.R.string.room_overflow_msghistory)
            )
            val uiItem = popup.menu.add(
                0,
                4,
                4,
                getString(com.cosmik.syncplay.R.string.room_overflow_settings)
            )
            //val adjustItem = popup.menu.add(0,5,5,"Change Username or Room")

            popup.setOnMenuItemClickListener {
                when (it) {
                    loadsubItem -> {
                        val intent2 = Intent()
                        intent2.type = "*/*"
                        intent2.action = Intent.ACTION_OPEN_DOCUMENT
                        startActivityForResult(intent2, 80808)
                    }
                    cutoutItem -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            if (!cutOutMode) {
                                cutOutMode = true
                                window?.attributes?.layoutInDisplayCutoutMode =
                                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                            } else {
                                cutOutMode = false
                                window?.attributes?.layoutInDisplayCutoutMode =
                                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
                            }
                            binding.vidplayer.performClick()
                            binding.vidplayer.performClick()
                        }
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
            if (checked) {
                replenishUsers(binding.syncplayOverview)
            } else {
                binding.syncplayOverview.removeAllViews()
            }
        }

        /****************
         * Ready Button *
         ****************/
        findViewById<MaterialCheckBox>(rr.id.syncplay_ready).setOnCheckedChangeListener { _, b ->
            protocol.ready = b
            protocol.sendPacket(sendReadiness(b), protocol.serverHost, protocol.serverPort)
        }

        /*******************
         * Change Fit Mode *
         *******************/
        findViewById<ImageButton>(rr.id.syncplay_screen).setOnClickListener {
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

        /** UI Settings' Popup Click Controllers **/
        binding.popupDismisser.setOnClickListener {
            binding.pseudoPopupParent.visibility = GONE
            applyUISettings()
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI(this, false) //Immersive Mode
        if (gottenFile != null) {
            injectVideo(binding.vidplayer.player as ExoPlayer, gottenFile!!)
            binding.starterInfo.visibility = GONE
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putDouble("last_position", protocol.currentVideoPosition)
    }

    override fun onPause() {
        super.onPause()
        startFromPosition = binding.vidplayer.player?.currentPosition!!
        updatePosition = false
    }

    override fun onStop() {
        super.onStop()

        updatePosition = false
        myMediaPlayer?.stop()
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
        updatePosition = false
        myMediaPlayer?.release()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                90909 -> {
                    gottenFile = data?.data
                    val filename = gottenFile?.let { getFileName(it) }
                    Toast.makeText(this, "Selected video file: $filename", Toast.LENGTH_LONG).show()

                }
                80808 -> {
                    if (gottenFile != null) {
                        val path = data?.data!!
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
                            gottenSub = MediaItem.SubtitleConfiguration.Builder(path)
                                .setUri(path)
                                .setMimeType(mimeType)
                                .setLanguage(null)
                                .setSelectionFlags(SELECTION_FLAG_DEFAULT)
                                .build()
                            Toast.makeText(
                                this, "Loaded sub successfully: $filename", Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(
                                this,
                                "Invalid subtitle file. Supported formats are: 'SRT', 'TTML', 'ASS', 'SSA', 'VTT'",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        Toast.makeText(this, "Load video first", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    /*********************************************************************************************
     *                                     CUSTOM FUNCTIONS                                      *
     ********************************************************************************************/
    private fun injectVideo(mp: ExoPlayer, mediaPath: Uri) {
        try {
            //val vid: MediaItem = MediaItem.fromUri(mediaPath)
            val vidbuilder = MediaItem.Builder()
            if (gottenSub != null) {
                runOnUiThread {
                    findViewById<ImageButton>(R.id.exo_subtitle).setImageDrawable(
                        getDrawable(
                            this,
                            rr.drawable.ic_subtitles
                        )
                    )
                }
                vidbuilder.setUri(mediaPath)
                    .setSubtitleConfigurations(Lists.newArrayList(gottenSub!!))
            } else {
                vidbuilder.setUri(mediaPath)
            }
            val vid = vidbuilder.build()
            //val file = File(mediaPath.path.toString())
            runOnUiThread {
                mp.setMediaItem(vid)
                findViewById<ImageButton>(R.id.exo_play).performClick()
                findViewById<ImageButton>(R.id.exo_pause).performClick()
                val vidtitle = getFileName(mediaPath)!!
                protocol.currentVideoName = vidtitle
                protocol.currentVideoPosition = 0.0
                protocol.currentVideoSize =
                    SyncplayUtils.getRealSizeFromUri(this, mediaPath)?.toDouble()
                        ?.roundToInt()!!
                if (startFromPosition != (-3.0).toLong()) mp.seekTo(startFromPosition)
            }

            if (!protocol.connected) {
                protocol.sendPacket(
                    sendFile(
                        protocol.currentVideoLength,
                        protocol.currentVideoName,
                        protocol.currentVideoSize
                    ),
                    protocol.serverHost,
                    protocol.serverPort
                )
            }
            updatePosition = true
            casualUpdater() //Most important updater to maintain continuity
        } catch (e: IOException) {
            throw RuntimeException("Invalid asset folder")
        }
    }
    private fun pausePlayback(mp: ExoPlayer) {
        runOnUiThread {
            mp.pause()
        }
    }
    private fun playPlayback(mp: ExoPlayer) {
        runOnUiThread {
            mp.play()
        }
        hideSystemUI(this, false)
    }
    private fun sendPlayback(play: Boolean) {
        val clienttime = System.currentTimeMillis() / 1000.0
        protocol.sendPacket(
            sendState(null, clienttime, null, 0, 1, play, protocol),
            protocol.serverHost,
            protocol.serverPort
        )
    }
    private fun sendMessage(message: String) {
        hideKb()
        if (binding.syncplayMESSAGERY.visibility != VISIBLE) {
            binding.syncplayVisiblitydelegate.visibility = GONE
        }
        protocol.sendPacket(
            sendChat(message),
            protocol.serverHost,
            protocol.serverPort
        )
        binding.syncplayINPUTBox.setText("")
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

            val msgs = protocol.messageSequence.takeLast(maxMsgsCount)

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
                val alpha = PreferenceManager.getDefaultSharedPreferences(this@RoomActivity)
                    .getInt("messagery_alpha", 0) //between 0-255
                @ColorInt val alphaColor = ColorUtils.setAlphaComponent(Color.DKGRAY, alpha)
                txtview.setBackgroundColor(alphaColor)

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
                binding.syncplayMESSAGERY.also {
                    it.clearAnimation()
                    it.visibility = VISIBLE
                    it.alpha = 1f
                }
                if (!binding.vidplayer.isControllerVisible) {
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
            if (binding.syncplayOverviewcheckbox.isChecked) {
                linearLayout.removeAllViews()
                val userList: MutableMap<String, MutableList<String>> = protocol.userList

                //Creating line for room-name:
                val roomnameView = TextView(this)
                roomnameView.text =
                    string(rr.string.room_details_current_room, protocol.currentRoom)
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
                binding.syncplayOverview.addView(linearlayout0, linearlayoutParams0)

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
                    binding.syncplayOverview.addView(linearlayout, linearlayoutParams)

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
                    binding.syncplayOverview.addView(linearlayout2, linearlayoutParams2)

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
                        binding.syncplayOverview.addView(linearlayout3, linearlayoutParams3)
                    }


                }

            }
        }
    }

    private fun ccSelect(ccButton: ImageButton) {
        val mappedTrackInfo =
            trackSelec.currentMappedTrackInfo!! //get Tracks from our default (already injected) selector
        var rendererIndex = 0
        for (i in 0 until mappedTrackInfo.rendererCount) {
            val trackgroups = mappedTrackInfo.getTrackGroups(i)
            if (trackgroups.length != 0) {
                when (myMediaPlayer?.getRendererType(i)) {
                    C.TRACK_TYPE_TEXT -> rendererIndex = i
                }
            }
        }
        if (rendererIndex == 0) {
            displayInfo(getString(rr.string.room_sub_track_notfound))
            runOnUiThread {
                ccButton.setImageDrawable(getDrawable(this, rr.drawable.ic_subtitles_off))
            }
        } else {
            //Get Tracks
            val cctrackgroups = mappedTrackInfo.getTrackGroups(rendererIndex)
            val ccmap: MutableMap<Int, Format> = mutableMapOf()
            for (index in 0 until cctrackgroups.length) {
                ccmap[index] = cctrackgroups.get(index).getFormat(0)
            }

            val popup = PopupMenu(this, ccButton)
            popup.menu.add(0, -3, 0, getString(rr.string.room_sub_track_disable))
            for (cc in ccmap) {
                var name = cc.value.label
                if (cc.value.label == null) {
                    name = getString(rr.string.room_track_track)
                }
                val item = popup.menu.add(
                    0,
                    cc.key,
                    NONE,
                    "$name [${(cc.value.language).toString().uppercase()}]"
                )
                item.isCheckable = true
                if (ccTracker == -999) {
                    if (cc.value.selectionFlags == 1) {
                        item.isChecked = true
                    }
                } else {
                    if (cc.key == ccTracker) {
                        item.isChecked = true
                    }
                }
            }
            popup.setOnMenuItemClickListener { menuItem: MenuItem ->
                paramBuilder = trackSelec.parameters.buildUpon().also {
                    if (menuItem.itemId != -3) {
                        val override: DefaultTrackSelector.SelectionOverride =
                            DefaultTrackSelector.SelectionOverride(menuItem.itemId, 0)
                        it.setSelectionOverride(
                            rendererIndex,
                            trackSelec.currentMappedTrackInfo!!.getTrackGroups(rendererIndex),
                            override
                        )
                    } else {
                        it.clearSelectionOverride(
                            rendererIndex,
                            trackSelec.currentMappedTrackInfo!!.getTrackGroups(rendererIndex)
                        )
                    }
                }
                trackSelec.setParameters(paramBuilder)
                ccTracker = menuItem.itemId
                displayInfo(string(rr.string.room_sub_track_changed, menuItem.title.toString()))

                return@setOnMenuItemClickListener true
            }
            popup.setOnDismissListener {
                // Respond to popup being dismissed.
            }
            // Show the popup menu.
            popup.show()

        }
    }
    private fun audioSelect(audioButton: ImageButton) {
        val mappedTrackInfo =
            trackSelec.currentMappedTrackInfo!! //get Tracks from our default (already injected) selector

        /** Now, we try to determine the audio renderer index (to use when we override tracks...etc) **/
        var rendererIndex = 0
        for (i in 0 until mappedTrackInfo.rendererCount) {
            val trackgroups = mappedTrackInfo.getTrackGroups(i)
            if (trackgroups.length != 0) {
                when (myMediaPlayer?.getRendererType(i)) {
                    C.TRACK_TYPE_AUDIO -> rendererIndex = i
                }
            }
        }
        /** Normally, if there is any audio renderer found, it should be something different than 0 **/
        if (rendererIndex == 0) {
            displayInfo(getString(rr.string.room_audio_track_not_found)) /* Otherwise, no audio track found */
        } else {
            /* If the renderer index is greater than 0, then we've got some audio tracks, at least 1 */
            val audiotrackgroups =
                mappedTrackInfo.getTrackGroups(rendererIndex) /* Getting all audio tracks */
            val audiomap: MutableMap<Int, Format> =
                mutableMapOf() /* Creating a map variable for tracks */

            for (index in 0 until audiotrackgroups.length) {
                audiomap[index] = audiotrackgroups.get(index).getFormat(0) /* Populating the map */
            }


            val popup =
                PopupMenu(this, audioButton) /* Creating a popup menu, anchored on Audio Button */

            /** Going through the entire audio track group, and populating the popup menu with each one of them **/
            for (audio in audiomap) {
                /* Choosing a name for the audio track, a format's label is a good choice */
                val name = if (audio.value.label == null)
                    getString(rr.string.room_track_track) else audio.value.label

                /* Now creating the popup menu item corresponding to the audio track */
                val item = popup.menu.add(
                    0,
                    audio.key,
                    0,
                    "$name [${(audio.value.language).toString().uppercase()}]"
                )

                /* Making the popup menu item checkable */
                item.isCheckable = true

                /* Now to see whether it should be checked or not (whether it's selected/forced/default) */
                if (audioTracker == -999) {
                    if (audio.value.selectionFlags == C.SELECTION_FLAG_DEFAULT) {
                        item.isChecked = true
                    }
                } else {
                    if (audio.key == audioTracker) {
                        item.isChecked = true
                    }
                }
            }
            popup.setOnMenuItemClickListener { menuItem: MenuItem ->
                paramBuilder = trackSelec.parameters.buildUpon().also {
                    val override: DefaultTrackSelector.SelectionOverride =
                        DefaultTrackSelector.SelectionOverride(menuItem.itemId, 0)
                    it.setSelectionOverride(
                        rendererIndex,
                        trackSelec.currentMappedTrackInfo!!.getTrackGroups(rendererIndex),
                        override
                    )
                }
                trackSelec.setParameters(paramBuilder)
                audioTracker = menuItem.itemId
                displayInfo(string(rr.string.room_audio_track_changed, menuItem.title.toString()))
                return@setOnMenuItemClickListener true
            }

            // Show the popup menu.
            popup.show()
        }
    }

    private fun selectAudioTrack(id: Int) {

    }

    private fun selectCCTrack(id: Int) {

    }

    private fun hideKb() {
        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.ime())
        binding.syncplayINPUTBox.clearFocus()
    }

    private fun casualUpdater() {
        //TODO: Change periodic sleep to periodic handler.
        GlobalScope.launch(Dispatchers.Unconfined) {
            while (true) {
                if (updatePosition) {
                    /* Informing my ViewModel about current vid position so it is retrieved for networking after */
                    runOnUiThread {
                        val progress = (binding.vidplayer.player?.currentPosition?.div(1000.0))
                        if (progress != null) {
                            protocol.currentVideoPosition = progress
                        }
                    }
                }
                delay(75)
            }
        }
    }

    private fun pingStatusUpdater() {
        GlobalScope.launch(Dispatchers.IO) {
            while (true) {
                /* Informing my ViewModel about current vid position so it is retrieved for networking after */
                if (protocol.socket.isConnected && !protocol.socket.isClosed) {
                    val ping = SyncplayUtils.pingIcmp("151.80.32.178", 32) * 1000.0
                    GlobalScope.launch(Dispatchers.Main) {
                        binding.syncplayConnectionInfo.text =
                            string(rr.string.room_ping_connected, "$ping")
                        when (ping) {
                            in (0.0..100.0) -> binding.syncplaySignalIcon.setImageResource(rr.drawable.ping_3)
                            in (100.0..200.0) -> binding.syncplaySignalIcon.setImageResource(rr.drawable.ping_2)
                            else -> binding.syncplaySignalIcon.setImageResource(rr.drawable.ping_1)
                        }
                    }
                } else {
                    GlobalScope.launch(Dispatchers.Main) {
                        binding.syncplayConnectionInfo.text =
                            string(rr.string.room_ping_disconnected)
                        binding.syncplaySignalIcon.setImageDrawable(
                            getDrawable(
                                this@RoomActivity,
                                rr.drawable.ic_unconnected
                            )
                        )
                    }
                }
                delay(1000)

            }
        }
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
        if (chatter.lowercase() == protocol.currentUsername.lowercase()) {
            msg.isMainUser = true
        }

        /** Assigning the message content to the message **/
        msg.content =
            message /* Assigning the message content to the variable inside our instance */

        /** Adding the message instance to our message sequence **/
        protocol.messageSequence.add(msg)

        /** Refresh views **/
        replenishMsgs(binding.syncplayMESSAGERY)
    }

    private fun displayInfo(msg: String) {
        binding.syncplayInfoDelegate.clearAnimation()
        binding.syncplayInfoDelegate.text = msg
        binding.syncplayInfoDelegate.alpha = 1f
        binding.syncplayInfoDelegate.animate()
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
                val msgs = protocol.messageSequence
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

    private fun disconnectedPopup() {
        class DisconnectedPopup(context: Context) : BasePopupWindow(context) {
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

        runOnUiThread {
            DisconnectedPopup(this).setBlurBackgroundEnable(true).showPopupWindow()
        }
    }

    fun applyUISettings() {
        /* For settings: Timestamp,Message Count,Message Font Size, Messages alpha */
        replenishMsgs(binding.syncplayMESSAGERY)

        /* Holding a reference to SharedPreferences to use it later */
        val sp = PreferenceManager.getDefaultSharedPreferences(this)

        /* Applying "overview_alpha" setting */
        val alpha = sp.getInt("overview_alpha", 30) //between 0-255
        @ColorInt val alphaColor = ColorUtils.setAlphaComponent(Color.DKGRAY, alpha)
        binding.syncplayOverviewCard.setBackgroundColor(alphaColor)

        /* Applying Subtitle Size setting */
        ccsize = sp.getInt("subtitle_size", 18).toFloat()
    }


    /*********************************************************************************************
     *                                        CALLBACKS                                          *
     ********************************************************************************************/

    override fun onSomeonePaused(pauser: String) {
        if (pauser != protocol.currentUsername) myMediaPlayer?.let { pausePlayback(it) }
        broadcastMessage(string(rr.string.room_guy_paused, pauser), false)
    }

    override fun onSomeonePlayed(player: String) {
        if (player != protocol.currentUsername) myMediaPlayer?.let { playPlayback(it) }
        broadcastMessage(string(rr.string.room_guy_played, player), false)

    }

    override fun onChatReceived(chatter: String, chatmessage: String) {
        broadcastMessage(chatmessage, true, chatter)
    }

    override fun onSomeoneSeeked(seeker: String, toPosition: Double) {
        runOnUiThread {
            if (seeker != protocol.currentUsername) {
                broadcastMessage(
                    string(
                        rr.string.room_seeked,
                        seeker,
                        timeStamper((protocol.currentVideoPosition).roundToInt()),
                        timeStamper(toPosition.roundToInt())
                    ), false
                )
                receivedSeek = true
                myMediaPlayer?.seekTo((toPosition * 1000.0).toLong())
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
            myMediaPlayer?.seekTo((toPosition * 1000.0).toLong())
        }
        broadcastMessage(string(rr.string.room_rewinded, behinder), false)
    }

    override fun onSomeoneLeft(leaver: String) {
        replenishUsers(binding.syncplayOverview)
        broadcastMessage(string(rr.string.room_guy_left, leaver), false)
    }

    override fun onSomeoneJoined(joiner: String) {
        replenishUsers(binding.syncplayOverview)
        broadcastMessage(string(rr.string.room_guy_joined, joiner), false)
    }

    override fun onReceivedList() {
        replenishUsers(binding.syncplayOverview)
    }

    override fun onSomeoneLoadedFile(
        person: String,
        file: String,
        fileduration: String,
        filesize: String
    ) {
        replenishUsers(binding.syncplayOverview)
        broadcastMessage(
            string(
                rr.string.room_isplayingfile,
                person,
                file,
                timeStamper(fileduration.toDouble().roundToInt())
            ),
            false
        )
    }

    override fun onDisconnected() {
        broadcastMessage(string(rr.string.room_attempting_reconnection), false)
        disconnectedPopup()
        protocol.connected = false
        protocol.connect(
            protocol.serverHost,
            protocol.serverPort
        )
    }

    override fun onJoined() {
        broadcastMessage(string(rr.string.room_you_joined_room, protocol.currentRoom), false)
    }

    override fun onConnectionFailed() {
        broadcastMessage(string(rr.string.room_connection_failed), false)
        protocol.connect(
            protocol.serverHost,
            protocol.serverPort
        )
    }

    override fun onReconnected() {
        broadcastMessage(string(rr.string.room_connected_to_server), false)
        replenishUsers(binding.syncplayOverview)
    }

    override fun onConnectionAttempt(port: String) {
        broadcastMessage(string(rr.string.room_attempting_connect, port), false)

    }

    override fun onBackPressed() {
        protocol.socket.close()
        finishAffinity()
        finish()
        finishAndRemoveTask()
        super.onBackPressed()
    }


    /** Functions to grab a localized string from resources, format it according to arguments **/
    fun string(id: Int, vararg stuff: String): String {
        return String.format(resources.getString(id), *stuff)
    }

}

