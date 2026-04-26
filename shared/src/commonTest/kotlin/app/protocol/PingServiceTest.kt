package app.protocol

import app.protocol.models.PingService
import app.utils.generateTimestampMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [PingService] — RTT smoothing (EMA) and asymmetry-aware `forwardDelay`.
 * These guard the latency-compensation math the `State` handler uses to bias the global
 * position by message age.
 *
 * Precision note: [PingService.receiveMessage] reads `generateTimestampMillis()`
 * internally and accepts a `timestamp: Long` in **whole seconds**. That truncation
 * means tests can't pin RTT to a precise value — there's an unavoidable 0–1s drift
 * between when the test snapshots wall-clock and when the function call resolves it.
 * Tests therefore assert on ranges and relationships, not on exact computed values.
 */
class PingServiceTest {

    /** Build a `latencyCalculation` (whole-second timestamp) targeting roughly [secondsAgo] of RTT. */
    private fun timestampSecondsAgo(secondsAgo: Long): Long {
        val nowSeconds = generateTimestampMillis() / 1000L
        return nowSeconds - secondsAgo
    }

    @Test
    fun `null timestamp is a no-op`() {
        val ps = PingService()
        ps.receiveMessage(timestamp = null, senderRtt = 0.0)
        assertEquals(0.0, ps.rtt)
        assertEquals(0.0, ps.forwardDelay)
    }

    @Test
    fun `negative computed RTT is rejected`() {
        val ps = PingService()
        // Timestamp from ~60s in the future → rtt would be negative → early return.
        val futureTimestamp = generateTimestampMillis() / 1000L + 60
        ps.receiveMessage(timestamp = futureTimestamp, senderRtt = 0.0)
        assertEquals(0.0, ps.forwardDelay, "forwardDelay should still be the initial 0.0")
    }

    @Test
    fun `negative senderRtt is rejected`() {
        val ps = PingService()
        ps.receiveMessage(timestamp = timestampSecondsAgo(1), senderRtt = -0.1)
        assertEquals(0.0, ps.forwardDelay, "negative senderRtt should not update state")
    }

    @Test
    fun `first message seeds forwardDelay to half the observed RTT`() {
        val ps = PingService()
        ps.receiveMessage(timestamp = timestampSecondsAgo(2), senderRtt = 1.0)
        // Truncation → rtt ∈ [2.0, 3.0). forwardDelay = rtt / 2 ∈ [1.0, 1.5).
        assertInRange(ps.rtt, 2.0, 3.0, "rtt")
        assertInRange(ps.forwardDelay, 1.0, 1.5, "forwardDelay (= rtt / 2)")
    }

    @Test
    fun `EMA pulls a sudden spike partially toward the moving average`() {
        val ps = PingService()
        // senderRtt high enough to keep us in the symmetric branch
        // (forwardDelay = avrRtt / 2) — otherwise asymmetry inflates the reading.
        ps.receiveMessage(timestamp = timestampSecondsAgo(1), senderRtt = 100.0)
        val seededDelay = ps.forwardDelay
        ps.receiveMessage(timestamp = timestampSecondsAgo(5), senderRtt = 100.0)

        // After the spike, forwardDelay should grow but stay well below the spike's
        // half (~3.0) — that's the whole point of smoothing. Symmetric path means
        // forwardDelay = avrRtt / 2 = (old*0.85 + new*0.15) / 2.
        assertTrue(
            ps.forwardDelay > seededDelay,
            "forwardDelay should increase after a spike: was=$seededDelay now=${ps.forwardDelay}"
        )
        assertTrue(
            ps.forwardDelay < 3.0,
            "EMA should keep forwardDelay below the un-smoothed spike-half: got ${ps.forwardDelay}"
        )
    }

    @Test
    fun `asymmetric path adds extra delay when client RTT exceeds server RTT`() {
        val symmetric = PingService().also {
            it.receiveMessage(timestamp = timestampSecondsAgo(1), senderRtt = 1.0)
            // Symmetric second sample: rtt ≈ 3, senderRtt = 3 → forwardDelay = avrRtt / 2.
            it.receiveMessage(timestamp = timestampSecondsAgo(3), senderRtt = 3.0)
        }
        val asymmetric = PingService().also {
            it.receiveMessage(timestamp = timestampSecondsAgo(1), senderRtt = 1.0)
            // Asymmetric: rtt ≈ 3, senderRtt = 1 → forwardDelay = avrRtt / 2 + (rtt - senderRtt).
            it.receiveMessage(timestamp = timestampSecondsAgo(3), senderRtt = 1.0)
        }

        // Asymmetric should be larger by approximately (rtt - senderRtt) ≈ 2s. Allow drift.
        val delta = asymmetric.forwardDelay - symmetric.forwardDelay
        assertInRange(delta, 1.5, 2.5, "asymmetric extra delay should be ~ (rtt - senderRtt)")
    }

    @Test
    fun `symmetric path stays at half the moving average when senderRtt is greater than rtt`() {
        val ps = PingService()
        ps.receiveMessage(timestamp = timestampSecondsAgo(1), senderRtt = 1.0) // seed
        ps.receiveMessage(timestamp = timestampSecondsAgo(1), senderRtt = 10.0)

        // senderRtt >= rtt → falls into the `else` branch: forwardDelay = avrRtt / 2.
        // avrRtt should be close to rtt (which is ~1s), so forwardDelay should be ~0.5–1.0.
        assertInRange(ps.forwardDelay, 0.4, 1.1, "symmetric forwardDelay should be ~rtt/2")
    }

    @Test
    fun `repeated identical RTTs converge toward roughly half the RTT`() {
        val ps = PingService()
        // senderRtt = 100 keeps us in the symmetric branch on every sample, so the
        // result reflects pure EMA convergence (no asymmetry term).
        repeat(50) { ps.receiveMessage(timestamp = timestampSecondsAgo(1), senderRtt = 100.0) }
        // Each sample's rtt drifts within [1.0, 2.0] due to whole-second truncation,
        // so avrRtt converges to ~1.0–2.0 → forwardDelay to ~0.5–1.0.
        assertInRange(ps.forwardDelay, 0.4, 1.1, "forwardDelay should converge to ~rtt/2")
    }

    private fun assertInRange(actual: Double, low: Double, high: Double, label: String) {
        assertTrue(
            actual in low..high,
            "$label: expected in [$low, $high], got $actual"
        )
    }
}
