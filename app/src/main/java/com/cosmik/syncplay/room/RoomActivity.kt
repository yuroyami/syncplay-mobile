package com.cosmik.syncplay.room

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.util.Log
import android.util.TypedValue.COMPLEX_UNIT_SP
import android.view.Gravity.END
import android.view.Menu.NONE
import android.view.MenuItem
import android.view.View
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
import com.cosmik.syncplay.protocol.SyncplayBroadcaster
import com.cosmik.syncplay.protocol.SyncplayProtocol
import com.cosmik.syncplay.protocol.SyncplayProtocolUtils.sendChat
import com.cosmik.syncplay.protocol.SyncplayProtocolUtils.sendFile
import com.cosmik.syncplay.protocol.SyncplayProtocolUtils.sendReadiness
import com.cosmik.syncplay.protocol.SyncplayProtocolUtils.sendState
import com.cosmik.syncplay.toolkit.SyncplayUtils
import com.cosmik.syncplay.toolkit.SyncplayUtils.generateTimestamp
import com.cosmik.syncplay.toolkit.SyncplayUtils.getFileName
import com.cosmik.syncplay.toolkit.SyncplayUtils.hideSystemUI
import com.cosmik.syncplay.toolkit.SyncplayUtils.timeStamper
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.C.SELECTION_FLAG_DEFAULT
import com.google.android.exoplayer2.C.VIDEO_SCALING_MODE_SCALE_TO_FIT
import com.google.android.exoplayer2.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
import com.google.android.exoplayer2.ext.ffmpeg.FfmpegLibrary
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.CaptionStyleCompat
import com.google.android.exoplayer2.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.common.collect.Lists
import com.google.gson.GsonBuilder
import kotlinx.coroutines.*
import razerdp.basepopup.BasePopupWindow
import java.io.IOException
import kotlin.collections.set
import kotlin.math.roundToInt
import com.cosmik.syncplay.R as rr

/*----------------------------------------------*/
@OptIn(DelicateCoroutinesApi::class)
@SuppressLint("SetTextI18n")
class RoomActivity : AppCompatActivity(), SyncplayBroadcaster {

    private var _binding: ActivityRoomBinding? = null
    private val binding get() = _binding!!
    lateinit var protocol: SyncplayProtocol

    /*-- Initializing Player Settings --*/
    private lateinit var trackSelec: DefaultTrackSelector
    private lateinit var paramBuilder: DefaultTrackSelector.ParametersBuilder
    private var ccTracker: Int = -1
    private var audioTracker: Int = -1
    private var seekTracker: Double = 0.0
    private var receivedSeek = false
    /*-- Initializing Player embeddable parts --*/
    private var myMediaPlayer: ExoPlayer? = null
    private var updatePosition = false
    private var startFromPosition = (-3.0).toLong()
    /*-- Initialize video path from intent --*/
    private var gottenFile: Uri? = null
    private var gottenSub: MediaItem.SubtitleConfiguration? = null

    /*-- Cosmetics --*/
    private var lockedScreen = false
    private var seekButtonEnable: Boolean? = null
    private var cutOutMode: Boolean = true
    /*----------------------------------------------*/
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityRoomBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        //Preparing UI Settings fragment
        protocol = ViewModelProvider(this)[SyncplayProtocol::class.java]


        //Getting all info from intent
        val ourInfo = intent.getStringExtra("json")
        val joinInfo: List<*> =
            GsonBuilder().create().fromJson(ourInfo, List::class.java) as List<*>
        //Creating all infrastructure roomviewmodel variables.
        protocol.addBroadcaster(this)
        protocol.serverHost = "151.80.32.178"
        protocol.serverPort = (joinInfo[0] as Double).toInt()
        protocol.currentUsername = joinInfo[1] as String
        protocol.currentRoom = joinInfo[2] as String
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        findViewById<MaterialCheckBox>(rr.id.syncplay_ready).isChecked =
            sp.getBoolean("ready_firsthand", true)
        protocol.ready = sp.getBoolean("ready_firsthand", true)
        protocol.rewindThreshold = sp.getInt("rewind_threshold", 12).toLong()

        //Connecting to server
        val tls = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("tls", false)
        if (!protocol.connected) {
            protocol.connect(
                protocol.serverHost,
                protocol.serverPort
            )
        }

        //Launch the ping updater
        pingStatusUpdater()

        //Apply UI Settings from SharedPreferences
        applyUISettings()

        //Controlling Cut-out mode and orientation.
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window?.attributes?.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }
    override fun onStart() {
        super.onStart()
        //Initializing player with default settings.
        val useCustomBuffer = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean("use_custom_buffer_boolean", false)
        val maxBuffer = if (useCustomBuffer) (PreferenceManager.getDefaultSharedPreferences(this)
            .getInt("player_max_buffer", 50)) * 1000 else 50000
        var minBuffer = if (useCustomBuffer) (PreferenceManager.getDefaultSharedPreferences(this)
            .getInt("player_min_buffer", 15)) * 1000 else 15000
        val playbackBuffer =
            if (useCustomBuffer) (PreferenceManager.getDefaultSharedPreferences(this)
                .getInt("player_playback_buffer", 2500)) else 2000
        if (minBuffer < (playbackBuffer + 500)) minBuffer = playbackBuffer + 500
        val loadControl =
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(minBuffer, maxBuffer, playbackBuffer, playbackBuffer + 500)
                .build()

        trackSelec = DefaultTrackSelector(this).also {
            it.parameters =
                DefaultTrackSelector.ParametersBuilder(this)
                    .build()
        }

        myMediaPlayer = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelec)
            .setLoadControl(loadControl)
            .setRenderersFactory(
                DefaultRenderersFactory(this).setExtensionRendererMode(
                    EXTENSION_RENDERER_MODE_PREFER
                )
            ) //Activating FFmpeg-audio
            .build()
            .also { exoPlayer ->
                binding.vidplayer.player = exoPlayer
            }
        myMediaPlayer?.videoScalingMode = VIDEO_SCALING_MODE_SCALE_TO_FIT
        binding.vidplayer.subtitleView?.background = null
        myMediaPlayer?.playWhenReady = true
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
        val captionStyle = CaptionStyleCompat(
            Color.WHITE,
            Color.TRANSPARENT,
            Color.TRANSPARENT,
            EDGE_TYPE_DROP_SHADOW,
            Color.BLACK,
            Typeface.DEFAULT_BOLD
        )
        val ccsize = PreferenceManager.getDefaultSharedPreferences(this).getInt("subtitle_size", 18)
            .toFloat()
        binding.vidplayer.subtitleView?.also {
            it.setStyle(captionStyle)
            it.setFixedTextSize(COMPLEX_UNIT_SP, ccsize)
        }

        binding.vidplayer.setOnClickListener {
            hideKb()
            binding.syncplayINPUTBox.clearFocus()
        }
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
                SyncplayUtils.hideSystemUI(this, false)
                binding.syncplayMESSAGERY.also {
                    it.clearAnimation()
                    it.alpha = 0f
                    it.visibility = GONE
                }
                val isTyping = binding.syncplayINPUTBox.hasFocus()
                if (!isTyping) {
                    binding.syncplayVisiblitydelegate.visibility = GONE
                    hideKb()
                }
            }
        }

        Log.e("FFMPEG", "${FfmpegLibrary.isAvailable()}")

        implementClickListeners() //Implementing click reactors


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
        Log.e("STATUS", "SAVED INSTANCE: ${protocol.currentVideoPosition}")
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
        Log.e("STATUS", "DESTROYED")
        updatePosition = false
        myMediaPlayer?.release()
    }

    /*----------------------------------------------*/
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
    private fun implementClickListeners() {

        /******************
         * Add Video File *
         ******************/
        findViewById<ImageButton>(rr.id.syncplay_addfile).setOnClickListener {
            val intent1 = Intent()
            intent1.type = "*/*"
            intent1.action = Intent.ACTION_OPEN_DOCUMENT
            startActivityForResult(intent1, 90909)
        }


        /**************************
         * Message Focus Listener *
         **************************/
        binding.syncplayINPUTBox.onFocusChangeListener = OnFocusChangeListener { _, hasFocus ->
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
                binding.syncplayINPUT.error = "Type something!"
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
        /****************
         * OverFlow Menu *
         *****************/
        findViewById<ImageButton>(rr.id.syncplay_more).setOnClickListener { overflow ->
            val popup = PopupMenu(this, overflow)

            val loadsubItem = popup.menu.add(0, 0, 0, "Load Subtitle File...")

            val cutoutItem = popup.menu.add(0, 1, 1, "Cut-out (Notch) Mode")
            cutoutItem.isCheckable = true
            cutoutItem.isChecked = cutOutMode
            cutoutItem.isEnabled = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            val seekbuttonsItem = popup.menu.add(0, 2, 2, "Fast Seek Buttons")
            seekbuttonsItem.isCheckable = true
            val ffwdButton = findViewById<ImageButton>(R.id.exo_ffwd)
            val rwndButton = findViewById<ImageButton>(R.id.exo_rew)
            seekbuttonsItem.isChecked = seekButtonEnable != false
            val messagesItem = popup.menu.add(0, 3, 3, "Messages History")
            val uiItem = popup.menu.add(0, 4, 4, "Settings")
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
                        binding.pseudoPopupParent.visibility = View.VISIBLE

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
            if (b) {
                protocol.sendPacket(
                    sendReadiness(true),
                    protocol.serverHost, protocol.serverPort
                )
            } else {
                protocol.sendPacket(
                    sendReadiness(false),
                    protocol.serverHost, protocol.serverPort
                )

            }
        }

        /*******************
         * Change Fit Mode *
         *******************/
        findViewById<ImageButton>(rr.id.syncplay_screen).setOnClickListener {
            val currentresolution = binding.vidplayer.resizeMode
            val resolutions = mutableMapOf<Int, String>()
            resolutions[AspectRatioFrameLayout.RESIZE_MODE_FIT] = "Resize Mode: FIT TO SCREEN"
            resolutions[AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH] = "Resize Mode: FIXED WIDTH"
            resolutions[AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT] =
                "Resize Mode: FIXED HEIGHT"
            resolutions[AspectRatioFrameLayout.RESIZE_MODE_FILL] = "Resize Mode: FILL SCREEN"
            resolutions[AspectRatioFrameLayout.RESIZE_MODE_ZOOM] = "Resize Mode: Zoom"

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

    /* UI-Related Functions */
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
        SyncplayUtils.hideSystemUI(this, false)
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
                    txtview.text = Html.fromHtml(message.factorize(isTimestampEnabled))
                } else {
                    txtview.text = Html.fromHtml(
                        message.factorize(isTimestampEnabled),
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
                roomnameView.text = "Current Room : ${protocol.currentRoom}"

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
                        if (isThereFile) userList[user]?.get(2) else "(No file being played)"

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
                        val fileInfoLine =
                            "Duration: ${
                                timeStamper(
                                    userList[user]?.get(3)?.toDouble()?.roundToInt()!!
                                )
                            } - Size: $filesizegetter MBs"

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
    /*--------------------------------------------------------*/
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
            displayInfo("No subtitles found !")
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
            popup.menu.add(0, -3, 0, "Disable Subtitles")
            for (cc in ccmap) {
                var name = cc.value.label
                if (cc.value.label == null) {
                    name = "Track"
                }
                val item = popup.menu.add(
                    0,
                    cc.key,
                    NONE,
                    "$name [${(cc.value.language).toString().uppercase()}]"
                )
                item.isCheckable = true
                if (ccTracker == -1) {
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
                displayInfo("Subtitle Track changed to: ${menuItem.title}")
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
        var rendererIndex = 0
        for (i in 0 until mappedTrackInfo.rendererCount) {
            val trackgroups = mappedTrackInfo.getTrackGroups(i)
            if (trackgroups.length != 0) {
                when (myMediaPlayer?.getRendererType(i)) {
                    C.TRACK_TYPE_AUDIO -> rendererIndex = i
                }
            }
        }
        if (rendererIndex == 0) {
            displayInfo("No audio found !")
        } else {
            //Get Tracks
            val audiotrackgroups = mappedTrackInfo.getTrackGroups(rendererIndex)
            val audiomap: MutableMap<Int, Format> = mutableMapOf()
            for (index in 0 until audiotrackgroups.length) {
                audiomap[index] = audiotrackgroups.get(index).getFormat(0)
            }

            val popup = PopupMenu(this, audioButton)
            for (audio in audiomap) {
                var name = audio.value.label
                if (audio.value.label == null) {
                    name = "Track"
                }
                val item = popup.menu.add(
                    0,
                    audio.key,
                    0,
                    "$name [${(audio.value.language).toString().uppercase()}]"
                )
                item.isCheckable = true
                if (audioTracker == -1) {
                    if (audio.value.selectionFlags == 1) {
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
                displayInfo("Audio Track changed to: ${menuItem.title}")
                return@setOnMenuItemClickListener true
            }
            popup.setOnDismissListener {
                // Respond to popup being dismissed.
            }
            // Show the popup menu.
            popup.show()
        }
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
                        binding.syncplayConnectionInfo.text = "CONNECTED - Ping: $ping ms"
                        when (ping) {
                            in (0.0..100.0) -> binding.syncplaySignalIcon.setImageResource(rr.drawable.ping_3)
                            in (100.0..200.0) -> binding.syncplaySignalIcon.setImageResource(rr.drawable.ping_2)
                            else -> binding.syncplaySignalIcon.setImageResource(rr.drawable.ping_1)
                        }
                    }
                } else {
                    GlobalScope.launch(Dispatchers.Main) {
                        binding.syncplayConnectionInfo.text = "DISCONNECTED "
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
        val msg = Message() /* Creating our custom message instance */
        msg.content =
            message /* Assigning the message content to the variable inside our instance */
        msg.timestamp = generateTimestamp()
        msg.timestampStylized = "<font color=\"#aa666666\">[${msg.timestamp}] </font>"

        msg.stylizedContent = if (isChat) {
            msg.sender = chatter
            val selfColorCode = "#ff2d2d";
            val friendColorCode = "#6082B6"
            if (chatter.lowercase() == protocol.currentUsername.lowercase()) {
                val username =
                    "<font color=\"${selfColorCode}\"><strong><bold> $chatter:</bold></strong></font>"
                "$username<font color=\"#ffffff\"><bold> $message</bold></font>"
            } else {
                val username =
                    "<font color=\"${friendColorCode}\"><strong><bold> $chatter:</bold></strong></font>"
                "$username<font color=\"#ffffff\"><bold> $message</bold></font>"
            }
        } else {
            "<font color=\"#eeeee1\"><bold>$message</bold></font>"
        }
        protocol.messageSequence.add(msg)

        replenishMsgs(binding.syncplayMESSAGERY)
    }

    private fun displayInfo(msg: String) {
        val info = binding.syncplayInfoDelegate
        info.clearAnimation()
        info.text = msg
        info.alpha = 1f
        info.animate()
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
                            message.stylizedContent,
                            Html.FROM_HTML_MODE_LEGACY
                        ) else Html.fromHtml(
                        message.stylizedContent
                    )
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
        /* Applying 'Timestamp', 'Message Count', 'Message Font Size' and 'Messages alpha' settings altogether.*/
        replenishMsgs(binding.syncplayMESSAGERY)

        /* Applying 'Room Details Alpha' setting */
        val alpha = PreferenceManager.getDefaultSharedPreferences(this)
            .getInt("overview_alpha", 30) //between 0-255
        @ColorInt val alphaColor = ColorUtils.setAlphaComponent(Color.DKGRAY, alpha)
        binding.syncplayOverviewCard.setBackgroundColor(alphaColor)
    }

    /*********************************************************************************************
     *                                        CALLBACKS                                          *
     ********************************************************************************************/
    override fun onSomeonePaused(pauser: String) {
        if (pauser != protocol.currentUsername) myMediaPlayer?.let { pausePlayback(it) }
        broadcastMessage("$pauser paused", false)
    }

    override fun onSomeonePlayed(player: String) {
        if (player != protocol.currentUsername) myMediaPlayer?.let { playPlayback(it) }
        broadcastMessage("$player unpaused", false)
    }

    override fun onChatReceived(chatter: String, chatmessage: String) {
        broadcastMessage(chatmessage, true, chatter)
    }

    override fun onSomeoneSeeked(seeker: String, toPosition: Double) {
        runOnUiThread {
            if (seeker != protocol.currentUsername) {
                broadcastMessage(
                    "$seeker jumped from ${timeStamper((protocol.currentVideoPosition).roundToInt())} to ${
                        timeStamper(
                            toPosition.roundToInt()
                        )
                    }", false
                )
                receivedSeek = true
                myMediaPlayer?.seekTo((toPosition * 1000.0).toLong())
            } else {
                broadcastMessage(
                    "$seeker jumped from ${timeStamper((seekTracker).roundToInt())} to ${
                        timeStamper(
                            toPosition.roundToInt()
                        )
                    }", false
                )
            }
        }

    }

    override fun onSomeoneBehind(behinder: String, toPosition: Double) {
        runOnUiThread {
            myMediaPlayer?.seekTo((toPosition * 1000.0).toLong())
        }
        broadcastMessage("Rewinded due to time difference with $behinder", false)
    }

    override fun onSomeoneLeft(leaver: String) {
        replenishUsers(binding.syncplayOverview)
        broadcastMessage("$leaver left the room.", false)
    }

    override fun onSomeoneJoined(joiner: String) {
        replenishUsers(binding.syncplayOverview)
        broadcastMessage("$joiner joined the room.", false)
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
            "$person is playing '$file' (${timeStamper(fileduration.toDouble().roundToInt())})",
            false
        )
    }

    override fun onDisconnected() {
        broadcastMessage("Lost connection to the server. Attempting to reconnect...", false)
        disconnectedPopup()
        protocol.connected = false
        protocol.connect(
            protocol.serverHost,
            protocol.serverPort
        )
    }

    override fun onJoined() {
        broadcastMessage("You have joined the room: ${protocol.currentRoom}", false)
    }

    override fun onConnectionFailed() {
        broadcastMessage("Connecting failed.", false)
        protocol.connect(
            protocol.serverHost,
            protocol.serverPort
        )
    }

    override fun onReconnected() {
        broadcastMessage("Connected to the server.", false)
        replenishUsers(binding.syncplayOverview)
    }

    override fun onConnectionAttempt(port: String) {
        broadcastMessage("Attempting to connect to syncplay.pl:${port}", false)

    }

    override fun onBackPressed() {
        protocol.socket.close()
        finishAffinity()
        finish()
        finishAndRemoveTask()
        super.onBackPressed()
    }
}

