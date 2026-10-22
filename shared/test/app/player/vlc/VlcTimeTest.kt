package app.player.vlc

import kotlin.test.Test
import kotlin.test.assertEquals

class VlcTimeTest {
    @Test
    fun microseconds_round_to_the_nearest_millisecond() {
        assertEquals(0L, vlcMillisFromMicros(0L))
        assertEquals(0L, vlcMillisFromMicros(499L))
        assertEquals(1L, vlcMillisFromMicros(500L))
        assertEquals(1L, vlcMillisFromMicros(1_499L))
        assertEquals(2L, vlcMillisFromMicros(1_500L))
        assertEquals(61_962L, vlcMillisFromMicros(61_961_704L))
        assertEquals(7_200_000L, vlcMillisFromMicros(7_200_000_000L))
    }

    @Test
    fun a_negative_value_stays_a_missing_value() {
        assertEquals(-1L, vlcMillisFromMicros(-1L))
        assertEquals(-1L, vlcMillisFromMicros(-700L))
    }

    @Test
    fun the_largest_value_does_not_overflow() {
        assertEquals(Long.MAX_VALUE / 1_000L + 1L, vlcMillisFromMicros(Long.MAX_VALUE))
    }
}
