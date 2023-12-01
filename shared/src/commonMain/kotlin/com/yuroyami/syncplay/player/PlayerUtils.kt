package com.yuroyami.syncplay.player

import com.yuroyami.syncplay.datastore.DataStoreKeys
import com.yuroyami.syncplay.datastore.ds
import com.yuroyami.syncplay.datastore.intFlow
import com.yuroyami.syncplay.utils.RoomUtils
import com.yuroyami.syncplay.watchroom.isSoloMode
import com.yuroyami.syncplay.watchroom.media
import com.yuroyami.syncplay.watchroom.p
import com.yuroyami.syncplay.watchroom.player
import com.yuroyami.syncplay.watchroom.seeks
import com.yuroyami.syncplay.watchroom.timeCurrent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
    fun pausePlayback() {
        player?.pause()
        //TODO: updatePiPParams()

    }

    /** This resumes playback on the main thread, and hides system UI **/
    fun playPlayback() {
        player?.play()
        //TODO: updatePiPParams()
        //TODO: hideSystemUI(true)
    }

    fun seekBckwd() {
        player?.playerScopeIO?.launch {
            val dec = DataStoreKeys.DATASTORE_INROOM_PREFERENCES.ds().intFlow(DataStoreKeys.PREF_INROOM_PLAYER_SEEK_BACKWARD_JUMP, 10).first()

            val currentMs = player!!.currentPositionMs()
            var newPos = (currentMs) - dec * 1000

            if (newPos < 0) { newPos = 0 }

            RoomUtils.sendSeek(newPos)
            player?.seekTo(newPos)

            if (isSoloMode) {
                seeks.add(Pair(currentMs, newPos * 1000))
            }
        }
    }

    fun seekFrwrd() {
        player?.playerScopeIO?.launch {
            val inc = DataStoreKeys.DATASTORE_INROOM_PREFERENCES.ds().intFlow(DataStoreKeys.PREF_INROOM_PLAYER_SEEK_FORWARD_JUMP, 10).first()

            val currentMs = player!!.currentPositionMs()
            var newPos = (currentMs) + inc * 1000
            if (media != null) {
                if (newPos > media?.fileDuration!!.toLong()) {
                    newPos = media?.fileDuration!!.toLong()
                }
            }
            RoomUtils.sendSeek(newPos)
            player?.seekTo(newPos)
            if (isSoloMode) {
                seeks.add(Pair((currentMs), newPos * 1000))
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
                        if (!isSoloMode) {
                            p.currentVideoPosition = progress.toDouble()
                        }
                    }
                    delay(1000)
                }
            }
        }
    }
}