package app.player.kite

import io.github.yuroyami.kiteplayer.PlaybackStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * KitePlayer's half of the fix for the room that paused itself: a failure is a stop the engine
 * makes by itself, so it is marked and stays local. A buffering stall is not a stop at all, and
 * the end of the file is the one stop the room must hear about.
 */
class KiteStatusMirrorTest {

    @Test
    fun aFailureIsALocalStop() {
        assertEquals(KiteStatusMirror(playing = false, buffering = false, ownStop = true), mirrorOf(PlaybackStatus.Failed))
    }

    @Test
    fun bufferingStillCountsAsPlaying() {
        assertEquals(KiteStatusMirror(playing = true, buffering = true, ownStop = false), mirrorOf(PlaybackStatus.Buffering))
        assertEquals(KiteStatusMirror(playing = null, buffering = true, ownStop = false), mirrorOf(PlaybackStatus.Opening))
    }

    @Test
    fun theEndAndAPauseReachTheRoom() {
        for (status in listOf(PlaybackStatus.Ended, PlaybackStatus.Paused, PlaybackStatus.Idle)) {
            assertEquals(KiteStatusMirror(playing = false, buffering = false, ownStop = false), mirrorOf(status), "$status")
        }
        assertEquals(KiteStatusMirror(playing = true, buffering = false, ownStop = false), mirrorOf(PlaybackStatus.Playing))
    }
}
