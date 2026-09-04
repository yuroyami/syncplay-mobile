package app.protocol.models

/**
 * How far our clock sits from the server's, estimated from the timestamps already on the wire.
 *
 * Every `State` carries the server's send time and echoes back the time we sent ours. With the
 * moment we received it, that is three of Cristian's four points, and the fourth (when the
 * server received ours) is not on the wire. Assuming the two directions are equally slow:
 *
 *     offset = serverSendTime + roundTrip / 2 - ourReceiveTime
 *
 * The assumption is wrong on any asymmetric link, and the error is bounded by half the
 * asymmetry. What makes the estimate usable anyway is the filter: the sample with the smallest
 * round trip in the window is the one that spent the least time queued, so it carries the least
 * of that error. This is the standard NTP trick and it is why a clock filter beats an average.
 *
 * **Nothing corrects playback from this yet.** It is measured and logged so a real two-device
 * session can show whether the numbers are sane before the sync decision is moved onto them.
 * Replacing the threshold ladder is the step after that, and it needs hardware.
 */
class ClockOffsetEstimator(
    /** How many samples the filter keeps. NTP uses eight for the same reason. */
    private val windowSize: Int = 8,
) {

    /** One round trip, as seen from here. */
    data class Sample(val roundTripSeconds: Double, val offsetSeconds: Double)

    private val window = ArrayDeque<Sample>()

    /** The best estimate: the offset from the least-delayed sample in the window. */
    var offsetSeconds: Double = 0.0
        private set

    /** The round trip that estimate came from. Small is good. */
    var bestRoundTripSeconds: Double = Double.MAX_VALUE
        private set

    /** How far apart the offsets in the window are. Large means the estimate is not settled. */
    var dispersionSeconds: Double = 0.0
        private set

    /** Enough samples, and they agree closely enough to be worth acting on. */
    val settled: Boolean
        get() = window.size >= windowSize && dispersionSeconds <= MAX_SETTLED_DISPERSION_SECONDS

    /**
     * Feeds one exchange.
     *
     * @param ourSendTime the timestamp we sent, echoed back by the server
     * @param serverSendTime the server's own timestamp on this message
     * @param ourReceiveTime when this message reached us
     */
    fun observe(ourSendTime: Double, serverSendTime: Double, ourReceiveTime: Double) {
        val roundTrip = ourReceiveTime - ourSendTime
        // A negative or implausible round trip is a stepped clock or a stale echo, not a network
        // delay. The same bound the RTT smoothing uses.
        if (roundTrip < 0.0 || roundTrip > PingService.MAX_PLAUSIBLE_RTT_SECONDS) return
        if (ourSendTime <= 0.0 || serverSendTime <= 0.0) return

        window.addLast(Sample(roundTrip, serverSendTime + roundTrip / 2 - ourReceiveTime))
        while (window.size > windowSize) window.removeFirst()

        val best = window.minBy { it.roundTripSeconds }
        offsetSeconds = best.offsetSeconds
        bestRoundTripSeconds = best.roundTripSeconds
        dispersionSeconds =
            (window.maxOf { it.offsetSeconds } - window.minOf { it.offsetSeconds })
    }

    /** Forgets everything. A new socket is a new path, so the old window means nothing. */
    fun reset() {
        window.clear()
        offsetSeconds = 0.0
        bestRoundTripSeconds = Double.MAX_VALUE
        dispersionSeconds = 0.0
    }

    companion object {
        /** Offsets spread wider than this are still settling, or the link is unstable. */
        const val MAX_SETTLED_DISPERSION_SECONDS = 0.25
    }
}
