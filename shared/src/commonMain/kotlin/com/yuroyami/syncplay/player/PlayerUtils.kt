package com.yuroyami.syncplay.player

import com.yuroyami.syncplay.watchroom.isSoloMode
import com.yuroyami.syncplay.watchroom.p
import com.yuroyami.syncplay.watchroom.player
import com.yuroyami.syncplay.watchroom.timeCurrent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object PlayerUtils {

    fun getEngineForString(nameOfEngine: String): ENGINE {
        return when (nameOfEngine) {
            "exo" -> ENGINE.ANDROID_EXOPLAYER
            "mpv" -> ENGINE.ANDROID_MPV
            "avplayer" -> ENGINE.IOS_AVPLAYER
            else -> ENGINE.ANDROID_EXOPLAYER
        }
    }

    /** This pauses playback on the main (necessary) thread **/
//    fun WatchActivity.pausePlayback() {
//        runOnUiThread {
//            player?.pause()
//            updatePiPParams()
//        }
//    }
//
//    /** This resumes playback on the main thread, and hides system UI **/
//    fun WatchActivity.playPlayback() {
//        runOnUiThread {
//            player?.play()
//            updatePiPParams()
//            hideSystemUI(true)
//        }
//    }
//
//    /** Changes the subtitle appearance given the size and captionStyle (otherwise, default will be used) */
//    fun WatchActivity.retweakSubtitleAppearance(
//        size: Float = 16f,
//        captionStyle: CaptionStyleCompat = CaptionStyleCompat(
//            Color.WHITE, Color.TRANSPARENT, Color.TRANSPARENT,
//            CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, Color.BLACK, Typeface.DEFAULT_BOLD
//        ),
//    ) {
//        runOnUiThread {
//            //TODO
//            if (player?.engine == HighLevelPlayer.ENGINE.EXOPLAYER) {
//                player?.exoView?.subtitleView?.setStyle(captionStyle)
//                player?.exoView?.subtitleView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, size)
//            }
//        }
//    }

    /** Tracks progress CONTINUOUSLY and updates it to UI (and server, if no solo mode) */
    var playerTrackerJob: Job? = null
    fun trackProgress() {
        if (playerTrackerJob == null) {
            playerTrackerJob = CoroutineScope(Dispatchers.Main).launch {
                while (true) {
                    if (player?.isSeekable() == true) {
                        val progress = (player?.currentPositionMs()?.div(1000L)) ?: 0L

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
    }
}