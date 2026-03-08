package app.protocol.models

import app.utils.generateTimestampMillis
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Measures network latency between client and server to keep playback in sync.
 *
 * Computes RTT and one-way forward delay using an exponential moving average,
 * which smooths out temporary lag spikes. [forwardDelay] is used by the protocol
 * to compensate for message age when applying server position updates.
 */
class PingService {
    companion object {
        /** EMA weight — higher value means slower, smoother adaptation to RTT changes. */
        private const val PING_MOVING_AVERAGE_WEIGHT = 0.85
    }

    /** Current round-trip time in seconds. */
    var rtt: Double = 0.0

    /**
     * Estimated one-way delay (client → server) in seconds.
     * Accounts for upload/download asymmetry: if the server's measured RTT is lower
     * than ours, our upload is slower, so the extra difference is added.
     */
    var forwardDelay: Double = 0.0

    private val avrRtt = MutableStateFlow(0.0)

    /** Called on each server ping response to update RTT and [forwardDelay]. */
    fun receiveMessage(timestamp: Long?, senderRtt: Double) {
        rtt = (generateTimestampMillis() - (timestamp?.times(1000L) ?: return)) / 1000.0
        if (rtt < 0 || senderRtt < 0) return

        if (avrRtt.value == 0.0) {
            avrRtt.value = rtt
            forwardDelay = rtt / 2
            return
        }

        avrRtt.value = avrRtt.value * PING_MOVING_AVERAGE_WEIGHT + rtt * (1 - PING_MOVING_AVERAGE_WEIGHT)

        forwardDelay = if (senderRtt < rtt) {
            avrRtt.value / 2 + (rtt - senderRtt)
        } else {
            avrRtt.value / 2
        }
    }
}