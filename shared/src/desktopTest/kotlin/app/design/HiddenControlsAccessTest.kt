package app.design

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import app.i18n.EnAppStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Hidden controls stay reachable by a remote or screen reader, without selecting invisible buttons.
 */
class HiddenControlsAccessTest {

    @Test
    fun centerOnHiddenControlsPausesWithoutOpeningPreferences() = RoomRig.drive(withVideo = true) {
        viewmodel.protocol.noteExpectedPlaybackState(paused = false)
        ui.visibleHUD.value = false
        driver.frames(30)

        driver.press(Key.DirectionCenter)

        assertTrue(ui.visibleHUD.value, "Center restores the controls")
        assertFalse(viewmodel.protocol.expectedPlaying, "Center pauses rather than activating a hidden button")
        assertFalse(ui.tabCardRoomPreferences.value, "Center must not open hidden preferences")
        Thread.sleep(250)
        driver.frames(10)
        assertEquals(EnAppStrings.roomPlay, focusedName(), "Focus returns to the transport button")
    }

    @Test
    fun upAndDownRestoreHiddenControlsWithoutActivatingThem() {
        for (key in listOf(Key.DirectionUp, Key.DirectionDown)) {
            RoomRig.drive(withVideo = true) {
                viewmodel.protocol.noteExpectedPlaybackState(paused = false)
                ui.visibleHUD.value = false
                driver.frames(30)

                driver.press(key)

                assertTrue(ui.visibleHUD.value, "$key restores the controls")
                assertTrue(viewmodel.protocol.expectedPlaying, "$key does not change playback")
                assertFalse(ui.tabCardRoomPreferences.value, "$key does not open hidden preferences")
                assertFalse(ui.tabLock.value, "$key does not activate the hidden lock button")
            }
        }
    }

    @Test
    fun hiddenControlsOfferANamedActionThatShowsThem() = RoomRig.drive(television = false, withVideo = true) {
        val label = EnAppStrings.roomControlsHidden
        driver.frames(20)
        assertNull(spokenNodes().firstOrNull { nameOf(it) == label }, "No show action while the controls show")

        ui.visibleHUD.value = false
        driver.frames(30)
        assertNull(focused(), "Hiding touch controls must not select the video layer")
        val show = assertNotNull(
            spokenNodes().firstOrNull { it.config.getOrNull(SemanticsActions.OnClick) != null && nameOf(it) == label },
            "Hidden controls must leave a named action that shows them",
        )
        assertEquals(label, show.config[SemanticsActions.OnClick].label, "The action says what it does")
        DesignHarness.onUiThread { show.config[SemanticsActions.OnClick].action?.invoke() }
        driver.frames(10)
        assertTrue(ui.visibleHUD.value, "The action shows the controls")
    }

    /**
     * The idle timer hides the controls during playback. A screen reader sends no presses, so with
     * one running the controls must stay. The run without a screen reader proves the timer fires.
     */
    @Test
    fun aRunningScreenReaderKeepsTheControlsUpDuringPlayback() = RoomRig.withIdleSeconds(1) {
        for (screenReader in listOf(false, true)) {
            RoomRig.drive(television = false, withVideo = true, screenReader = screenReader) {
                viewmodel.playerManager.isNowPlaying.value = true
                driver.frames(10)
                Thread.sleep(1_600)
                driver.frames(10)
                assertEquals(screenReader, ui.visibleHUD.value, "Controls visible after the idle time, screen reader $screenReader")
            }
        }
    }
}
