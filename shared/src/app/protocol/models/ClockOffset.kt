package app.protocol.models

/**
 * How far our clock sits from the server's, estimated from the timestamps already on the wire.
 *
 * Every `State` carries the server's send time and echoes back the time we sent ours. With the
 * moment we received it, that gives three of the four timestamps that NTP uses; the fourth
 * (when the server received ours) is not on the wire. Assuming both directions are equally
 * slow, Cristian's algorithm gives:
 *
 *     offset = serverSendTime + roundTrip / 2 - ourReceiveTime
 *
 * The assumption is wrong on any asymmetric link, and the error is bounded by half the
 * asymmetry. What makes the estimate usable anyway is the filter: the sample with the smallest
 * round trip in the window is the one that spent the least time queued, so it carries the least
 * of that error. NTP's clock filter works the same way, and picking the least-delayed sample is
 * why a clock filter beats an average.
 *
 * The room uses it to age each `State` by that message's own delay: see [messageAgeSeconds].
 * No timestamp method can see a delay that is always the same in one direction: the offset takes
 * half of it in, and the age computed from that offset is half the round trip again. What the
 * offset does see is a delay that changes from one message to the next, such as a `State` that
 * waited in a queue on its way down.
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

    /** Enough samples, and they agree closely enough to be trusted. The log reports it. */
    val settled: Boolean
        get() = window.size >= windowSize && dispersionSeconds <= MAX_SETTLED_DISPERSION_SECONDS

    /**
     * Enough samples that the least-delayed one is a fair estimate. Unlike [settled], it ignores
     * how far the other samples spread: a delayed message spreads them, and a delayed message is
     * exactly when [messageAgeSeconds] matters.
     */
    val usable: Boolean
        get() = window.size >= windowSize / 2

    /**
     * How long one message from the server took to arrive, from its own timestamps: its send time
     * on the server's clock and its receive time on ours. A message that waited in a queue on its
     * way down counts as older than the rest, which a smoothed average cannot show.
     *
     * Null until the estimate is [usable], and for an age that no link produces. That means a
     * clock was stepped after the estimate, and the caller then keeps its smoothed estimate.
     */
    fun messageAgeSeconds(serverSendTime: Double, ourReceiveTime: Double): Double? {
        if (!usable || serverSendTime <= 0.0) return null
        val age = ourReceiveTime + offsetSeconds - serverSendTime
        // A little below zero is the estimate's own error on a fast link. Far below is a stepped clock.
        if (age < -MAX_NEGATIVE_AGE_SECONDS || age > PingService.MAX_PLAUSIBLE_RTT_SECONDS) return null
        return age.coerceAtLeast(0.0)
    }

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

        // One pass, not three: the best sample and the spread of the window come out together.
        var best = window.first()
        var lowestOffset = best.offsetSeconds
        var highestOffset = best.offsetSeconds
        for (sample in window) {
            if (sample.roundTripSeconds < best.roundTripSeconds) best = sample
            if (sample.offsetSeconds < lowestOffset) lowestOffset = sample.offsetSeconds
            if (sample.offsetSeconds > highestOffset) highestOffset = sample.offsetSeconds
        }
        offsetSeconds = best.offsetSeconds
        bestRoundTripSeconds = best.roundTripSeconds
        dispersionSeconds = highestOffset - lowestOffset
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

        /** How far below zero an age may fall and still count as a fast message, not a stepped clock. */
        const val MAX_NEGATIVE_AGE_SECONDS = 0.1
    }
}
