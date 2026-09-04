package app.protocol.models

import app.utils.SyncClock

/**
 * Measures network latency between client and server to keep playback in sync.
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
        /** EMA weight — higher value means slower, smoother adaptation to RTT changes. */
        private const val PING_MOVING_AVERAGE_WEIGHT = 0.85

        /** Beyond this, a "round trip" is a clock step or a stale echo, not a network delay. */
        const val MAX_PLAUSIBLE_RTT_SECONDS = 10.0
    }

    /** Current round-trip time in seconds. */
    var rtt: Double = 0.0

    /**
     * Estimated one-way delay (client → server) in seconds.
     * Accounts for upload/download asymmetry: if the server's measured RTT is lower
     * than ours, our upload is slower, so the extra difference is added.
     */
    var forwardDelay: Double = 0.0

    private var avrRtt: Double = 0.0

    /**
     * Called on each server ping response to update RTT and [forwardDelay].
     *
     * [timestamp] must arrive as full-precision seconds (Double) — rounding it to
     * whole seconds before the subtraction destroys the only signal it carries
     * (sub-second drift) and replaces it with up-to-±500 ms quantization noise.
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