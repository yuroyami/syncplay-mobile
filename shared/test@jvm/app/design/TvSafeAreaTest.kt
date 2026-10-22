package app.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import app.uicomponents.TvSafeArea
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A television cuts the outer edge of the picture away, so every screen but the room keeps its
 * content inside a margin. The harness renders at two pixels per dp.
 */
class TvSafeAreaTest {

    private fun measure(television: Boolean): Triple<Int, Int, Int> {
        var width = 0
        var height = 0
        var left = 0
        DesignHarness.drive(widthDp = 960, heightDp = 540, television = television, content = {
            TvSafeArea {
                Box(
                    Modifier.fillMaxSize().onGloballyPositioned {
                        width = it.size.width
                        height = it.size.height
                        left = it.positionInRoot().x.toInt()
                    }
                )
            }
        }) { frames(2) }
        return Triple(width, height, left)
    }

    @Test
    fun aTelevisionScreenStopsShortOfEveryEdge() {
        val (width, height, left) = measure(television = true)
        assertEquals((960 - 48 - 48) * 2, width, "48dp is kept clear on each side")
        assertEquals((540 - 27 - 27) * 2, height, "27dp is kept clear top and bottom")
        assertEquals(48 * 2, left, "the content starts after the left margin")
    }

    @Test
    fun everywhereElseTheScreenFillsTheWindow() {
        val (width, height, left) = measure(television = false)
        assertEquals(960 * 2, width)
        assertEquals(540 * 2, height)
        assertEquals(0, left)
    }
}
