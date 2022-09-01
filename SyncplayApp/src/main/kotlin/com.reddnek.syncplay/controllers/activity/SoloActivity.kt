package com.reddnek.syncplay.controllers.activity

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.MenuItem
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.widget.ImageButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.children
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.CaptionStyleCompat
import com.google.android.exoplayer2.util.MimeTypes
import com.reddnek.syncplay.R
import com.reddnek.syncplay.controllers.fragment.RoomSettingsFragment
import com.reddnek.syncplay.databinding.ActivitySoloplayerBinding
import com.reddnek.syncplay.misc.HudBinding
import com.reddnek.syncplay.popups.StarterHintPopup
import com.reddnek.syncplay.utils.RoomUtils.string
import com.reddnek.syncplay.utils.SyncplayUtils
import com.reddnek.syncplay.utils.SyncplayUtils.getFileName
import com.reddnek.syncplay.utils.UIUtils.attachTooltip
import com.reddnek.syncplay.utils.UIUtils.hideKb
import com.reddnek.syncplay.utils.UIUtils.showPopup
import com.reddnek.syncplay.utils.UIUtils.toasty
import com.reddnek.syncplay.wrappers.MediaFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.*

class SoloActivity : AppCompatActivity() {

    /* Declaring our ViewBinding global variables (much faster than findViewById) **/
    lateinit var binding: ActivitySoloplayerBinding
    private lateinit var hudBinding: HudBinding

    /*-- Declaring ExoPlayer variables --*/
    var myExoPlayer: ExoPlayer? = null
    var file: MediaFile? = null

    /*-- Declaring Playtracking variables **/
    private var lastAudioOverride: TrackSelectionOverride? = null
    private var lastSubtitleOverride: TrackSelectionOverride? = null
    var startFromPosition = (-3.0).toLong()

    /*-- UI-Related --*/
    private var lockedScreen = false
    private var seekButtonEnable: Boolean? = null
    private var cutOutMode: Boolean = true
    var ccsize = 18f

    /**********************************************************************************************
     *                                  LIFECYCLE METHODS
     *********************************************************************************************/

    private val videoPickResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result?.resultCode == Activity.RESULT_OK) {
                file = MediaFile()
                file?.uri = result.data?.data
                file?.collectInfo(this@SoloActivity)
                toasty(string(R.string.room_selected_vid, "${file?.fileName}"))
                Handler(Looper.getMainLooper()).postDelayed({ myExoPlayer?.seekTo(0L) }, 2000)
            }
        }

    private val subtitlePickResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result?.resultCode == Activity.RESULT_OK) {
                if (file != null) {
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
                        file!!.externalSub = MediaItem.SubtitleConfiguration.Builder(path)
                            .setUri(path)
                            .setMimeType(mimeType)
                            .setLanguage(null)
                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                            .build()
                        toasty(string(R.string.room_selected_sub, filename))
                    } else {
                        toasty(getString(R.string.room_selected_sub_error))
                    }
                } else {
                    toasty(getString(R.string.room_sub_error_load_vid_first))
                }
            }

        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        /** Inflating and ViewBinding */
        binding = ActivitySoloplayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hudBinding = HudBinding.bind(findViewById(R.id.vidplayerhud))

        /** Storing SharedPreferences to apply some settings **/
        val sp = PreferenceManager.getDefaultSharedPreferences(this)

        /** Showing starter hint if it's not permanently disabled */
        val dontShowHint = sp.getBoolean("dont_show_starter_hint", false)
        if (!dontShowHint) {
            showPopup(StarterHintPopup(this@SoloActivity), true)
        }

        /** Let's apply Room UI Settings **/
        applyUISettings()

        /** Let's apply Cut-Out Mode on the get-go, user can turn it off later **/
        SyncplayUtils.cutoutMode(true, window)

        /** Inject in-room settings fragment **/
        supportFragmentManager.beginTransaction()
            .replace(R.id.pseudo_popup_container, RoomSettingsFragment())
            .commit()

        /** Attaching tooltip longclick listeners to all the buttons using an extensive method */
        for (child in hudBinding.buttonRowOne.children) {
            if (child is ImageButton) {
                child.attachTooltip(child.contentDescription.toString())
            }
        }
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
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER /* We prefer extensions, such as FFmpeg */
                )
            )
            .build()
            .also { exoPlayer ->
                binding.vidplayer.player = exoPlayer
            }

        /** Customizing ExoPlayer components **/
        myExoPlayer?.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT /* Starter scaling */

        myExoPlayer?.playWhenReady = true /* Play once the media has been buffered */

        binding.vidplayer.setShutterBackgroundColor(Color.TRANSPARENT)
        binding.vidplayer.videoSurfaceView?.visibility = View.GONE

        myExoPlayer?.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                super.onTracksChanged(tracks)
                /** Repopulate audio and subtitle track lists with the new analysis of tracks **/
                file?.analyzeTracks(myExoPlayer!!)
            }
        })

        /** Defining the subtitle appearance, and inserting it into ExoPlayer **/
        val captionStyle = CaptionStyleCompat(
            Color.WHITE,
            Color.TRANSPARENT,
            Color.TRANSPARENT,
            CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
            Color.BLACK,
            Typeface.DEFAULT_BOLD
        )
        binding.vidplayer.subtitleView?.also {
            it.setStyle(captionStyle)
            it.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, ccsize)
        }

        /** Clicking on the player hides the keyboard, and loses focus from the message box **/
        binding.vidplayer.setOnClickListener {
            hideKb()
        }

        /** Listening to ExoPlayer's UI Visibility **/
        binding.vidplayer.setControllerVisibilityListener { visibility ->
            if (visibility == View.VISIBLE) {
                val visib = if (seekButtonEnable == false) View.GONE else View.VISIBLE
                Handler(Looper.getMainLooper()).postDelayed({
                    hudBinding.exoFfwd.visibility = visib
                    hudBinding.exoRew.visibility = visib
                }, 10)
            } else {
                SyncplayUtils.hideSystemUI(this, false)
            }
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
                    binding.vidplayer.controllerHideOnTouch = false
                    binding.vidplayer.controllerAutoShow = false
                    binding.vidplayer.hideController()
                    binding.syncplayerLockoverlay.visibility = View.VISIBLE
                    binding.syncplayerLockoverlay.isFocusable = true
                    binding.syncplayerLockoverlay.isClickable = true
                    binding.syncplayerUnlock.visibility = View.VISIBLE
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
                binding.syncplayerLockoverlay.visibility = View.GONE
                binding.syncplayerLockoverlay.isFocusable = false
                binding.syncplayerLockoverlay.isClickable = false
                binding.syncplayerUnlock.visibility = View.GONE
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
            val popup = PopupMenu(
                this,
                hudBinding.syncplayAddfile
            )

            val loadsubItem =
                popup.menu.add(
                    0,
                    0,
                    0,
                    getString(R.string.room_overflow_sub)
                )

            val cutoutItem = popup.menu.add(
                0,
                1,
                1,
                getString(R.string.room_overflow_cutout)
            )
            cutoutItem.isCheckable = true
            cutoutItem.isChecked = cutOutMode
            cutoutItem.isEnabled = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            val seekbuttonsItem = popup.menu.add(
                0,
                2,
                2,
                getString(R.string.room_overflow_ff)
            )
            seekbuttonsItem.isCheckable = true
            val ffwdButton = hudBinding.exoFfwd
            val rwndButton = hudBinding.exoRew
            seekbuttonsItem.isChecked = seekButtonEnable != false

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
                            ffwdButton.visibility = View.GONE
                            rwndButton.visibility = View.GONE
                        } else {
                            seekButtonEnable = true
                            ffwdButton.visibility = View.VISIBLE
                            rwndButton.visibility = View.VISIBLE
                        }
                    }
                }
                return@setOnMenuItemClickListener true
            }
            popup.show()
        }


        /*******************
         * Change Fit Mode *
         *******************/
        hudBinding.syncplayScreen.setOnClickListener {
            val currentresolution = binding.vidplayer.resizeMode
            val resolutions = mutableMapOf<Int, String>()
            resolutions[AspectRatioFrameLayout.RESIZE_MODE_FIT] =
                getString(R.string.room_scaling_fit_screen)
            resolutions[AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH] =
                getString(R.string.room_scaling_fixed_width)
            resolutions[AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT] =
                getString(R.string.room_scaling_fixed_height)
            resolutions[AspectRatioFrameLayout.RESIZE_MODE_FILL] =
                getString(R.string.room_scaling_fill_screen)
            resolutions[AspectRatioFrameLayout.RESIZE_MODE_ZOOM] =
                getString(R.string.room_scaling_zoom)

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
            binding.pseudoPopupParent.visibility = View.GONE
            applyUISettings()
        }
    }

    override fun onResume() {
        super.onResume()
        /** Activating Immersive Mode **/
        SyncplayUtils.hideSystemUI(this, false)

        /** If there exists a file already, inject it again **/
        injectVideo(file?.uri ?: return)

        /** And apply track choices again **/
        applyLastOverrides()

    }

    override fun onPause() {
        super.onPause()
        startFromPosition = binding.vidplayer.player?.currentPosition!!
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

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    fun displayInfo(msg: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            binding.syncplayInfoDelegate.clearAnimation()
            binding.syncplayInfoDelegate.text = msg
            binding.syncplayInfoDelegate.alpha = 1f
            binding.syncplayInfoDelegate.animate()
                .alpha(0f)
                .setDuration(1000L)
                .setInterpolator(AccelerateInterpolator())
                .start()
        }
    }

    private fun injectVideo(mediaPath: Uri) {
        try {
            /** This is the builder responsible for building a MediaItem component for ExoPlayer **/
            val vidbuilder = MediaItem.Builder()

            /** Seeing if we have any loaded external sub **/
            if (file?.externalSub != null) {
                runOnUiThread {
                    hudBinding.exoSubtitle.setImageDrawable(
                        AppCompatResources.getDrawable(
                            this,
                            R.drawable.ic_subtitles
                        )
                    )
                }
                vidbuilder.setUri(mediaPath)
                    .setSubtitleConfigurations(Collections.singletonList(file!!.externalSub!!))
            } else {
                vidbuilder.setUri(mediaPath)
            }

            /** Now finally, we build it **/
            val vid = vidbuilder.build()

            /** Injecting it into ExoPlayer and getting relevant info **/
            runOnUiThread {
                myExoPlayer?.setMediaItem(vid) /* This loads the media into ExoPlayer **/

                hudBinding.exoPlay.performClick()
                hudBinding.exoPause.performClick()

                /** Seeing if we have to start over **/
                if (startFromPosition != (-3.0).toLong()) myExoPlayer?.seekTo(startFromPosition)

                /** Removing artwork */
                binding.syncplayArtworkDelegate.visibility = View.GONE
                binding.vidplayer.videoSurfaceView?.visibility = View.VISIBLE
                binding.vidplayer.setShutterBackgroundColor(Color.BLACK)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /** Checks for available sub tracks, shows 'em in a popup, then applies a selection **/
    private fun subtitleSelect(ccButton: ImageButton) {
        file?.analyzeTracks(myExoPlayer!!) ?: return
        if (file?.subtitleTracks!!.isEmpty()) {
            displayInfo(getString(R.string.room_sub_track_notfound))
            runOnUiThread {
                ccButton.setImageDrawable(
                    AppCompatResources.getDrawable(
                        this,
                        R.drawable.ic_subtitles_off
                    )
                )
            }
        } else {
            runOnUiThread {
                ccButton.setImageDrawable(
                    AppCompatResources.getDrawable(
                        this,
                        R.drawable.ic_subtitles
                    )
                )
            }
            val popup = PopupMenu(this, ccButton)
            popup.menu.add(0, -999, 0, getString(R.string.room_sub_track_disable))
            for (subtitleTrack in file!!.subtitleTracks) {
                /* Choosing a name for the sub track, a format's label is a good choice */
                val name = if (subtitleTrack.format?.label == null) {
                    getString(R.string.room_track_track)
                } else {
                    subtitleTrack.format?.label!!
                }

                /* Now creating the popup menu item corresponding to the audio track */
                val item = popup.menu.add(
                    0,
                    file!!.subtitleTracks.indexOf(subtitleTrack),
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
                        file!!.subtitleTracks[menuItem.itemId].trackGroup!!,
                        file!!.subtitleTracks[menuItem.itemId].index
                    )
                    myExoPlayer?.trackSelector?.parameters =
                        builder.addOverride(lastSubtitleOverride!!).build()
                }

                /** Show an info that audio track has been changed **/
                displayInfo(string(R.string.room_sub_track_changed, menuItem.title.toString()))
                return@setOnMenuItemClickListener true
            }

            // Show the popup menu.
            popup.show()

        }
    }

    /** Checks for available audio tracks, shows 'em in a popup, then applies a selection **/
    private fun audioSelect(audioButton: ImageButton) {
        file?.analyzeTracks(myExoPlayer!!)
        if (file == null) return
        if (file!!.audioTracks.isEmpty()) {
            displayInfo(getString(R.string.room_audio_track_not_found)) /* Otherwise, no audio track found */
        } else {
            val popup =
                PopupMenu(this, audioButton) /* Creating a popup menu, anchored on Audio Button */

            /** Going through the entire audio track list, and populating the popup menu with each one of them **/
            for (audioTrack in file!!.audioTracks) {
                /* Choosing a name for the audio track, a format's label is a good choice */
                val name = if (audioTrack.format?.label == null) {
                    getString(R.string.room_track_track)
                } else {
                    audioTrack.format?.label!!
                }

                /* Now creating the popup menu item corresponding to the audio track */
                val item = popup.menu.add(
                    0,
                    file!!.audioTracks.indexOf(audioTrack),
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
                    file!!.audioTracks[menuItem.itemId].trackGroup!!,
                    file!!.audioTracks[menuItem.itemId].index
                )
                val newParams = builder.addOverride(lastAudioOverride!!).build()

                myExoPlayer?.trackSelector?.parameters = newParams

                /** Show an info that audio track has been changed **/
                displayInfo(string(R.string.room_audio_track_changed, menuItem.title.toString()))
                return@setOnMenuItemClickListener true
            }

            // Show the popup menu.
            popup.show()
        }
    }

    /** Used for applying any registered subtitle/audio selecions after, for example, going back
     * to the RoomActivity after closing the app for far too long (restoring instance state).*/
    private fun applyLastOverrides() {
        file?.analyzeTracks(myExoPlayer!!)
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

    /** Applies UI settings that are being changed through the in-room settings dialog **/
    fun applyUISettings() {
        lifecycleScope.launch(Dispatchers.Main) {
            /* Holding a reference to SharedPreferences to use it later */
            val sp = PreferenceManager.getDefaultSharedPreferences(this@SoloActivity)

            /* Applying Subtitle Size setting */
            ccsize = sp.getInt("subtitle_size", 18).toFloat()
            binding.vidplayer.subtitleView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, ccsize)
        }
    }
}