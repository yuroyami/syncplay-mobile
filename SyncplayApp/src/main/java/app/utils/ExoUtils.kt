package app.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import app.R
import app.protocol.JsonSender
import app.ui.activities.WatchActivity
import app.utils.MiscUtils.hideSystemUI
import app.utils.RoomUtils.sendPlayback
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Tracks
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.CaptionStyleCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Methods exclusive to ExoPlayer functionality */
object ExoUtils {

    /** This pauses playback on the main (necessary) thread **/
    fun WatchActivity.pausePlayback() {
        runOnUiThread { myExoPlayer.pause() }
    }

    /** This resumes playback on the main thread, and hides system UI **/
    fun WatchActivity.playPlayback() {
        runOnUiThread {
            myExoPlayer.play()
            hideSystemUI(true)
        }
    }

    /** Changes the subtitle appearance given the size and captionStyle (otherwise, default will be used) */
    fun WatchActivity.retweakSubtitleAppearance(
        size: Float = 16f,
        captionStyle: CaptionStyleCompat = CaptionStyleCompat(
            Color.WHITE, Color.TRANSPARENT, Color.TRANSPARENT,
            CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, Color.BLACK, Typeface.DEFAULT_BOLD
        ),
    ) {
        playerView.subtitleView?.setStyle(captionStyle)
        playerView.subtitleView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, size)
    }

    /** This is the global event listener for ExoPlayer (responsible for many core functionality features */
    fun WatchActivity.setupEventListeners() {
        myExoPlayer.addListener(object : Player.Listener {

            /* This detects when the player loads a file/URL, so we tell the server */
            override fun onIsLoadingChanged(isLoading: Boolean) {
                super.onIsLoadingChanged(isLoading)

                if (!isLoading) {

                    /* Updating our timeFull */
                    val duration = myExoPlayer.duration / 1000.0
                    timeFull.value = duration.toLong()

                    if (isSoloMode()) return
                    if (duration != media?.fileDuration) {
                        media?.fileDuration = duration
                        p.sendPacket(JsonSender.sendFile(media ?: return, this@setupEventListeners))
                    }
                }
            }

            /* This detects when the user pauses or plays */
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)

                if (myExoPlayer.mediaItemCount != 0) {
                    if (myExoPlayer.playbackState != ExoPlayer.STATE_BUFFERING) {
                        exoPlaying.value = isPlaying //Just to inform UI

                        //Tell server about playback state change
                        if (!isSoloMode()) {
                            sendPlayback(isPlaying)
                            p.paused = !isPlaying
                        }
                    }
                }
            }

            /* This detects when the user seeks */
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int,
            ) {
                super.onPositionDiscontinuity(oldPosition, newPosition, reason)

            }

            /* This detects when a media track change has happened (such as loading a custom sub) */
            override fun onTracksChanged(tracks: Tracks) {
                super.onTracksChanged(tracks)

                /* Updating our HUD's timeFull here again, just in case. */
                val duration = myExoPlayer.duration / 1000.0
                timeFull.value = duration.toLong()

                /* Repopulate audio and subtitle track lists with the new analysis of tracks **/
                media?.analyzeTracks(myExoPlayer)
            }
        })
    }

    /** Tracks progress CONTINUOUSLY and updates it to UI (and server, if no solo mode) */
    fun WatchActivity.trackProgress() {
        lifecycleScope.launch(Dispatchers.Main) {
            while (true) {
                if (myExoPlayer.isCurrentMediaItemSeekable) {
                    val progress = (myExoPlayer.currentPosition.div(1000L))

                    /* Informing UI */
                    timeCurrent.value = progress

                    /* Informing protocol */
                    if (!isSoloMode()) {
                        p.currentVideoPosition = progress.toDouble()
                    }
                }
                delay(1000)
            }
        }
    }

    /** Changes Aspect Ratio for Exo */
    @SuppressLint("WrongConstant")
    fun WatchActivity.nextAspectRatio(): String {
        val resolutions = mutableMapOf<Int, String>()
        resolutions[AspectRatioFrameLayout.RESIZE_MODE_FIT] = getString(R.string.room_scaling_fit_screen)
        resolutions[AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH] = getString(R.string.room_scaling_fixed_width)
        resolutions[AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT] = getString(R.string.room_scaling_fixed_height)
        resolutions[AspectRatioFrameLayout.RESIZE_MODE_FILL] = getString(R.string.room_scaling_fill_screen)
        resolutions[AspectRatioFrameLayout.RESIZE_MODE_ZOOM] = getString(R.string.room_scaling_zoom)

        var nextRes = (playerView.resizeMode + 1)
        if (nextRes == 5) nextRes = 0;
        playerView.resizeMode = nextRes

        return resolutions[nextRes]!!
    }


    /** Used for applying any registered subtitle/audio selecions after, for example, going back
     * to the RoomActivity after closing the app for far too long (restoring instance state).*/
    fun WatchActivity.applyLastOverrides() {
        media?.analyzeTracks(myExoPlayer)
        if (myExoPlayer.trackSelector != null) {
            val builder = myExoPlayer.trackSelector?.parameters?.buildUpon()

            var newParams = builder?.build()

            if (lastAudioOverride != null) {
                newParams = newParams?.buildUpon()?.addOverride(lastAudioOverride!!)?.build()
            }
            if (lastSubtitleOverride != null) {
                newParams = newParams?.buildUpon()?.addOverride(lastSubtitleOverride!!)?.build()
            }
            myExoPlayer.trackSelector?.parameters = newParams ?: return
        }
    }

    /** Selects the track with the given index, based on its type.
     * @param type Whether it's an audio or a text track. Can only be [C.TRACK_TYPE_TEXT] or [C.TRACK_TYPE_AUDIO]
     * @param index The index of the track. Any negative index disables the tracks altogether.
     */
    fun WatchActivity.selectTrack(type: Int, index: Int) {
        val builder = myExoPlayer.trackSelector?.parameters?.buildUpon()

        /* First, clearing our subtitle track selection (This helps troubleshoot many issues */
        myExoPlayer.trackSelector?.parameters = builder?.clearOverridesOfType(type)!!.build()
        lastSubtitleOverride = null

        /* Now, selecting our subtitle track should one be selected */
        if (index >= 0) {
            when (type) {
                C.TRACK_TYPE_TEXT -> {
                    lastSubtitleOverride = TrackSelectionOverride(
                        media!!.subtitleTracks[index].trackGroup!!,
                        media!!.subtitleTracks[index].index
                    )
                }

                C.TRACK_TYPE_AUDIO -> {
                    lastSubtitleOverride = TrackSelectionOverride(
                        media!!.audioTracks[index].trackGroup!!,
                        media!!.audioTracks[index].index
                    )
                }
            }
            myExoPlayer.trackSelector?.parameters = builder.addOverride(lastSubtitleOverride!!).build()
        }

    }

    /** TODO: Get LoadControl through datastore */
    fun Context.buildExo(): ExoPlayer {
        /** LoadControl variables and building (Buffering Controller) **/
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        val useCustomBuffer = sp.getBoolean("use_custom_buffer_boolean", false)
        val maxBuffer = if (useCustomBuffer) (sp.getInt("player_max_buffer", 50) * 1000) else 30000
        var minBuffer = if (useCustomBuffer) (sp.getInt("player_min_buffer", 15) * 1000) else 15000
        val playbackBuffer = if (useCustomBuffer) (sp.getInt("player_playback_buffer", 2500)) else 2000
        if (minBuffer < (playbackBuffer + 500)) minBuffer = playbackBuffer + 500
        val loadControl = DefaultLoadControl.Builder().setBufferDurationsMs(minBuffer, maxBuffer, playbackBuffer, playbackBuffer + 500).build()

        /** Building EXOPLAYER with that loadcontrol **/
        val myExoPlayer = ExoPlayer.Builder(this)
            .setLoadControl(loadControl) /* We use the custom LoadControl we initialized before */
            .setRenderersFactory(
                DefaultRenderersFactory(this).setExtensionRendererMode(
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER /* We prefer extensions, such as FFmpeg */
                )
            ).build()

        /** Customizing ExoPlayer components **/
        myExoPlayer.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT /* Starter scaling */
        myExoPlayer.playWhenReady = false


        return myExoPlayer
    }
}