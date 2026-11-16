package app.design

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import app.i18n.EnAppStrings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The edge guard of the swipe gestures over the video (#137). A swipe that starts at the top or
 * bottom edge of the screen belongs to the system, so it must not change the volume, at any speed.
 */
@OptIn(ExperimentalComposeUiApi::class)
class GestureGuardTest {
    // 844 x 390 dp at 2 px per dp. The top guard is 8 percent of the height, about 62 px.
    private val widthDp = 844
    private val heightDp = 390
    private val rightHalfX = 1300f

    /**
     * A finger goes down at [fromY], jumps [firstStep] px with its first sample, then moves on in
     * 12 px samples to [toY]. Returns whether the volume readout showed while the finger was down.
     */
    private fun RoomRig.Room.swipeDown(fromY: Float, toY: Float, firstStep: Float): Boolean = swipe(fromY, toY, firstStep)

    /** As [swipeDown], in either direction: [firstStep] and the 12 px samples follow the sign of the move. */
    private fun RoomRig.Room.swipe(fromY: Float, toY: Float, firstStep: Float): Boolean {
        ui.visibleHUD.value = false
        driver.frames(20)
        DesignHarness.onUiThread {
            driver.scene.sendPointerEvent(PointerEventType.Press, Offset(rightHalfX, fromY), type = PointerType.Touch)
        }
        driver.frames(1)
        val sign = if (toY >= fromY) 1f else -1f
        var y = fromY + sign * firstStep
        while (sign * (toY - y) >= 0f) {
            val at = Offset(rightHalfX, y)
            DesignHarness.onUiThread { driver.scene.sendPointerEvent(PointerEventType.Move, at, type = PointerType.Touch) }
            driver.frames(1)
            y += sign * 12f
        }
        driver.frames(10)
        // The readout reads "Volume 42%".
        val shown = allNodes().any { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith(EnAppStrings.roomVolume + " ") && it.text.endsWith("%") } == true
        }
        DesignHarness.onUiThread {
            driver.scene.sendPointerEvent(PointerEventType.Release, Offset(rightHalfX, toY), type = PointerType.Touch)
        }
        driver.frames(5)
        return shown
    }

    private fun inLandscapeRoom(block: RoomRig.Room.() -> Unit) =
        RoomRig.drive(solo = false, television = false, withVideo = true, widthDp = widthDp, heightDp = heightDp, drive = block)

    @Test
    fun aSwipeInTheMiddleChangesTheVolume() = inLandscapeRoom {
        assertTrue(swipeDown(fromY = 300f, toY = 600f, firstStep = 12f), "A swipe that starts in the middle is a volume swipe")
    }

    @Test
    fun aSlowSwipeFromTheTopEdgeIsIgnored() = inLandscapeRoom {
        assertFalse(swipeDown(fromY = 4f, toY = 400f, firstStep = 12f), "A slow pull from the top edge belongs to the system")
    }

    @Test
    fun aFastSwipeFromTheTopEdgeIsIgnored() = inLandscapeRoom {
        // The first sample after the touch is already 200 px down, as in a quick flick.
        assertFalse(swipeDown(fromY = 4f, toY = 500f, firstStep = 200f), "A fast pull from the top edge belongs to the system")
    }

    @Test
    fun aFastSwipeFromTheBottomEdgeIsIgnored() = inLandscapeRoom {
        // The bottom edge holds the home gesture. The screen is 780 px tall.
        assertFalse(swipe(fromY = 776f, toY = 300f, firstStep = 200f), "A fast push from the bottom edge belongs to the system")
    }

    @Test
    fun aSlowSwipeFromTheBottomEdgeIsIgnored() = inLandscapeRoom {
        assertFalse(swipe(fromY = 776f, toY = 400f, firstStep = 12f), "A slow push from the bottom edge belongs to the system")
    }
}
