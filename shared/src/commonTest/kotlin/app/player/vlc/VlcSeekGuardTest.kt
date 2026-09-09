package app.player.vlc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VlcSeekGuardTest {
    @Test
    fun rapid_seeks_keep_only_the_latest_target() {
        val guard = VlcSeekGuard()
        guard.seek(10_000L, 100L)
        assertEquals(10_000L, guard.sample(1_000L, 110L))

        guard.seek(20_000L, 200L)
        assertEquals(20_000L, guard.sample(10_000L, 210L))

        guard.seek(30_000L, 300L)
        assertEquals(30_000L, guard.sample(20_000L, 1_300L))
        assertEquals(30_500L, guard.sample(30_500L, 1_301L))
    }

    @Test
    fun convergence_releases_the_target_before_the_deadline() {
        val guard = VlcSeekGuard()
        guard.seek(20_000L, 0L)
        assertEquals(19_000L, guard.sample(19_000L, 10L))
        assertEquals(18_000L, guard.sample(18_000L, 20L))
    }

    @Test
    fun elapsed_time_releases_a_target_even_without_intervening_events() {
        val guard = VlcSeekGuard()
        guard.seek(20_000L, 50L)
        assertEquals(20_000L, guard.sample(5_000L, 1_050L))
        assertEquals(5_000L, guard.sample(5_000L, 1_051L))
    }

    @Test
    fun invalid_native_samples_cannot_extend_the_deadline() {
        val guard = VlcSeekGuard()
        guard.seek(20_000L, 0L)
        assertEquals(20_000L, guard.sample(-1L, 500L))
        assertNull(guard.sample(-1L, 1_001L))
        assertNull(guard.sample(-1L, 2_000L))
    }

    @Test
    fun a_paused_seek_waits_for_resume_then_has_a_bounded_playing_deadline() {
        val guard = VlcSeekGuard()
        guard.seek(20_000L, 0L)
        assertEquals(20_000L, guard.sample(5_000L, 10L, playing = false))
        assertEquals(20_000L, guard.sample(5_000L, 10_000L, playing = false))
        assertEquals(20_000L, guard.sample(5_000L, 10_010L, playing = true))
        assertEquals(20_000L, guard.sample(5_000L, 11_010L, playing = true))
        assertEquals(5_000L, guard.sample(5_000L, 11_011L, playing = true))
    }

    @Test
    fun a_paused_seek_does_not_need_an_intermediate_sample_to_defer_its_deadline() {
        val guard = VlcSeekGuard()
        guard.seek(20_000L, 0L, playing = false)
        // The tracker may be asleep/backgrounded for the whole pause. Its first sample
        // after resume must start the playing deadline instead of expiring immediately.
        assertEquals(20_000L, guard.sample(5_000L, 10_000L, playing = true))
        assertEquals(20_000L, guard.sample(5_000L, 11_000L, playing = true))
        assertEquals(5_000L, guard.sample(5_000L, 11_001L, playing = true))
    }

    @Test
    fun a_paused_seek_releases_when_the_native_position_reaches_it() {
        val guard = VlcSeekGuard()
        guard.seek(20_000L, 0L)
        assertEquals(20_000L, guard.sample(5_000L, 10_000L, playing = false))
        assertEquals(20_000L, guard.sample(20_000L, 10_100L, playing = false))
        assertEquals(21_500L, guard.sample(21_500L, 10_200L, playing = true))
    }

    @Test
    fun zero_is_valid_including_a_seek_back_to_the_start() {
        val guard = VlcSeekGuard()
        assertEquals(0L, guard.sample(0L, 0L))
        guard.seek(0L, 10L)
        assertEquals(0L, guard.sample(50_000L, 20L))
        assertEquals(0L, guard.sample(0L, 30L))
        assertEquals(250L, guard.sample(250L, 40L))
    }

    @Test
    fun changing_media_discards_the_previous_seek() {
        val guard = VlcSeekGuard()
        guard.seek(20_000L, 0L)
        guard.reset()
        assertNull(guard.sample(-1L, 10L))
        assertEquals(0L, guard.sample(0L, 20L))
    }

    @Test
    fun long_media_positions_are_valid_and_distance_does_not_overflow() {
        val guard = VlcSeekGuard()
        val threeDaysMs = 3L * 24L * 60L * 60L * 1_000L
        assertEquals(threeDaysMs, guard.sample(threeDaysMs, 0L))
        guard.seek(Long.MAX_VALUE, 10L)
        assertEquals(Long.MAX_VALUE, guard.sample(0L, 20L))
        assertEquals(Long.MAX_VALUE - 500L, guard.sample(Long.MAX_VALUE - 500L, 30L))
    }
}
