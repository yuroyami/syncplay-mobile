package com.yuroyami.syncplay.managers.protocol

import com.yuroyami.syncplay.utils.generateTimestampMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlin.time.TimeMark
import kotlin.time.TimeSource

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

        sealed interface ConnectionState {
            data class Connected(val pingMs: Int) : ConnectionState
            object Disconnected : ConnectionState
        }
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
     * This is "PING" shown to users.
     */
    private val lastUpdatedMark = MutableStateFlow<TimeMark?>(null)
    private val avrRtt = MutableStateFlow(0.0)
    /**
     * Updates lag measurements when we get a response from the server.
     *
     * @param timestamp When we originally sent the message (seconds)
     * @param senderRtt How long the server thinks our lag is (seconds)
     */
    fun receiveMessage(timestamp: Long?, senderRtt: Double) {
        // Calculate current round-trip time
        rtt = (generateTimestampMillis() - (timestamp?.times(1000L) ?: return)) / 1000.0
        if (rtt < 0 || senderRtt < 0) return

        // Initialize average on first ping
        if (avrRtt.value == 0.0) {
            avrRtt.value = rtt
            forwardDelay = rtt / 2
            return
        }

        // Smooth out the average using exponential moving average
        avrRtt.value = avrRtt.value * PING_MOVING_AVERAGE_WEIGHT + rtt * (1 - PING_MOVING_AVERAGE_WEIGHT)

        // Calculate one-way delay
        // If server's RTT is lower, our upload is slower - add the difference
        forwardDelay = if (senderRtt < rtt) {
            val asymmetricDelay = avrRtt.value / 2 + (rtt - senderRtt)
            asymmetricDelay
        } else {
            val symmetricDelay = avrRtt.value / 2
            symmetricDelay
        }

        lastUpdatedMark.value = TimeSource.Monotonic.markNow()
    }

    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(500)
        }
    }

    val connectionState =
        combine(avrRtt, lastUpdatedMark, ticker) { avr, mark, _ ->
            val disconnected = mark == null || mark.elapsedNow().inWholeSeconds > 5

            if (disconnected) {
                ConnectionState.Disconnected
            } else {
                ConnectionState.Connected((avr * 1000).toInt())
            }
        }
}