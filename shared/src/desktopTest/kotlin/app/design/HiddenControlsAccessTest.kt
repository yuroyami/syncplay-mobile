package app.design

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import app.i18n.EnAppStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A screen reader user can always reach the room controls. Hidden controls fade to zero alpha,
 * and Compose reports such nodes as hidden, so something outside the fade must offer them back.
 */
class HiddenControlsAccessTest {

    @Test
    fun hiddenControlsOfferANamedActionThatShowsThem() = RoomRig.drive(television = false, withVideo = true) {
        val label = EnAppStrings.roomControlsHidden
        driver.frames(20)
        assertNull(spokenNodes().firstOrNull { nameOf(it) == label }, "No show action while the controls show")

        ui.visibleHUD.value = false
        driver.frames(30)
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
