package com.yuroyami.syncplay.managers.protocol

import com.yuroyami.syncplay.utils.generateTimestampMillis
import com.yuroyami.syncplay.utils.loggy

/**
 * Tracks network lag between you and the server.
 *
 * This helps Syncplay figure out how delayed your video should be so everyone
 * stays in sync, even when internet speeds are different.
 */
class PingService {
    companion object {
        /**
         * How much we trust old measurements vs new ones.
         * 0.85 means we trust the history more, making changes gradual and smooth.
         */
        private const val PING_MOVING_AVERAGE_WEIGHT = 0.85
    }

    /**
     * How long (in seconds) it takes for a message to go to the server and come back.
     */
    var rtt: Double = 0.0

    /**
     * How long (in seconds) it takes for a message to reach the server (one-way trip).
     * This accounts for your upload being slower/faster than your download.
     */
    var forwardDelay: Double = 0.0

    /**
     * Smoothed average of RTT. Helps avoid jumpiness from temporary lag spikes.
     */
    private var avrRtt: Double = 0.0

    /**
     * Updates lag measurements when we get a response from the server.
     *
     * @param timestamp When we originally sent the message (seconds)
     * @param senderRtt How long the server thinks our lag is (seconds)
     */
    fun receiveMessage(timestamp: Long?, senderRtt: Double) {
        // Calculate current round-trip time
        rtt = (generateTimestampMillis() - (timestamp?.times(1000L) ?: return)) / 1000.0
        loggy("Raw RTT calculated: $rtt seconds")

        if (rtt < 0 || senderRtt < 0) {
            loggy("Invalid RTT values - rtt: $rtt, senderRtt: $senderRtt")
            return
        }

        // Initialize average on first ping
        if (avrRtt == 0.0) {
            avrRtt = rtt
            loggy("Initial average RTT set: $avrRtt seconds")
        }

        // Smooth out the average using exponential moving average
        avrRtt = avrRtt * PING_MOVING_AVERAGE_WEIGHT + rtt * (1 - PING_MOVING_AVERAGE_WEIGHT)
        loggy("Updated average RTT: $avrRtt seconds")

        // Calculate one-way delay
        // If server's RTT is lower, our upload is slower - add the difference
        forwardDelay = if (senderRtt < rtt) {
            val asymmetricDelay = avrRtt / 2 + (rtt - senderRtt)
            loggy("Asymmetric network detected - forwardDelay: $asymmetricDelay seconds")
            asymmetricDelay
        } else {
            val symmetricDelay = avrRtt / 2
            loggy("Symmetric network - forwardDelay: $symmetricDelay seconds")
            symmetricDelay
        }
    }
}