package app.design

import androidx.compose.ui.geometry.Offset
import app.i18n.EnAppStrings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The mouse pointer in the room. It hides with the controls during playback and comes back on the
 * first move. It never hides while it rests on a control, or while a menu is open.
 */
class RoomPointerTest {

    /** A point over the video with no control on it, in a room with no server (so no chat). */
    private val emptyVideo = Offset(480f, 540f)

    @Test
    fun aMouseMoveBringsBackHiddenControls() = RoomRig.drive(television = false, withVideo = true) {
        viewmodel.playerManager.isNowPlaying.value = true
        ui.visibleHUD.value = false
        driver.frames(10)
        driver.hover(emptyVideo)
        driver.hover(emptyVideo + Offset(40f, 0f))
        assertTrue(ui.visibleHUD.value, "A mouse move shows the controls")
    }

    @Test
    fun aPointerThatDoesNotMoveBringsNothingBack() = RoomRig.drive(television = false, withVideo = true) {
        viewmodel.playerManager.isNowPlaying.value = true
        driver.hover(emptyVideo)
        ui.visibleHUD.value = false
        driver.frames(10)
        // What the scene sends when the layout under a still pointer changes: a move to the same place.
        driver.hover(emptyVideo)
        assertFalse(ui.visibleHUD.value, "A move of no distance is not the person moving the mouse")
    }

    @Test
    fun thePointerHidesWithTheControlsAndComesBackOnAMove() = RoomRig.withIdleSeconds(1) {
        RoomRig.drive(television = false, withVideo = true) {
            viewmodel.playerManager.isNowPlaying.value = true
            driver.hover(emptyVideo)
            Thread.sleep(1_600)
            driver.frames(10)
            assertFalse(ui.visibleHUD.value, "The idle timer hides the controls")
            assertTrue(ui.pointerHidden.value, "The pointer hides with the controls")

            driver.hover(emptyVideo + Offset(40f, 0f))
            assertTrue(ui.visibleHUD.value, "A move brings the controls back")
            assertFalse(ui.pointerHidden.value, "A move brings the pointer back")
        }
    }

    @Test
    fun theControlsStayWhileThePointerRestsOnThem() = RoomRig.withIdleSeconds(1) {
        RoomRig.drive(television = false, withVideo = true) {
            viewmodel.playerManager.isNowPlaying.value = true
            driver.frames(10)
            val key = assertNotNull(allNodes().firstOrNull { nameOf(it) == EnAppStrings.roomPause }, "The pause key")
            driver.hover(boundsOf(key).center)
            Thread.sleep(1_600)
            driver.frames(10)
            assertTrue(ui.visibleHUD.value, "A pointer that rests on a control holds the controls")

            driver.hover(emptyVideo)
            Thread.sleep(1_600)
            driver.frames(10)
            assertFalse(ui.visibleHUD.value, "Off the controls, the idle timer runs again")
        }
    }

    @Test
    fun anOpenMenuHoldsTheControlsAndThePointer() = RoomRig.withIdleSeconds(1) {
        RoomRig.drive(television = false, withVideo = true) {
            viewmodel.playerManager.isNowPlaying.value = true
            ui.railActionsExpanded.value = true
            driver.hover(emptyVideo)
            Thread.sleep(1_600)
            driver.frames(10)
            assertTrue(ui.visibleHUD.value, "The controls stay while the rail menu is open")
            assertFalse(ui.pointerHidden.value, "The pointer stays while the rail menu is open")
        }
    }
}
