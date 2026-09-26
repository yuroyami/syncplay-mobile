package app.player

import kotlin.test.Test
import kotlin.test.assertEquals

class ChapterStillPositionTest {
    @Test
    fun aLongChapterGivesTheFrameThreeSecondsIn() {
        assertEquals(3_000, stillPositionMs(0, 60_000))
        assertEquals(63_000, stillPositionMs(60_000, null))
    }

    @Test
    fun aShortChapterGivesTheFrameFromItsMiddle() {
        assertEquals(11_000, stillPositionMs(10_000, 12_000))
    }

    @Test
    fun anEmptyOrReversedChapterGivesItsStart() {
        assertEquals(5_000, stillPositionMs(5_000, 5_000))
        assertEquals(5_000, stillPositionMs(5_000, 4_000))
    }
}
