package com.yuroyami.syncplay.player

import com.yuroyami.syncplay.settings.DataStoreKeys
import com.yuroyami.syncplay.settings.intFlow
import com.yuroyami.syncplay.utils.RoomUtils
import com.yuroyami.syncplay.watchroom.isSoloMode
import com.yuroyami.syncplay.watchroom.viewmodel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object PlayerUtils {

    fun getEngineForString(nameOfEngine: String): BasePlayer.ENGINE {
        return when (nameOfEngine) {
            "exo" -> BasePlayer.ENGINE.ANDROID_EXOPLAYER
            "mpv" -> BasePlayer.ENGINE.ANDROID_MPV
            "avplayer" -> BasePlayer.ENGINE.IOS_AVPLAYER
            else -> BasePlayer.ENGINE.ANDROID_EXOPLAYER
        }
    }

    /** This pauses playback on the main (necessary) thread **/
    fun pausePlayback() {
        viewmodel?.player?.pause()
        viewmodel?.roomCallback?.onPlayback(true)
    }

    /** This resumes playback on the main thread, and hides system UI **/
    fun playPlayback() {
        viewmodel?.player?.play()
        viewmodel?.roomCallback?.onPlayback(false)
    }

    fun seekBckwd() {
        viewmodel?.player?.playerScopeIO?.launch {
            val dec = intFlow(DataStoreKeys.PREF_INROOM_PLAYER_SEEK_BACKWARD_JUMP, 10).first()

            val currentMs = withContext(Dispatchers.Main) { viewmodel?.player!!.currentPositionMs() }
            var newPos = (currentMs) - (dec * 1000L)

            if (newPos < 0) { newPos = 0 }

            RoomUtils.sendSeek(newPos)
            viewmodel?.player?.seekTo(newPos)

            if (isSoloMode) {
                viewmodel?.seeks?.add(Pair(currentMs, newPos * 1000))
            }
        }
    }

    fun seekFrwrd() {
        viewmodel?.player?.playerScopeIO?.launch {
            val inc = intFlow(DataStoreKeys.PREF_INROOM_PLAYER_SEEK_FORWARD_JUMP, 10).first()

            val currentMs = withContext(Dispatchers.Main) { viewmodel?.player!!.currentPositionMs() }
            val newPos = (currentMs) + (inc * 1000L)
//            if (media != null) {
//                if (newPos > media?.fileDuration!!.toLong()) {
//                    newPos = media?.fileDuration!!.toLong()
//                }
//            }
            RoomUtils.sendSeek(newPos)
             viewmodel?.player?.seekTo(newPos)

            if (isSoloMode) {
                viewmodel?.seeks?.add(Pair((currentMs), newPos * 1000))
            }
        }
    }
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
    fun trackProgress() {
        if (viewmodel?.playerTrackerJob == null) {
            viewmodel?.playerTrackerJob = CoroutineScope(Dispatchers.Main).launch {
                while (true) {
                    if (viewmodel?.player?.isSeekable() == true) {
                        val progress = (viewmodel?.player?.currentPositionMs()?.div(1000L)) ?: 0L

                        /* Informing UI */
                        viewmodel?.timeCurrent?.longValue = progress

                        /* Informing protocol */
                        if (!isSoloMode) {
                            viewmodel?.p?.currentVideoPosition = progress.toDouble()
                        }
                    }
                    delay(500)
                }
            }
        }
    }
}