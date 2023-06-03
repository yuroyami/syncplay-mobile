package app.utils

import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.CaptionStyleCompat
import app.activities.WatchActivity
import app.player.highlevel.HighLevelPlayer
import app.utils.MiscUtils.hideSystemUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


/** Methods exclusive to ExoPlayer functionality */
object PlayerUtils {

    /** This pauses playback on the main (necessary) thread **/
    fun WatchActivity.pausePlayback() {
        runOnUiThread {
            player?.pause()
            updatePiPParams()
        }
    }

    /** This resumes playback on the main thread, and hides system UI **/
    fun WatchActivity.playPlayback() {
        runOnUiThread {
            player?.play()
            updatePiPParams()
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
        runOnUiThread {
            //TODO
            if (player?.engine == HighLevelPlayer.ENGINE.EXOPLAYER) {
                player?.exoView?.subtitleView?.setStyle(captionStyle)
                player?.exoView?.subtitleView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            }
        }
    }

    var isTrackingProgress = false

    /** Tracks progress CONTINUOUSLY and updates it to UI (and server, if no solo mode) */
    fun WatchActivity.trackProgress() {
        if (isTrackingProgress) return

        lifecycleScope.launch(Dispatchers.Main) {
            while (true) {
                isTrackingProgress = true
                if (player?.isSeekable() == true) {
                    val progress = (player?.getPositionMs()?.div(1000L)) ?: 0L

                    /* Informing UI */
                    timeCurrent.longValue = progress

                    /* Informing protocol */
                    if (!isSoloMode()) {
                        p.currentVideoPosition = progress.toDouble()
                    }
                }
                delay(1000)
            }
        }
    }

    /** Used for applying any registered subtitle/audio selecions after, for example, going back
     * to the RoomActivity after closing the app for far too long (restoring instance state).*/
    fun WatchActivity.reapplyTrackChoices() {
        if (player?.engine == HighLevelPlayer.ENGINE.EXOPLAYER) {

            /* We need to cast MediaController to ExoPlayer since they're roughly the same */
            player?.analyzeTracks(media ?: return)

            player?.exoplayer?.apply {
                val builder = trackSelectionParameters.buildUpon()

                var newParams = builder.build()

                if (currentTrackChoices.lastAudioOverride != null) {
                    newParams = newParams.buildUpon().addOverride(currentTrackChoices.lastAudioOverride ?: return).build()
                }
                if (currentTrackChoices.lastSubtitleOverride != null) {
                    newParams = newParams.buildUpon().addOverride(currentTrackChoices.lastSubtitleOverride ?: return).build()
                }
                trackSelectionParameters = newParams
            }
        }

        if (player?.engine == HighLevelPlayer.ENGINE.MPV) {
            val subIndex = currentTrackChoices.subtitleSelectionIndexMpv
            val audioIndex = currentTrackChoices.audioSelectionIndexMpv

            with(player ?: return) {
                if (subIndex != null) selectTrack(C.TRACK_TYPE_TEXT, subIndex)
                if (audioIndex != null) selectTrack(C.TRACK_TYPE_AUDIO, audioIndex)
            }
        }
    }
}