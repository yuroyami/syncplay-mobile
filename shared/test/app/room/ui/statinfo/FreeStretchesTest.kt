package app.room.ui.statinfo

import kotlin.test.Test
import kotlin.test.assertEquals

/** The status strip of the portrait room places its two parts in the stretches of the top edge that no camera cutout covers. */
class FreeStretchesTest {
    @Test
    fun withoutAHoleTheWholeSpanIsFree() {
        assertEquals(listOf(10f..90f), freeStretches(10f, 90f, emptyList(), clearance = 4f, minimum = 20f))
    }

    @Test
    fun aHoleInTheMiddleLeavesTwoStretchesWithClearance() {
        assertEquals(listOf(10f..36f, 64f..90f), freeStretches(10f, 90f, listOf(40f..60f), clearance = 4f, minimum = 20f))
    }

    @Test
    fun aStretchTooNarrowForTheTextIsDropped() {
        // A hole near the start corner leaves 6 units before it: too narrow for the minimum of 20.
        assertEquals(listOf(34f..90f), freeStretches(10f, 90f, listOf(20f..30f), clearance = 4f, minimum = 20f))
    }

    @Test
    fun twoHolesLeaveThreeStretches() {
        val stretches = freeStretches(0f, 300f, listOf(50f..70f, 200f..220f), clearance = 5f, minimum = 20f)
        assertEquals(listOf(0f..45f, 75f..195f, 225f..300f), stretches)
    }

    @Test
    fun whenNoStretchIsLeftTheWholeSpanStays() {
        assertEquals(listOf(10f..90f), freeStretches(10f, 90f, listOf(15f..85f), clearance = 4f, minimum = 20f))
    }
}
