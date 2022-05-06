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
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources.getDrawable
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.children
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.cosmik.syncplay.databinding.RoomActivityBinding
import com.cosmik.syncplay.toolkit.AltStorageHandler
import com.cosmik.syncplay.toolkit.DpToPx
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.C.SELECTION_FLAG_DEFAULT
import com.google.android.exoplayer2.C.VIDEO_SCALING_MODE_SCALE_TO_FIT
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
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.sql.Timestamp
import kotlin.collections.set
import kotlin.math.roundToInt
import com.cosmik.syncplay.R as rr


/*----------------------------------------------*/
@OptIn(DelicateCoroutinesApi::class)
class RoomActivity : AppCompatActivity(), UserInteractionDelegate.UserInteractionListener, ClientDelegate.SyncplayBroadcaster {

    private var _binding: RoomActivityBinding? = null
    private val binding get() = _binding!!
    lateinit var roomViewModel: RoomViewModel

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
    private var gotSub: Boolean = false
    private lateinit var gottenSub: MediaItem.Subtitle

    /*-- Initiating ServerTalker --*/
    private val serverTalker = ClientDelegate()
    val tls = false
    private val altStorageHandler = AltStorageHandler()

    /*-- Cosmetics --*/
    private val tempMsgs = mutableListOf<String>()
    private var lockedScreen = false
    private var seekButtonEnable: Boolean? = null
    private var cutOutMode: Boolean = true

    /*----------------------------------------------*/
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                90909 -> {
                    gottenFile = data?.data
                    Toast.makeText(this, "Selected video successfully.",Toast.LENGTH_LONG).show()
                }
                80808 -> {
                    if (gottenFile!=null) {
                        val path = data?.data!!
                        val extension = path.toString().substring(path.toString().length - 4)
                        val type =
                            if (extension.contains("srt")) MimeTypes.APPLICATION_SUBRIP
                            else if ((extension.contains("ass"))
                                || (extension.contains("ssa"))
                            ) MimeTypes.TEXT_SSA
                            else if (extension.contains("ttml")) MimeTypes.APPLICATION_TTML
                            else if (extension.contains("vtt")) MimeTypes.TEXT_VTT else ""
                        if (type != "") {
                            gottenSub = MediaItem.Subtitle(
                                path,
                                type,  // The correct MIME type.
                                null,  // The subtitle language. May be null.
                                SELECTION_FLAG_DEFAULT
                            ) // Selection flags for the track.

                            Toast.makeText(
                                this, "Loaded sub successfully: ${
                                    URLDecoder.decode(
                                        path.lastPathSegment,
                                        "UTF-8"
                                    )
                                }", Toast.LENGTH_LONG
                            ).show()
                            gotSub = true

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

    /*----------------------------------------------*/
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.cosmik.syncplay.R.layout.room_activity)
        _binding = RoomActivityBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        Thread.setDefaultUncaughtExceptionHandler { paramThread, paramThrowable ->
            Log.e(
                "Error ${Thread.currentThread().stackTrace[2]}",
                paramThrowable.message.toString()
            )
            paramThrowable.printStackTrace()
        }

        //Getting all info from intent
        val ourInfo = intent.getStringExtra("json")
        val joinInfo: List<Any> =
            GsonBuilder().create().fromJson(ourInfo, List::class.java) as List<Any>
        //Creating all infrastructure roomviewmodel variables.
        serverTalker.addBroadcaster(this)
        roomViewModel = ViewModelProvider(this)[RoomViewModel::class.java]
        roomViewModel.serverHost = "151.80.32.178"
        roomViewModel.serverPort = (joinInfo[0] as Double).toInt()
        roomViewModel.currentUsername = joinInfo[1] as String
        roomViewModel.currentRoom = joinInfo[2] as String
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        findViewById<MaterialCheckBox>(rr.id.syncplay_ready).isChecked =
            sp.getBoolean("ready_firsthand", true)
        roomViewModel.ready = sp.getBoolean("ready_firsthand", true)
        roomViewModel.rewindThreshold = sp.getInt("rewind_threshold", 12).toLong()

        //Connecting to server
        val tls = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("tls", true)
        if (!roomViewModel.connected) {
            serverTalker.connect(
                roomViewModel.serverHost,
                roomViewModel.serverPort,
                roomViewModel,
                tls
            )
        }

        //Update ping
        pingStatusUpdater()

        //Room Overview transparency adjustment
        val alpha = PreferenceManager.getDefaultSharedPreferences(this)
            .getInt("overview_alpha", 30) //between 0-255
        @ColorInt val alphaColor = ColorUtils.setAlphaComponent(Color.DKGRAY, alpha)
        binding.syncplayOverviewCard.setBackgroundColor(alphaColor)

        //Controlling Cut-out mode and orientation.
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
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
                    if (length != roomViewModel.currentVideoLength) {
                        roomViewModel.currentVideoLength =
                            (myMediaPlayer?.duration!!.toDouble()) / 1000.0
                        serverTalker.sendPacket(
                            serverTalker.sendFile(),
                            roomViewModel.serverHost,
                            roomViewModel.serverPort
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
                                serverTalker.paused = false
                            }
                        }
                        false -> {
                            if (myMediaPlayer?.playbackState != ExoPlayer.STATE_BUFFERING) {
                                sendPlayback(false)
                                serverTalker.paused = true
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
                            serverTalker.sendPacket(
                                serverTalker.sendState(
                                    null,
                                    clienttime,
                                    true,
                                    newPosition.positionMs,
                                    1,
                                    play = myMediaPlayer?.isPlaying
                                ),
                                roomViewModel.serverHost,
                                roomViewModel.serverPort
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
                hideSystemUI(false)
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
        implementClickListeners() //Implementing click reactors
    }

    @SuppressLint("NewApi", "SetTextI18n")
    override fun onResume() {
        super.onResume()

        hideSystemUI(false) //Immersive Mode

        if (gottenFile != null) {
            injectVideo(binding.vidplayer.player as ExoPlayer, gottenFile!!)
            binding.starterInfo.visibility = GONE
        }


    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putDouble("last_position", roomViewModel.currentVideoPosition)
        Log.e("STATUS", "SAVED INSTANCE: ${roomViewModel.currentVideoPosition}")
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

    /***********************************************
     *                MY FUNCTIONS                 *
     ***********************************************/
    @SuppressLint("InlinedApi")
    override fun onUserInteraction() {
    }

    private fun hideSystemUI(newTrick: Boolean) {
        GlobalScope.launch(Dispatchers.Main) {
            if (newTrick) {
                WindowCompat.setDecorFitsSystemWindows(this@RoomActivity.window, false)
                WindowInsetsControllerCompat(
                    this@RoomActivity.window,
                    binding.root
                ).let { controller ->
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

                }
            } else {
                val decorView: View = window.decorView
                val uiOptions = decorView.systemUiVisibility
                var newUiOptions = uiOptions
                newUiOptions = newUiOptions or SYSTEM_UI_FLAG_LOW_PROFILE
                newUiOptions = newUiOptions or SYSTEM_UI_FLAG_FULLSCREEN
                newUiOptions = newUiOptions or SYSTEM_UI_FLAG_HIDE_NAVIGATION
                newUiOptions = newUiOptions or SYSTEM_UI_FLAG_IMMERSIVE
                newUiOptions = newUiOptions or SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                decorView.systemUiVisibility = newUiOptions
                OnSystemUiVisibilityChangeListener { newmode ->
                    if (newmode != newUiOptions) {
                        hideSystemUI(false)
                    }
                }
                window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)

            }
        }
    }

    private fun showSystemUI() {
        val decorView: View = window.decorView
        decorView.systemUiVisibility = (SYSTEM_UI_FLAG_LAYOUT_STABLE
                or SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }

    /*--------------------------------------------------------*/
    private fun injectVideo(mp: ExoPlayer, mediaPath: Uri) {
        try {
            //val vid: MediaItem = MediaItem.fromUri(mediaPath)
            val vidbuilder = MediaItem.Builder()
            if (gotSub) {
                gotSub = false
                runOnUiThread {
                    findViewById<ImageButton>(R.id.exo_subtitle).setImageDrawable(getDrawable(this, rr.drawable.ic_subtitles))
                }
                vidbuilder.setUri(mediaPath).setSubtitles(Lists.newArrayList(gottenSub))
            } else {
                vidbuilder.setUri(mediaPath)
            }
            val vid = vidbuilder.build()
            val file = File(mediaPath.path.toString())
            runOnUiThread {
                mp.setMediaItem(vid)
                findViewById<ImageButton>(R.id.exo_play).performClick()
                findViewById<ImageButton>(R.id.exo_pause).performClick()
                val vidtitle = mp.mediaMetadata.title
                val vidname = vidtitle?.toString() ?: URLDecoder.decode(file.name, "UTF-8")
                roomViewModel.currentVideoName = vidname
                roomViewModel.currentVideoPosition = 0.0
                roomViewModel.currentVideoSize =
                    altStorageHandler.getRealSizeFromUri(this, mediaPath)?.toDouble()
                        ?.roundToInt()!!
                if (startFromPosition != (-3.0).toLong()) mp.seekTo(startFromPosition)
            }

            if (!roomViewModel.connected) {
                serverTalker.sendPacket(
                    serverTalker.sendFile(),
                    roomViewModel.serverHost,
                    roomViewModel.serverPort
                )
            }
            updatePosition = true
            casualUpdater() //Most important updater to maintain continuity
        } catch (e: IOException) {
            throw RuntimeException("Invalid asset folder")
        }
    }

    /*--------------------------------------------------------*/
//Playback-related functions
    private fun pausePlayback(mp: ExoPlayer) {
        runOnUiThread {
            mp.pause()
        }
    }

    private fun playPlayback(mp: ExoPlayer) {
        runOnUiThread {
            mp.play()
        }
        hideSystemUI(false)
    }

    private fun sendPlayback(play: Boolean) {
        val clienttime = System.currentTimeMillis() / 1000.0
        serverTalker.sendPacket(
            serverTalker.sendState(null, clienttime, null, 0, 1, play),
            roomViewModel.serverHost,
            roomViewModel.serverPort
        )
    }

    private fun sendMessage(message: String) {
        hideKb()
        if (binding.syncplayMESSAGERY.visibility != VISIBLE) {
            binding.syncplayVisiblitydelegate.visibility = GONE
        }
        serverTalker.sendPacket(
            serverTalker.sendChat(message),
            roomViewModel.serverHost,
            roomViewModel.serverPort
        )
        binding.syncplayINPUTBox.setText("")
    }

    private fun replenishMsgs(rltvLayout: RelativeLayout) {
        GlobalScope.launch(Dispatchers.Main) {
            rltvLayout.removeAllViews()
            val msgs =
                if (roomViewModel.messageSequence.size != 0 && tempMsgs.size == 0) roomViewModel.messageSequence else tempMsgs
            for (message in msgs) {
                val msgPosition: Int = msgs.indexOf(message)

                val txtview = TextView(this@RoomActivity)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    txtview.text = Html.fromHtml(message)
                } else {
                    txtview.text = Html.fromHtml(message, Html.FROM_HTML_MODE_LEGACY)
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

    @SuppressLint("SetTextI18n")
    private fun replenishUsers(linearLayout: LinearLayout) {
        runOnUiThread {
            if (binding.syncplayOverviewcheckbox.isChecked) {
                linearLayout.removeAllViews()
                val userList: MutableMap<String, MutableList<String>> = roomViewModel.userList

                //Creating line for room-name:
                val roomnameView = TextView(this)
                roomnameView.text = "Current Room : ${roomViewModel.currentRoom}"

                val linearlayout0 = LinearLayout(this)
                val linearlayoutParams0: LinearLayout.LayoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                linearlayout0.gravity = END
                linearlayout0.orientation = HORIZONTAL
                linearlayout0.addView(roomnameView)
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
                        for (f in (0 until 2)) {
                            val lineBlanker3 = ImageView(this)
                            lineBlanker3.setImageResource(rr.drawable.ic_blanker)
                            linearlayout3.addView(lineBlanker3)
                        }
                        binding.syncplayOverview.addView(linearlayout3, linearlayoutParams3)
                    }


                }

            }
        }
    }

    /*--------------------------------------------------------*/
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
                    if (it.length>150) it.substring(0, 149)
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
            val seekbuttonsItem = popup.menu.add(0, 2, 2, "Fast Seek Buttons")
            seekbuttonsItem.isCheckable = true
            val ffwdButton = findViewById<ImageButton>(R.id.exo_ffwd)
            val rwndButton = findViewById<ImageButton>(R.id.exo_rew)
            seekbuttonsItem.isChecked = seekButtonEnable != false
            val messagesItem = popup.menu.add(0,3,3,"Messages History")
            //val reconnectItem = popup.menu.add(0,4,4,"Reconnect to Server")
            //val exitItem = popup.menu.add(0,5,5,"Exit Room")


            popup.setOnMenuItemClickListener {
                when (it) {
                    loadsubItem -> {
                        val intent2 = Intent()
                        intent2.type = "*/*"
                        intent2.action = Intent.ACTION_OPEN_DOCUMENT
                        startActivityForResult(intent2, 80808)
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
            roomViewModel.ready = b
            if (b) {
                serverTalker.sendPacket(
                    serverTalker.sendReadiness(true),
                    roomViewModel.serverHost, roomViewModel.serverPort
                )
            } else {
                serverTalker.sendPacket(
                    serverTalker.sendReadiness(false),
                    roomViewModel.serverHost, roomViewModel.serverPort
                )

            }
        }

        /*******************
         * Change Fit Mode *
         *******************/
        findViewById<ImageButton>(rr.id.syncplay_screen).setOnClickListener {
            var currentresolution = binding.vidplayer.resizeMode
            val resolutions = mutableMapOf<Int, String>()
            resolutions[AspectRatioFrameLayout.RESIZE_MODE_FIT] = "Resize Mode: FIT TO SCREEN"
            resolutions[AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH] = "Resize Mode: FIXED WIDTH"
            resolutions[AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT] =
                "Resize Mode: FIXED HEIGHT"
            resolutions[AspectRatioFrameLayout.RESIZE_MODE_FILL] = "Resize Mode: FILL SCREEN"
            resolutions[AspectRatioFrameLayout.RESIZE_MODE_ZOOM] = "Resize Mode: Zoom"
            resolutions[6] = "Resize Mode: FIT TO SCREEN with CUT-OUT MODE DISABLED"
            val nextresolution = currentresolution + 1
            currentresolution = binding.vidplayer.resizeMode

            if (currentresolution == 0) {
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
                }
            }
            when {
                nextresolution == 5 -> {
                    binding.vidplayer.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    resolutions[0]?.let { it1 -> displayInfo(it1) }
                }
                cutOutMode -> {
                    binding.vidplayer.resizeMode = nextresolution
                    resolutions[currentresolution]?.let { it1 -> displayInfo(it1) }
                }
                else -> {
                    resolutions[6]?.let { it1 -> displayInfo(it1) }
                }
            }

            binding.vidplayer.performClick()
            binding.vidplayer.performClick()
        }
    }

    private fun ccSelect(ccButton: ImageButton) {
        val mappedTrackInfo = trackSelec.currentMappedTrackInfo!! //get Tracks from our default (already injected) selector
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
                if (cc.value.label==null) {
                    name = "Track"
                }
                val item = popup.menu.add(0, cc.key, NONE, "$name [${(cc.value.language).toString().uppercase()}]")
                item.isCheckable = true
                if (ccTracker==-1) {
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
                    if (menuItem.itemId!=-3) {
                        val override: DefaultTrackSelector.SelectionOverride =
                            DefaultTrackSelector.SelectionOverride(menuItem.itemId, 0)
                        it.setSelectionOverride(
                            rendererIndex,
                            trackSelec.currentMappedTrackInfo!!.getTrackGroups(rendererIndex),
                            override
                        )
                    } else {
                        it.clearSelectionOverride(rendererIndex, trackSelec.currentMappedTrackInfo!!.getTrackGroups(rendererIndex))
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
        val mappedTrackInfo = trackSelec.currentMappedTrackInfo!! //get Tracks from our default (already injected) selector
        var rendererIndex = 0
        for (i in 0 until mappedTrackInfo.rendererCount) {
            val trackgroups = mappedTrackInfo.getTrackGroups(i)
            if (trackgroups.length != 0) {
                when (myMediaPlayer?.getRendererType(i)) {
                    C.TRACK_TYPE_AUDIO-> rendererIndex = i
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
                if (audio.value.label==null) {
                    name = "Track"
                }
                val item = popup.menu.add(0, audio.key, 0, "$name [${(audio.value.language).toString().uppercase()}]")
                item.isCheckable = true
                if (audioTracker==-1) {
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

    @SuppressLint("SetTextI18n")
    private fun casualUpdater() {
        //TODO: Change periodic sleep to periodic handler.
        GlobalScope.launch(Dispatchers.Unconfined) {
            while (true) {
                if (updatePosition) {
                    /* Informing my ViewModel about current vid position so it is retrieved for networking after */
                    runOnUiThread {
                        val progress = (binding.vidplayer.player?.currentPosition?.div(1000.0))
                        if (progress != null) {
                            roomViewModel.currentVideoPosition = progress
                        }
                    }
                }
                delay(75)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun pingStatusUpdater() {
        GlobalScope.launch(Dispatchers.Unconfined) {
            while (true) {
                /* Informing my ViewModel about current vid position so it is retrieved for networking after */
                val isConnected = roomViewModel.connected
                if (isConnected) {
                    val ping = IcmpPing().pingIcmp("151.80.32.178", 32)*1000.0
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
                        binding.syncplaySignalIcon.setImageDrawable(getDrawable(this@RoomActivity, rr.drawable.ic_unconnected))
                    }
                }

                //Updating my views
                delay(1000)
            }
        }

    }

    private fun timeStamper(seconds: Int): String {
        return if (seconds < 3600) {
            String.format("%02d:%02d", (seconds / 60) % 60, seconds % 60)
        } else {
            String.format("%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60)
        }
    }

    private fun broadcastMessage(message: String, isChat: Boolean, chatter: String = "") {
        //Time-stamp only
        var timeSent: String = Timestamp(System.currentTimeMillis()).toString()
        timeSent = timeSent.removeRange(19 until timeSent.length).removeRange(0..10)
        val timestamp = "<font color=\"#aa666666\">[$timeSent] </font>"
        val fullmsg: String = if (isChat) {
            val selfColorCode = "#ff2d2d"
            val friendColorCode = "#6082B6"
            if (chatter.lowercase() == roomViewModel.currentUsername.lowercase()) {
                val username = "<font color=\"${selfColorCode}\"><strong><bold> $chatter:</bold></strong></font>"
                "$timestamp$username<font color=\"#ffffff\"><bold> $message</bold></font>"
            } else {
                val username = "<font color=\"${friendColorCode}\"><strong><bold> $chatter:</bold></strong></font>"
                "$timestamp$username<font color=\"#ffffff\"><bold> $message</bold></font>"
            }
        } else {
            "$timestamp<font color=\"#eeeeee\"><bold>$message</bold></font>"
        }
        roomViewModel.messageSequence.add(fullmsg)
        tempMsgs.add(fullmsg)
        val maxmsgs = PreferenceManager.getDefaultSharedPreferences(this@RoomActivity).getInt("msg_count", 12)
        if (tempMsgs.size>maxmsgs) {
            tempMsgs.removeFirst()
        }
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
                setContentView(rr.layout.messages_popup)
                val rltvLayout = findViewById<RelativeLayout>(rr.id.syncplay_MESSAGEHISTORY)
                rltvLayout.removeAllViews()
                val msgs = roomViewModel.messageSequence
                for (message in msgs) {
                    val msgPosition: Int = msgs.indexOf(message)

                    val txtview = TextView(this@RoomActivity)
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                        txtview.text = Html.fromHtml(message)
                    } else {
                        txtview.text = Html.fromHtml(message, Html.FROM_HTML_MODE_LEGACY)
                    }
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

            }
        }


        val x = (this.resources.displayMetrics.widthPixels / 2)-((DpToPx(350f, this).convertUnit()/2).roundToInt())
        val y = (this.resources.displayMetrics.heightPixels / 2)-((DpToPx(200f, this).convertUnit()/2).roundToInt())

        DemoPopup(this).setBlurBackgroundEnable(true).showPopupWindow(x, y)
    }
    /*<---------------------- Event Listeners ---------------------->*/
    override fun onSomeonePaused(pauser: String) {
        if (pauser!=roomViewModel.currentUsername) myMediaPlayer?.let { pausePlayback(it) }
        broadcastMessage("$pauser paused", false)
    }
    override fun onSomeonePlayed(player: String) {
        if (player!=roomViewModel.currentUsername) myMediaPlayer?.let { playPlayback(it) }
        broadcastMessage("$player unpaused", false)
    }
    override fun onChatReceived(chatter: String, chatmessage: String) {
        broadcastMessage(chatmessage, true, chatter)
    }

    override fun onSomeoneSeeked(seeker: String, toPosition: Double) {
        runOnUiThread {
            if (seeker!=roomViewModel.currentUsername) {
                broadcastMessage("$seeker jumped from ${timeStamper((roomViewModel.currentVideoPosition).roundToInt())} to ${timeStamper(toPosition.roundToInt())}", false)
                receivedSeek = true
                myMediaPlayer?.seekTo((toPosition*1000.0).toLong())
            } else {
                broadcastMessage("$seeker jumped from ${timeStamper((seekTracker).roundToInt())} to ${timeStamper(toPosition.roundToInt())}", false)
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

    override fun onSomeoneLoadedFile(person: String, file: String, fileduration: String, filesize: String) {
        replenishUsers(binding.syncplayOverview)
        broadcastMessage("$person is playing '$file' (${timeStamper(fileduration.toDouble().roundToInt())})", false)
    }


    override fun onDisconnected() {
        broadcastMessage("Lost connection to the server and left room. Trying to reconnect...", false)
    }

    override fun onJoined() {
        broadcastMessage("You have joined the room: ${roomViewModel.currentRoom}", false)
    }

    override fun onReconnected() {
        broadcastMessage("Connected.", false)
        replenishUsers(binding.syncplayOverview)
    }


}