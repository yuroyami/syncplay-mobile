package app.design

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import app.i18n.EnAppStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The add key of the landscape room before a file loads. A tap grows the key into the card of
 * routes. The card grows over the keys beside it, so they keep their place.
 */
class RoomAddCardTest {
    private val viewKeys = setOf(
        EnAppStrings.roomFullScreen,
        EnAppStrings.roomFullScreenExit,
        EnAppStrings.roomRotationLock,
        EnAppStrings.roomRotationUnlock,
    )

    private fun RoomRig.Room.viewKey(): SemanticsNode? = allNodes().firstOrNull { nameOf(it) in viewKeys }

    @Test
    fun theViewKeyKeepsItsPlaceWhileTheCardGrowsAndFolds() =
        RoomRig.drive(solo = false, television = false, withVideo = false, canHoldLandscape = true) {
            driver.frames(20)
            val before: Rect = boundsOf(assertNotNull(viewKey(), "The view key shows"))

            ui.expandMediaAdd()
            driver.frames(4)
            // Each step is 16 ms, so these checks fall inside the growth and after its end.
            val growing = viewKey()?.let(::boundsOf)
            driver.frames(40)
            assertTrue(ui.mediaAddExpanded.value, "The card is open")
            assertEquals(before, growing, "The view key stays put while the card grows")

            // The card's close key works, although the card reaches past the bar it sits in.
            val close = assertNotNull(
                allNodes().filter { nameOf(it) == EnAppStrings.actionClose }.maxByOrNull { boundsOf(it).right },
                "The card's close key shows",
            )
            driver.tap(boundsOf(close).center)
            driver.frames(40)
            assertFalse(ui.mediaAddExpanded.value, "The close key folds the card")
            assertEquals(before, viewKey()?.let(::boundsOf), "The view key is where it was")
        }
}
