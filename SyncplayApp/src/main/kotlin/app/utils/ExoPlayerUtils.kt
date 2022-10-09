package app.utils

import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.MenuItem
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.widget.ImageButton
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import app.R
import app.controllers.activity.RoomActivity
import app.protocol.JsonSender
import app.utils.RoomUtils.sendPlayback
import app.utils.RoomUtils.string
import app.utils.UIUtils.displayInfo
import app.utils.UIUtils.hideKb
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Tracks
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride
import com.google.android.exoplayer2.ui.CaptionStyleCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.Collections

/** Separating Exoplayer-related methods here to make our code cleaner, and more maintainable.
 * Any function that is here needs no more coding and is ready to deploy for all Android APIs.
 */
object ExoPlayerUtils {

    /** Injects a certain media file into ExoPlayer **/
    fun RoomActivity.injectVideo(mediaPath: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                /** This is the builder responsible for building a MediaItem component for ExoPlayer **/
                val vidbuilder = MediaItem.Builder()

                /** Seeing if we have any loaded external sub **/
                if (p.file?.externalSub != null) {
                    runOnUiThread {
                        hudBinding.exoSubtitle.setImageDrawable(
                            AppCompatResources.getDrawable(
                                this@injectVideo,
                                R.drawable.ic_subtitles
                            )
                        )
                    }
                    vidbuilder.setUri(mediaPath)
                        .setSubtitleConfigurations(Collections.singletonList(p.file!!.externalSub!!))
                } else {
                    vidbuilder.setUri(mediaPath)
                }

                /** Now finally, we build it **/
                val vid = vidbuilder.build()

                /** Injecting it into ExoPlayer and getting relevant info **/
                myExoPlayer?.setMediaItem(vid) /* This loads the media into ExoPlayer **/
                myExoPlayer?.prepare()

                /** Goes back to the beginning */
                p.currentVideoPosition = 0.0

                /** Seeing if we have to start over **/
                if (startFromPosition != (-3.0).toLong()) myExoPlayer?.seekTo(startFromPosition)

                /** Removing artwork */
                binding.syncplayArtworkDelegate.visibility = View.GONE
                binding.vidplayer.videoSurfaceView?.visibility = View.VISIBLE
                binding.vidplayer.setShutterBackgroundColor(Color.BLACK)

//            if (!p.connected) {
//                p.sendPacket(JsonSender.sendFile(p.file!!, this))
//            }

                //TODO: This shit gotta be fixed
                //sharedplaylistPopup.update()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    /** Used for applying any registered subtitle/audio selecions after, for example, going back
     * to the RoomActivity after closing the app for far too long (restoring instance state).*/
    fun RoomActivity.applyLastOverrides() {
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

    /** This pauses playback on the right thread **/
    fun RoomActivity.pausePlayback() {
        runOnUiThread {
            myExoPlayer?.pause()
        }
    }

    /** This resumes playback on the right thread, and hides ExoPlayer's HUD/Controller UI**/
    fun RoomActivity.playPlayback() {
        runOnUiThread {
            myExoPlayer?.play()
            MiscUtils.hideSystemUI(this, false)
        }
    }

    /** Checks for available sub tracks, shows 'em in a popup, then applies a selection **/
    fun RoomActivity.subtitleSelect(ccButton: ImageButton) {
        p.file?.analyzeTracks(myExoPlayer!!)
        if (p.file == null) return
        if (p.file!!.subtitleTracks.isEmpty()) {
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
            for (subtitleTrack in p.file!!.subtitleTracks) {
                /* Choosing a name for the sub track, a format's label is a good choice */
                val name = if (subtitleTrack.format?.label == null) {
                    getString(R.string.room_track_track)
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
                displayInfo(string(R.string.room_sub_track_changed, menuItem.title.toString()))
                return@setOnMenuItemClickListener true
            }

            // Show the popup menu.
            popup.show()

        }
    }

    /** Checks for available audio tracks, shows 'em in a popup, then applies a selection **/
    fun RoomActivity.audioSelect(audioButton: ImageButton) {
        p.file?.analyzeTracks(myExoPlayer!!)
        if (p.file == null) return
        if (p.file!!.audioTracks.isEmpty()) {
            displayInfo(getString(R.string.room_audio_track_not_found)) /* Otherwise, no audio track found */
        } else {
            val popup =
                PopupMenu(this, audioButton) /* Creating a popup menu, anchored on Audio Button */

            /** Going through the entire audio track list, and populating the popup menu with each one of them **/
            for (audioTrack in p.file!!.audioTracks) {
                /* Choosing a name for the audio track, a format's label is a good choice */
                val name = if (audioTrack.format?.label == null) {
                    getString(R.string.room_track_track)
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
                displayInfo(string(R.string.room_audio_track_changed, menuItem.title.toString()))
                return@setOnMenuItemClickListener true
            }

            // Show the popup menu.
            popup.show()
        }
    }

    /** Initializes ExoPlayer with all required parameters */
    fun RoomActivity.initializeExo() {
        /** LoadControl variables and building (Buffering Controller) **/
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
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

        binding.vidplayer.player = myExoPlayer

        /** Customizing ExoPlayer components **/
        myExoPlayer?.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT /* Starter scaling */

        myExoPlayer?.playWhenReady = false /* Don't play once the media has been buffered */

        binding.vidplayer.setShutterBackgroundColor(Color.TRANSPARENT)
        binding.vidplayer.videoSurfaceView?.visibility = View.GONE


        /** Defining the subtitle appearance, and inserting it into ExoPlayer **/
        val captionStyle = CaptionStyleCompat(
            Color.WHITE,
            Color.TRANSPARENT,
            Color.TRANSPARENT,
            CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
            Color.BLACK,
            Typeface.DEFAULT_BOLD
        )

        binding.vidplayer.subtitleView?.setStyle(captionStyle)
        binding.vidplayer.subtitleView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, ccsize)

        /** Listening to ExoPlayer's UI Visibility **/
        binding.vidplayer.setControllerVisibilityListener { visibility ->
            if (visibility == View.VISIBLE) {
                binding.syncplayMESSAGERYOpacitydelegate.also {
                    it.clearAnimation()
                    it.alpha = 1f
                    it.visibility = View.VISIBLE
                }
                binding.syncplayMESSAGERY.also {
                    it.clearAnimation()
                    it.alpha = 1f
                    it.visibility = View.VISIBLE
                }
                for (c in binding.syncplayMESSAGERY.children) {
                    c.animate()
                        .alpha(1f)
                        .setDuration(1L)
                        .setInterpolator(AccelerateInterpolator())
                        .start()
                }
                binding.syncplayVisiblitydelegate.visibility = View.VISIBLE
                val visib = if (seekButtonEnable == false) View.GONE else View.VISIBLE
                Handler(Looper.getMainLooper()).postDelayed({
                    hudBinding.exoFfwd.visibility = visib
                    hudBinding.exoRew.visibility = visib
                }, 10)
            } else {
                MiscUtils.hideSystemUI(this, false)
                binding.syncplayMESSAGERYOpacitydelegate.also {
                    it.clearAnimation()
                    it.alpha = 0f
                    it.visibility = View.GONE
                }
                binding.syncplayMESSAGERY.also {
                    it.clearAnimation()
                    it.alpha = 0f
                    it.visibility = View.GONE
                }
                if ((!binding.syncplayINPUTBox.hasFocus())
                    || binding.syncplayINPUTBox.text.toString().trim() == ""
                ) {
                    binding.syncplayVisiblitydelegate.visibility = View.GONE
                    hideKb()
                }
            }
            binding.syncplayOverviewCard.isVisible =
                (visibility == View.VISIBLE) && binding.syncplayOverviewcheckbox.isChecked
        }

        /** This listener is very important, without it, syncplay is nothing but a video player */
        myExoPlayer?.addListener(object : Player.Listener {
            override fun onIsLoadingChanged(isLoading: Boolean) {
                super.onIsLoadingChanged(isLoading)
                if (!isLoading) {
                    val duration = ((myExoPlayer?.duration?.toDouble())?.div(1000.0)) ?: 0.0
                    if (duration != p.file?.fileDuration) {
                        p.file?.fileDuration = duration
                        p.sendPacket(JsonSender.sendFile(p.file ?: return, this@initializeExo))
                    }
                }
            }


            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)
                if (myExoPlayer?.mediaItemCount != 0) {
                    if (myExoPlayer?.playbackState != ExoPlayer.STATE_BUFFERING) {
                        when (isPlaying) {
                            true -> {
                                sendPlayback(true)
                                p.paused = false
                            }

                            false -> {
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
                                JsonSender.sendState(
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

    }
}