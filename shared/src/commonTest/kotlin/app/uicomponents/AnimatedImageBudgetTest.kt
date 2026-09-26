package app.uicomponents

import androidx.compose.ui.unit.IntSize
import app.uicomponents.AnimatedImageBudget.MAX_DECODED_BYTES
import app.uicomponents.AnimatedImageBudget.decodeSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnimatedImageBudgetTest {
    @Test
    fun aSmallImageKeepsItsSize() {
        assertEquals(IntSize(200, 150), decodeSize(200, 150, frames = 30))
    }

    @Test
    fun aLargeImageShrinksToTheTileEdgeOnItsShortSide() {
        assertEquals(IntSize(768, 512), decodeSize(1200, 800, frames = 1))
    }

    @Test
    fun aLongAnimationShrinksUntilItsFramesFitTheBudget() {
        val size = decodeSize(480, 480, frames = 200)!!
        // Rounding to whole pixels may pass the budget by a hair.
        assertTrue(200L * size.width * size.height * 4 <= MAX_DECODED_BYTES * 101 / 100)
        assertTrue(size.width < 480)
    }

    @Test
    fun overTheLimitsIsRefused() {
        assertNull(decodeSize(480, 480, frames = AnimatedImageBudget.MAX_FRAMES + 1), "too many frames")
        assertNull(decodeSize(4000, 4000, frames = 1), "too many pixels in one frame")
        assertNull(decodeSize(1000, 80, frames = 400), "would need frames below the smallest edge")
        assertNull(decodeSize(0, 100, frames = 1), "no size")
    }
}
