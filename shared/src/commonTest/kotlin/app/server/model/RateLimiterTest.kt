package app.server.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The cap that keeps one client from drowning a room. */
class RateLimiterTest {

    @Test
    fun a_burst_within_the_bucket_is_allowed() {
        val limiter = RateLimiter(capacity = 5.0, refillPerSecond = 1.0, startedAtSeconds = 0.0)
        repeat(5) { assertTrue(limiter.allow(0.0), "message ${it + 1} of the burst") }
    }

    @Test
    fun the_message_past_the_burst_is_dropped() {
        val limiter = RateLimiter(capacity = 3.0, refillPerSecond = 1.0, startedAtSeconds = 0.0)
        repeat(3) { limiter.allow(0.0) }
        assertFalse(limiter.allow(0.0))
    }

    @Test
    fun waiting_earns_the_right_to_send_again() {
        val limiter = RateLimiter(capacity = 2.0, refillPerSecond = 1.0, startedAtSeconds = 0.0)
        repeat(2) { limiter.allow(0.0) }
        assertFalse(limiter.allow(0.0))
        assertTrue(limiter.allow(1.0), "one second earns one token")
    }

    @Test
    fun the_bucket_never_grows_past_its_capacity() {
        val limiter = RateLimiter(capacity = 3.0, refillPerSecond = 1.0, startedAtSeconds = 0.0)
        assertEquals(3.0, limiter.available(3600.0), "an hour of silence is still three")
    }

    @Test
    fun a_sustained_flood_settles_at_the_refill_rate() {
        val limiter = RateLimiter(capacity = 5.0, refillPerSecond = 2.0, startedAtSeconds = 0.0)
        var allowed = 0
        // Ten seconds of trying a hundred times a second.
        for (tick in 0 until 1000) {
            if (limiter.allow(tick / 100.0)) allowed++
        }
        // Five from the burst, then two a second for ten seconds.
        assertTrue(allowed in 24..26, "settled at $allowed, expected about 25")
    }

    @Test
    fun a_clock_that_goes_backwards_neither_mints_tokens_nor_freezes_the_bucket() {
        val limiter = RateLimiter(capacity = 2.0, refillPerSecond = 1.0, startedAtSeconds = 100.0)
        repeat(2) { limiter.allow(100.0) }
        assertFalse(limiter.allow(50.0), "going back in time must not refill")
        assertTrue(limiter.allow(51.0), "and the bucket must still refill from the new reading")
    }

    @Test
    fun the_chat_defaults_let_a_person_talk_and_stop_a_loop() {
        val limiter = RateLimiter(RateLimiter.CHAT_BURST, RateLimiter.CHAT_PER_SECOND, 0.0)
        // A person typing: ten messages over ten seconds, all through.
        for (i in 0 until 10) assertTrue(limiter.allow(i.toDouble()), "message $i")

        val loop = RateLimiter(RateLimiter.CHAT_BURST, RateLimiter.CHAT_PER_SECOND, 0.0)
        var through = 0
        repeat(100) { if (loop.allow(0.0)) through++ }
        assertEquals(10, through, "a hundred at once gets the burst and no more")
    }
}
