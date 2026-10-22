package app.design

import androidx.compose.ui.input.key.Key
import app.i18n.EnAppStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

/**
 * What a remote does with the side panels. An open panel holds focus the way a dialog does, and
 * Back gives focus back to the control that opened the panel.
 */
class SidePanelFocusTest {

    /** Waits out the focus hand-off, which retries in real time, 60 ms apart. */
    private fun RoomRig.Room.settle() = repeat(7) {
        Thread.sleep(100)
        driver.frames(3)
    }

    @Test
    fun anOpenPanelKeepsFocusWhenADirectionWouldLeaveIt() = RoomRig.drive {
        settle()
        val outside = allNodes().map { it.id }.toSet()
        ui.toggleRoomPreferences(true)
        driver.frames(10)
        settle()
        val entered = assertNotNull(focused(), "Opening a panel moves focus into it")
        assertFalse(entered.id in outside, "Focus went into the panel, not to ${focusedName()}")
        for (key in listOf(Key.DirectionDown, Key.DirectionLeft, Key.DirectionUp, Key.DirectionRight)) {
            repeat(12) {
                driver.press(key)
                val now = assertNotNull(focused(), "Focus is kept after $key")
                assertFalse(now.id in outside, "$key left the open panel for ${focusedName()}")
            }
        }
    }

    @Test
    fun backFromARailPanelReturnsFocusToItsCell() = RoomRig.drive(solo = false) {
        settle()
        ui.toggleUserInfo(true)
        driver.frames(10)
        settle()
        ui.back()
        driver.frames(10)
        settle()
        assertEquals(EnAppStrings.roomCardTitleUserInfo, focusedName())
    }

    /** The control strip closes when one of its panels opens, so its own button takes focus back. */
    @Test
    fun backFromAToolPanelReturnsFocusToTheControlPanelButton() = RoomRig.drive(withVideo = true) {
        settle()
        ui.toggleControlPanel(true)
        driver.frames(10)
        settle()
        ui.toggleSeekTo(true)
        driver.frames(10)
        settle()
        ui.back()
        driver.frames(10)
        settle()
        assertEquals(EnAppStrings.roomControlPanel, focusedName())
    }

    @Test
    fun backFromTheAddMediaPanelReturnsFocusToTheAddKey() = RoomRig.drive(withVideo = true) {
        settle()
        ui.toggleAddMedia(true)
        driver.frames(10)
        settle()
        ui.back()
        driver.frames(10)
        settle()
        assertEquals(EnAppStrings.roomButtonDescAdd, focusedName())
    }

    /** The hand-off retries until it lands, and must stop there. Another try would undo the key. */
    @Test
    fun aKeyPressedWhileThePanelOpensKeepsItsMove() = RoomRig.drive {
        settle()
        val outside = allNodes().map { it.id }.toSet()
        ui.toggleRoomPreferences(true)
        driver.frames(10)
        val start = System.nanoTime()
        while (focused()?.id?.let { it !in outside } != true && System.nanoTime() - start < 1_000_000_000L) {
            Thread.sleep(10)
            driver.frames(1)
        }
        val first = assertNotNull(focusedName(), "Focus entered the panel")
        driver.press(Key.DirectionDown)
        val moved = focusedName()
        assertNotEquals(first, moved, "Down moved within the panel")
        settle()
        assertEquals(moved, focusedName(), "A late focus request undid the key")
    }
}
