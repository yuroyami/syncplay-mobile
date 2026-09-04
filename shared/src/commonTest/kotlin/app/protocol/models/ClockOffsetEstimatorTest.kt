package app.protocol.models

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The clock offset, from the timestamps already on the wire. Nothing corrects playback from it
 * yet; these tests are what will make moving the sync decision onto it a small step.
 */
class ClockOffsetEstimatorTest {

    /**
     * One exchange over a link with a known offset and delay.
     *
     * @param offset how far ahead the server's clock is
     * @param up how long our message took to arrive
     * @param down how long the reply took to reach us
     */
    private fun ClockOffsetEstimator.exchange(offset: Double, up: Double, down: Double, at: Double = 1000.0) {
        observe(
            ourSendTime = at,
            serverSendTime = at + offset + up,
            ourReceiveTime = at + up + down,
        )
    }

    @Test
    fun a_symmetric_link_gives_the_offset_exactly() {
        val e = ClockOffsetEstimator()
        e.exchange(offset = 2.0, up = 0.05, down = 0.05)
        assertEquals(2.0, e.offsetSeconds, 1e-9)
        assertEquals(0.1, e.bestRoundTripSeconds, 1e-9)
    }

    @Test
    fun an_asymmetric_link_is_wrong_by_half_the_asymmetry_and_no_more() {
        val e = ClockOffsetEstimator()
        // Upload takes 200ms, download 20ms: 180ms of asymmetry, so at most 90ms of error.
        e.exchange(offset = 0.0, up = 0.2, down = 0.02)
        assertTrue(abs(e.offsetSeconds) <= 0.09 + 1e-9, "error was ${e.offsetSeconds}")
    }

    @Test
    fun the_least_delayed_sample_wins_which_is_the_whole_point() {
        val e = ClockOffsetEstimator()
        // Three congested exchanges, then one clean one. The clean one should decide.
        e.exchange(offset = 5.0, up = 0.9, down = 0.1, at = 1000.0)
        e.exchange(offset = 5.0, up = 0.8, down = 0.1, at = 1010.0)
        e.exchange(offset = 5.0, up = 0.02, down = 0.02, at = 1020.0)
        assertEquals(5.0, e.offsetSeconds, 1e-9)
        assertEquals(0.04, e.bestRoundTripSeconds, 1e-9)
    }

    @Test
    fun the_window_forgets_old_samples() {
        val e = ClockOffsetEstimator(windowSize = 2)
        e.exchange(offset = 5.0, up = 0.01, down = 0.01, at = 1000.0)
        e.exchange(offset = 5.0, up = 0.5, down = 0.5, at = 1010.0)
        e.exchange(offset = 5.0, up = 0.4, down = 0.4, at = 1020.0)
        assertEquals(0.8, e.bestRoundTripSeconds, 1e-9, "the fast first sample has fallen out")
    }

    @Test
    fun a_negative_round_trip_is_a_stepped_clock_and_is_dropped() {
        val e = ClockOffsetEstimator()
        e.observe(ourSendTime = 2000.0, serverSendTime = 2000.0, ourReceiveTime = 1000.0)
        assertEquals(0.0, e.offsetSeconds, "nothing was recorded")
    }

    @Test
    fun an_implausible_round_trip_is_dropped_on_the_same_bound_as_the_rtt_smoothing() {
        val e = ClockOffsetEstimator()
        e.exchange(offset = 0.0, up = 30.0, down = 30.0)
        assertEquals(0.0, e.offsetSeconds)
    }

    @Test
    fun a_missing_timestamp_is_dropped_rather_than_read_as_the_epoch() {
        val e = ClockOffsetEstimator()
        e.observe(ourSendTime = 0.0, serverSendTime = 1000.0, ourReceiveTime = 1000.1)
        e.observe(ourSendTime = 1000.0, serverSendTime = 0.0, ourReceiveTime = 1000.1)
        assertEquals(0.0, e.offsetSeconds)
    }

    @Test
    fun it_is_not_settled_until_the_window_is_full_and_the_samples_agree() {
        val e = ClockOffsetEstimator(windowSize = 4)
        repeat(3) { i -> e.exchange(offset = 1.0, up = 0.02, down = 0.02, at = 1000.0 + i * 10) }
        assertFalse(e.settled, "three samples is not a window")

        e.exchange(offset = 1.0, up = 0.02, down = 0.02, at = 1030.0)
        assertTrue(e.settled)
    }

    @Test
    fun samples_that_disagree_wildly_are_never_called_settled() {
        val e = ClockOffsetEstimator(windowSize = 3)
        e.exchange(offset = 1.0, up = 0.02, down = 0.02, at = 1000.0)
        e.exchange(offset = 4.0, up = 0.02, down = 0.02, at = 1010.0)
        e.exchange(offset = 9.0, up = 0.02, down = 0.02, at = 1020.0)
        assertTrue(e.dispersionSeconds > ClockOffsetEstimator.MAX_SETTLED_DISPERSION_SECONDS)
        assertFalse(e.settled)
    }

    @Test
    fun a_reset_forgets_the_path_because_a_new_socket_is_a_new_path() {
        val e = ClockOffsetEstimator()
        e.exchange(offset = 5.0, up = 0.02, down = 0.02)
        e.reset()
        assertEquals(0.0, e.offsetSeconds)
        assertFalse(e.settled)
    }
}
