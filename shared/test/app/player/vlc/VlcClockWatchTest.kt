package app.player.vlc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VlcClockWatchTest {
    /** Samples every 250 ms, as the tracker does, and returns the first repair target. */
    private fun VlcClockWatch.run(
        fromMs: Long,
        toMs: Long,
        native: (nowMs: Long) -> Long,
        playing: Boolean = true,
        rate: Float = 1f,
    ): Long? {
        var now = fromMs
        while (now <= toMs) {
            sample(native(now), now, playing, rate)?.let { return it }
            now += 250L
        }
        return null
    }

    @Test
    fun a_clock_frozen_after_a_paused_seek_asks_for_one_repair_at_the_expected_position() {
        val watch = VlcClockWatch()
        watch.seek(45_000L)
        // Paused for 5 s: no play time, no stall.
        assertNull(watch.run(0L, 5_000L, native = { 45_000L }, playing = false))
        // Play: the clock jumps to the target plus the pause time and stays there.
        val repair = watch.run(5_250L, 10_000L, native = { 50_173L })
        // Play time: 250 ms up to the jump, then the 1.5 s stall.
        assertEquals(45_000L + 1_750L, repair)
        // Only one repair per seek.
        assertNull(watch.run(10_250L, 20_000L, native = { 50_173L }))
    }

    @Test
    fun a_running_clock_never_asks_for_a_repair() {
        val watch = VlcClockWatch()
        watch.seek(10_000L)
        assertNull(watch.run(0L, 30_000L, native = { 10_000L + it }))
    }

    @Test
    fun a_healthy_clock_ends_the_watch_so_a_later_stall_is_not_repaired() {
        val watch = VlcClockWatch()
        watch.seek(10_000L)
        assertNull(watch.run(0L, 3_000L, native = { 10_000L + it }))
        // A stall after the clock proved healthy is not the seek bug, for example a stream that
        // stops without a buffering event.
        assertNull(watch.run(3_250L, 10_000L, native = { 13_000L }))
    }

    @Test
    fun pause_time_counts_neither_as_play_time_nor_as_a_stall() {
        val watch = VlcClockWatch()
        watch.seek(0L)
        assertNull(watch.run(0L, 1_000L, native = { 7_000L }))
        assertNull(watch.run(1_250L, 60_000L, native = { 7_000L }, playing = false))
        val repair = watch.run(60_250L, 70_000L, native = { 7_000L })
        // Play time: the first sample has no time before it, then 1 s, then 1.5 s after the pause.
        assertEquals(1_000L + 1_500L, repair)
    }

    @Test
    fun the_expected_position_follows_the_playback_rate() {
        val watch = VlcClockWatch()
        watch.seek(20_000L)
        val repair = watch.run(0L, 10_000L, native = { 25_000L }, rate = 2f)
        assertEquals(20_000L + 3_000L, repair)
    }

    @Test
    fun a_newer_seek_and_a_reset_replace_the_watch() {
        val watch = VlcClockWatch()
        watch.seek(10_000L)
        assertNull(watch.run(0L, 1_000L, native = { 12_000L }))
        watch.seek(30_000L)
        assertEquals(30_000L + 1_500L, watch.run(1_250L, 10_000L, native = { 33_000L }))

        watch.seek(40_000L)
        watch.reset()
        assertNull(watch.run(0L, 10_000L, native = { 41_000L }))
    }

    @Test
    fun without_a_seek_there_is_nothing_to_watch() {
        val watch = VlcClockWatch()
        assertNull(watch.run(0L, 10_000L, native = { 5_000L }))
    }

    @Test
    fun a_missing_native_clock_is_not_a_stall() {
        val watch = VlcClockWatch()
        watch.seek(10_000L)
        assertNull(watch.run(0L, 10_000L, native = { -1L }))
    }
}
