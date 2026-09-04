package app.utils

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The seam itself: installing a clock must actually redirect every reading, and reset must undo it. */
class SyncClockTest {

    @AfterTest
    fun teardown() = SyncClock.reset()

    @Test
    fun an_installed_clock_answers_all_three_readings() {
        val clock = TestClock(millis = 1_700_000_000_000L)
        clock.install()

        assertEquals(1_700_000_000_000L, SyncClock.nowMillis())
        assertEquals(1_700_000_000.0, SyncClock.nowSeconds())
        assertEquals(1_700_000_000_000L, SyncClock.now().toEpochMilliseconds())

        clock.advanceSeconds(2.5)
        assertEquals(1_700_000_002.5, SyncClock.nowSeconds())
    }

    @Test
    fun reset_puts_the_wall_clock_back() {
        TestClock(millis = 1L).install()
        assertEquals(1L, SyncClock.nowMillis())

        SyncClock.reset()
        assertTrue(SyncClock.nowMillis() > 1_600_000_000_000L, "should be a real epoch again")
    }
}
