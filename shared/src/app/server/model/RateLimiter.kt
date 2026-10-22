package app.server.model

/**
 * A token bucket that stops one hostile client from flooding a room.
 *
 * The hosted server bounds the message size, the roster and the playlist, and this bounds the
 * *rate*. Without it, a peer that sends chat in a loop reaches everyone in the room as fast as
 * the socket allows. That is griefing, not a compromise of the server, so a simple cap is enough.
 *
 * A bucket holds [capacity] tokens and refills at [refillPerSecond]. A burst of a few messages
 * costs nothing, which is what real people send. A sustained flood empties the bucket, and the
 * extra messages are dropped, not broadcast.
 *
 * Not thread-safe on purpose: every server-side call already runs on the one server dispatcher,
 * and a lock here would suggest otherwise.
 */
class RateLimiter(
    private val capacity: Double,
    private val refillPerSecond: Double,
    startedAtSeconds: Double,
) {
    private var tokens: Double = capacity
    private var lastRefillSeconds: Double = startedAtSeconds

    /** Takes one token if there is one. False means "over the limit, drop this". */
    fun allow(nowSeconds: Double): Boolean {
        refill(nowSeconds)
        if (tokens < 1.0) return false
        tokens -= 1.0
        return true
    }

    /** How many tokens are left, for a log line or a test. */
    fun available(nowSeconds: Double): Double {
        refill(nowSeconds)
        return tokens
    }

    private fun refill(nowSeconds: Double) {
        val elapsed = nowSeconds - lastRefillSeconds
        // A clock that went backwards must not create tokens or freeze the bucket.
        if (elapsed <= 0.0) {
            lastRefillSeconds = nowSeconds
            return
        }
        tokens = (tokens + elapsed * refillPerSecond).coerceAtMost(capacity)
        lastRefillSeconds = nowSeconds
    }

    companion object {
        /** Chat: a burst of ten, then two a second. Comfortable for a person, useless for a loop. */
        const val CHAT_BURST = 10.0
        const val CHAT_PER_SECOND = 2.0

        /** Playlist edits are rarer and more expensive, since each one reaches the whole room. */
        const val PLAYLIST_BURST = 5.0
        const val PLAYLIST_PER_SECOND = 0.5
    }
}
