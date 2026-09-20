package app.room.ui.chat

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals

/** Where the chat image menu opens: under the image when it fits, over it when not, never off screen. */
class BelowOrAboveTest {

    private val window = IntSize(1000, 2000)
    private val menu = IntSize(300, 200)
    private val gap = 10

    private fun place(anchor: IntRect, window: IntSize = this.window, direction: LayoutDirection = LayoutDirection.Ltr) =
        BelowOrAbove(gap).calculatePosition(anchor, window, direction, menu)

    @Test
    fun opensUnderTheImageWhenItFits() {
        assertEquals(IntOffset(100, 510), place(IntRect(100, 300, 400, 500)))
    }

    @Test
    fun opensOverTheImageNearTheBottom() {
        // 1900 + 10 + 200 passes 2000, so the menu goes above: 1700 - 10 - 200.
        assertEquals(IntOffset(100, 1490), place(IntRect(100, 1700, 400, 1900)))
    }

    @Test
    fun staysInsideTheWindowSideways() {
        assertEquals(700, place(IntRect(900, 300, 1000, 500)).x)
        assertEquals(0, place(IntRect(-50, 300, 100, 500)).x)
    }

    @Test
    fun rightToLeftAlignsTheEndEdges() {
        assertEquals(IntOffset(100, 510), place(IntRect(100, 300, 400, 500), direction = LayoutDirection.Rtl))
    }

    @Test
    fun aWindowSmallerThanTheMenuDoesNotThrow() {
        assertEquals(IntOffset(0, 0), place(IntRect(0, 50, 100, 100), window = IntSize(200, 150)))
    }
}
