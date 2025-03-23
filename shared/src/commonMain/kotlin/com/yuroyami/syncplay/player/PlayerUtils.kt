package com.yuroyami.syncplay.player

import com.yuroyami.syncplay.settings.DataStoreKeys
import com.yuroyami.syncplay.settings.valueSuspendingly
import com.yuroyami.syncplay.utils.RoomUtils
import com.yuroyami.syncplay.watchroom.isSoloMode
import com.yuroyami.syncplay.watchroom.viewmodel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object PlayerUtils {

    /** This pauses playback on the main (necessary) thread **/
    fun pausePlayback() {
        if (viewmodel?.background == true) return;
        viewmodel?.player?.pause()
        viewmodel?.roomCallback?.onPlayback(true)
    }

    /** This resumes playback on the main thread, and hides system UI **/
    fun playPlayback() {
        if (viewmodel?.background == true) return;
        viewmodel?.player?.play()
        viewmodel?.roomCallback?.onPlayback(false)
    }

    fun seekBckwd() {
        viewmodel?.player?.playerScopeIO?.launch {
            val dec = valueSuspendingly(DataStoreKeys.PREF_INROOM_PLAYER_SEEK_BACKWARD_JUMP, 10)

            val currentMs = withContext(Dispatchers.Main) { viewmodel?.player!!.currentPositionMs() }
            var newPos = ( (currentMs) - (dec * 1000L)).coerceIn(0, viewmodel?.media?.fileDuration?.toLong() ?: 0)

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
            val inc = valueSuspendingly(DataStoreKeys.PREF_INROOM_PLAYER_SEEK_FORWARD_JUMP, 10)

            val currentMs = withContext(Dispatchers.Main) { viewmodel?.player!!.currentPositionMs() }
            val newPos =( (currentMs) + (inc * 1000L)).coerceIn(0, viewmodel?.media?.fileDuration?.toLong() ?: 0)

            RoomUtils.sendSeek(newPos)
             viewmodel?.player?.seekTo(newPos)

            if (isSoloMode) {
                viewmodel?.seeks?.add(Pair((currentMs), newPos * 1000))
            }
        }
    }

    /** Tracks progress CONTINUOUSLY and updates it to UI (and server, if no solo mode) */
    fun CoroutineScope.trackProgress(intervalMillis: Long) {
        if (viewmodel?.playerTrackerJob == null) {
            viewmodel?.playerTrackerJob = launch {
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
                    delay(intervalMillis)
                }
            }
        }
    }
}