package app.player.vlc

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VlcDeferredPlayTest {
    @Test
    fun a_play_is_replayed_once_on_the_next_pause() {
        val play = VlcDeferredPlay()
        play.arm(revision = 5L)
        assertTrue(play.takeOnPaused(currentRevision = 5L))
        assertFalse(play.takeOnPaused(currentRevision = 5L))
    }

    @Test
    fun a_newer_command_cancels_the_replay() {
        val play = VlcDeferredPlay()
        play.arm(revision = 5L)
        assertFalse(play.takeOnPaused(currentRevision = 6L))
    }

    @Test
    fun a_cancelled_replay_does_not_come_back() {
        val play = VlcDeferredPlay()
        play.arm(revision = 5L)
        assertFalse(play.takeOnPaused(currentRevision = 6L))
        assertFalse(play.takeOnPaused(currentRevision = 5L))
    }

    @Test
    fun a_pause_without_an_armed_play_is_not_answered() {
        assertFalse(VlcDeferredPlay().takeOnPaused(currentRevision = 0L))
    }
}
