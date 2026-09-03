package app.protocol

import app.protocol.models.PingService
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [PingService] — RTT smoothing (EMA) and asymmetry-aware `forwardDelay`.
 * These guard the latency-compensation math the `State` handler uses to bias the global
 * position by message age.
 *
 * The clock is injected, so every expectation below is exact. These tests used to build their
 * timestamps from the same wall clock they were asserting against, which forced loose ranges: a
 * loaded machine turned them red on its own, and the smoothing weight could be set to anything
 * without a single failure.
 */
class PingServiceTest {

    /** A clock the test moves by hand. Starts at a round number so the arithmetic below is readable. */
    private class FakeClock(var seconds: Double = 1_000_000.0)

    private fun serviceWithClock(clock: FakeClock) = PingService(nowSeconds = { clock.seconds })

    /** The timestamp a server would have echoed [rtt] seconds ago on [clock]. */
    private fun FakeClock.echoOf(rtt: Double) = seconds - rtt

    private fun assertClose(expected: Double, actual: Double, label: String, tolerance: Double = 1e-9) {
        assertTrue(abs(expected - actual) <= tolerance, "$label: expected $expected, was $actual")
    }

    @Test
    fun `null timestamp is a no-op`() {
        val ps = PingService()
        ps.receiveMessage(timestamp = null, senderRtt = 0.0)
        assertEquals(0.0, ps.rtt)
        assertEquals(0.0, ps.forwardDelay)
    }

    @Test
    fun `a timestamp of zero is an absent one, not the epoch`() {
        val clock = FakeClock()
        val ps = serviceWithClock(clock)
        ps.receiveMessage(timestamp = 0.0, senderRtt = 0.0)
        assertEquals(0.0, ps.forwardDelay, "a zero echo must not age positions by decades")
    }

    @Test
    fun `negative computed RTT is rejected`() {
        val clock = FakeClock()
        val ps = serviceWithClock(clock)
        ps.receiveMessage(timestamp = clock.seconds + 60.0, senderRtt = 0.0)
        assertEquals(0.0, ps.forwardDelay, "forwardDelay should still be the initial 0.0")
    }

    @Test
    fun `negative senderRtt is rejected`() {
        val clock = FakeClock()
        val ps = serviceWithClock(clock)
        ps.receiveMessage(timestamp = clock.echoOf(1.0), senderRtt = -0.1)
        assertEquals(0.0, ps.forwardDelay, "negative senderRtt should not update state")
    }

    @Test
    fun `an implausible round trip is dropped instead of smoothed in`() {
        val clock = FakeClock()
        val ps = serviceWithClock(clock)
        ps.receiveMessage(timestamp = clock.echoOf(1.0), senderRtt = 100.0)
        val settled = ps.forwardDelay

        // A stepped clock or a stale echo: minutes, not a network delay.
        ps.receiveMessage(timestamp = clock.echoOf(600.0), senderRtt = 100.0)
        assertClose(settled, ps.forwardDelay, "forwardDelay after an implausible sample")
    }

    @Test
    fun `first message seeds forwardDelay to half the observed RTT in the symmetric path`() {
        val clock = FakeClock()
        val ps = serviceWithClock(clock)
        // senderRtt high enough to keep us in the symmetric branch (forwardDelay = avrRtt / 2).
        ps.receiveMessage(timestamp = clock.echoOf(2.0), senderRtt = 100.0)
        assertClose(2.0, ps.rtt, "rtt")
        assertClose(1.0, ps.forwardDelay, "forwardDelay (= rtt / 2)")
    }

    @Test
    fun `first message applies asymmetry compensation when senderRtt is lower`() {
        val clock = FakeClock()
        val ps = serviceWithClock(clock)
        // rtt 2, senderRtt 0.5 → avrRtt/2 + (rtt - senderRtt) = 1.0 + 1.5 = 2.5.
        ps.receiveMessage(timestamp = clock.echoOf(2.0), senderRtt = 0.5)
        assertClose(2.5, ps.forwardDelay, "asymmetric forwardDelay")
    }

    /**
     * The smoothing constant itself. With the weight at 0 this lands on 2.5, with it at 1 on 0.5;
     * only 0.85 gives 0.8, so the number in the source cannot be changed unnoticed.
     */
    @Test
    fun `the moving average weight is 0_85`() {
        val clock = FakeClock()
        val ps = serviceWithClock(clock)
        ps.receiveMessage(timestamp = clock.echoOf(1.0), senderRtt = 100.0)
        ps.receiveMessage(timestamp = clock.echoOf(5.0), senderRtt = 100.0)

        // avrRtt = 1.0 * 0.85 + 5.0 * 0.15 = 1.6 → forwardDelay = 0.8
        assertClose(0.8, ps.forwardDelay, "forwardDelay after one spike")
    }

    @Test
    fun `EMA pulls a sudden spike only partly toward the new value`() {
        val clock = FakeClock()
        val ps = serviceWithClock(clock)
        ps.receiveMessage(timestamp = clock.echoOf(1.0), senderRtt = 100.0)
        val seededDelay = ps.forwardDelay
        ps.receiveMessage(timestamp = clock.echoOf(5.0), senderRtt = 100.0)

        assertTrue(ps.forwardDelay > seededDelay, "the spike should move it: was $seededDelay now ${ps.forwardDelay}")
        assertTrue(ps.forwardDelay < 2.5, "but nowhere near the un-smoothed half: ${ps.forwardDelay}")
    }

    @Test
    fun `asymmetric path adds exactly the difference between the two round trips`() {
        val clock = FakeClock()
        val symmetric = serviceWithClock(clock).also {
            it.receiveMessage(timestamp = clock.echoOf(1.0), senderRtt = 1.0)
            it.receiveMessage(timestamp = clock.echoOf(3.0), senderRtt = 3.0)
        }
        val asymmetric = serviceWithClock(clock).also {
            it.receiveMessage(timestamp = clock.echoOf(1.0), senderRtt = 1.0)
            it.receiveMessage(timestamp = clock.echoOf(3.0), senderRtt = 1.0)
        }

        // Both share the same avrRtt; the asymmetric one adds (rtt - senderRtt) = 2.
        assertClose(2.0, asymmetric.forwardDelay - symmetric.forwardDelay, "the asymmetry term")
    }

    @Test
    fun `symmetric path stays at half the moving average when senderRtt is greater than rtt`() {
        val clock = FakeClock()
        val ps = serviceWithClock(clock)
        ps.receiveMessage(timestamp = clock.echoOf(1.0), senderRtt = 1.0)
        ps.receiveMessage(timestamp = clock.echoOf(1.0), senderRtt = 10.0)

        // Both samples are 1s, so the average is 1 and forwardDelay is half of it.
        assertClose(0.5, ps.forwardDelay, "symmetric forwardDelay")
    }

    @Test
    fun `repeated identical RTTs converge on exactly half the RTT`() {
        val clock = FakeClock()
        val ps = serviceWithClock(clock)
        repeat(50) { ps.receiveMessage(timestamp = clock.echoOf(1.0), senderRtt = 100.0) }
        assertClose(0.5, ps.forwardDelay, "converged forwardDelay", tolerance = 1e-6)
    }
}
