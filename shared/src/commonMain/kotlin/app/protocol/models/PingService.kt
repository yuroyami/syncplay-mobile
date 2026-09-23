package app.protocol.models

import app.utils.SyncClock

/**
 * Measures network latency between client and server to keep playback in sync. A port of the
 * reference client's `PingService` (protocols.py).
 *
 * Computes RTT and one-way forward delay using an exponential moving average,
 * which smooths out temporary lag spikes. [forwardDelay] is used by the protocol
 * to compensate for message age when applying server position updates.
 */
class PingService(
    /**
     * The clock, in fractional seconds. Injectable for tests only: a test that has to build its
     * timestamps from the same wall clock it is asserting against fails on a loaded machine and
     * cannot pin the smoothing constant at all.
     */
    private val nowSeconds: () -> Double = { SyncClock.nowSeconds() },
) {
    companion object {
        /** Moving-average weight: a higher value adapts to RTT changes more slowly and smoothly. */
        private const val PING_MOVING_AVERAGE_WEIGHT = 0.85

        /** Beyond this, a "round trip" is a clock step or a stale echo, not a network delay. */
        const val MAX_PLAUSIBLE_RTT_SECONDS = 10.0
    }

    /** The latest round-trip time, in seconds (the running average after an implausible sample). */
    var rtt: Double = 0.0

    /**
     * Estimated age of a server message when it arrives, in seconds: half the smoothed round
     * trip, plus the amount by which our last round trip exceeds the server's measured one.
     */
    var forwardDelay: Double = 0.0

    private var avrRtt: Double = 0.0

    /**
     * Updates RTT and [forwardDelay] from a server `State` that carries ping data. [timestamp]
     * is our own send time echoed back by the server, and [senderRtt] is the server's RTT.
     *
     * [timestamp] must arrive as full-precision seconds (Double). Rounding it to whole seconds
     * before the subtraction destroys the only signal it carries (sub-second drift) and
     * replaces it with up to ±500 ms of quantization noise.
     */
    fun receiveMessage(timestamp: Double?, senderRtt: Double) {
        // A missing timestamp arrives as null, or as 0 from a sender that coerced it: neither is a
        // clock reading, and subtracting 0 from "now" would age every position by decades.
        if (timestamp == null || timestamp <= 0.0) return
        rtt = nowSeconds() - timestamp
        if (rtt < 0 || senderRtt < 0) return
        // A stepped clock, or an echo from a much older session, can hand us a "round trip" of
        // minutes. Half of it would then be added to every position we compute, so the sample is
        // dropped rather than smoothed in, and rtt keeps the running average instead.
        if (rtt > MAX_PLAUSIBLE_RTT_SECONDS) {
            rtt = avrRtt
            return
        }

        if (avrRtt == 0.0) avrRtt = rtt
        avrRtt = avrRtt * PING_MOVING_AVERAGE_WEIGHT + rtt * (1 - PING_MOVING_AVERAGE_WEIGHT)

        forwardDelay = if (senderRtt < rtt) {
            avrRtt / 2 + (rtt - senderRtt)
        } else {
            avrRtt / 2
        }
    }
}