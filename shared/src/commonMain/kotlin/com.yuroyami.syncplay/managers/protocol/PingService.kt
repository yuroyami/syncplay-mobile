package com.yuroyami.syncplay.managers.protocol

import com.yuroyami.syncplay.utils.generateTimestampMillis

/**
 * Service for measuring and tracking network latency in the Syncplay protocol.
 *
 * Calculates round-trip time (RTT) and forward delay using timestamps exchanged with
 * the server. Uses exponential moving average to smooth out latency fluctuations and
 * provide more stable synchronization.
 *
 * The forward delay calculation accounts for asymmetric network conditions where
 * upload and download speeds may differ, which is critical for accurate playback
 * synchronization.
 */
class PingService {
    companion object {
        /**
         * Weight factor for exponential moving average calculation.
         * Higher values (closer to 1.0) give more weight to historical measurements,
         * providing smoother but less responsive latency tracking.
         * Value: 0.85 (85% historical, 15% current measurement)
         */
        private const val PING_MOVING_AVERAGE_WEIGHT = 0.85
    }

    /**
     * Current round-trip time in seconds.
     * Time for a message to travel to the server and back.
     */
    var rtt: Double = 0.0

    /**
     * Estimated one-way forward delay in seconds.
     * The time it takes for a message to reach the server (half the trip).
     * Adjusted for asymmetric network conditions using sender's RTT.
     */
    var forwardDelay: Double = 0.0

    /**
     * Exponentially smoothed average RTT in seconds.
     * Provides a stable baseline for latency calculations.
     */
    private var avrRtt: Double = 0.0

    /**
     * Processes a ping response message from the server and updates latency metrics.
     *
     * Calculates current RTT from the timestamp, updates the moving average, and
     * computes forward delay accounting for network asymmetry. If the sender's RTT
     * is less than ours, it indicates slower upload than download, requiring
     * adjustment to the forward delay calculation.
     *
     * @param timestamp The original timestamp (in milliseconds) when the message was sent
     * @param senderRtt The server's measured RTT for this client in seconds
     */
    fun receiveMessage(timestamp: Long?, senderRtt: Double) {
        rtt = (generateTimestampMillis() - (timestamp ?: return)) / 1000.0
        if (rtt < 0 || senderRtt < 0) return

        if (avrRtt == 0.0) avrRtt = rtt

        avrRtt = avrRtt * PING_MOVING_AVERAGE_WEIGHT + rtt * (1 - PING_MOVING_AVERAGE_WEIGHT)

        forwardDelay = if (senderRtt < rtt) {
            avrRtt / 2 + (rtt - senderRtt)
        } else {
            avrRtt / 2
        }
    }
}