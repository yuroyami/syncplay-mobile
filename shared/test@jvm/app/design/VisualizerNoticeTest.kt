package app.design

import androidx.compose.ui.input.key.Key
import app.i18n.EnAppStrings
import app.preferences.Pref
import app.preferences.Preferences.AUDIO_VISUALIZATION
import app.preferences.Preferences.AUDIO_VIZ_NOTICE_SEEN
import app.preferences.Preferences.REDUCE_MOTION
import app.preferences.set
import app.preferences.settings.Render
import app.preferences.settings.withControl
import app.preferences.value
import app.room.ui.misc.AfterVisualizerNotice
import app.room.ui.misc.VisualizerNoticeControl
import app.room.ui.misc.rememberVisualizerCalm
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The audio visualizer can flash. It asks once before it first turns on, it draws nothing before
 * that answer, and it follows the Reduce motion switch, which takes its flashes away.
 */
class VisualizerNoticeTest {

    private val title = EnAppStrings.roomVisualizerNoticeTitle
    private val rowTitle = EnAppStrings.uisettingKiteAudioVizTitle

    @Test
    fun theVisualizerFollowsTheReduceMotionSwitch() = withPrefs(seen = true, on = true, calm = false) {
        var calm: Boolean? = null
        DesignHarness.drive(widthDp = 400, content = { calm = rememberVisualizerCalm() }) {
            assertEquals(false, calm, "the desktop has no platform setting, so the switch decides")
            runBlocking { REDUCE_MOTION.set(true) }
            await { REDUCE_MOTION.value() }
            frames(5)
            assertEquals(true, calm, "turning Reduce motion on calms the visualizer")
        }
    }

    @Test
    fun theSettingsRowAsksBeforeTheVisualizerFirstTurnsOn() = withPrefs(seen = false, on = false) {
        // A remote: Down reaches the row, and the notice opens on its confirming action.
        DesignHarness.drive(widthDp = 400, content = { row() }) {
            press(Key.DirectionDown)
            press(Key.DirectionCenter)
            assertTrue(shows(title), "the row opens the notice")
            assertFalse(AUDIO_VISUALIZATION.value(), "and the visualizer stays off while it asks")
            press(Key.DirectionCenter)
            await { AUDIO_VISUALIZATION.value() && AUDIO_VIZ_NOTICE_SEEN.value() }
            frames(5)
            assertFalse(shows(title), "turning on closes the notice")
        }
    }

    @Test
    fun aCancelledNoticeLeavesTheVisualizerOff() = withPrefs(seen = false, on = false) {
        DesignHarness.drive(widthDp = 400, television = false, content = { row() }) {
            click(rowTitle)
            assertTrue(shows(title), "the row opens the notice")
            click(EnAppStrings.cancel)
            frames(10)
            assertFalse(shows(title), "cancelling closes the notice")
            assertFalse(AUDIO_VISUALIZATION.value(), "the visualizer stays off")
            assertFalse(AUDIO_VIZ_NOTICE_SEEN.value(), "and the notice asks again next time")
        }
    }

    @Test
    fun anAcceptedNoticeIsNotAskedAgain() = withPrefs(seen = true, on = false) {
        DesignHarness.drive(widthDp = 400, television = false, content = { row() }) {
            click(rowTitle)
            await { AUDIO_VISUALIZATION.value() }
            assertFalse(shows(title), "the row turns the visualizer on at once")
        }
    }

    @Test
    fun turningTheVisualizerOffNeverAsks() = withPrefs(seen = false, on = true) {
        DesignHarness.drive(widthDp = 400, television = false, content = { row() }) {
            click(rowTitle)
            await { !AUDIO_VISUALIZATION.value() }
            assertFalse(shows(title), "turning off needs no notice")
        }
    }

    @Test
    fun aVisualizerTurnedOnBeforeTheNoticeWaitsForIt() = withPrefs(seen = false, on = true) {
        var drawn = false
        DesignHarness.drive(widthDp = 400, television = false, content = { AfterVisualizerNotice { drawn = true } }) {
            frames(5)
            assertTrue(shows(title), "a visualizer that was already on shows the notice first")
            assertFalse(drawn, "and draws nothing before the answer")
            click(EnAppStrings.roomVisualizerNoticeTurnOn)
            await { AUDIO_VIZ_NOTICE_SEEN.value() }
            frames(5)
            assertTrue(drawn, "turning on lets it draw")
            assertTrue(AUDIO_VISUALIZATION.value(), "and it stays on")
        }
    }

    @Test
    fun cancellingTheWaitingNoticeTurnsTheVisualizerOff() = withPrefs(seen = false, on = true) {
        var drawn = false
        DesignHarness.drive(widthDp = 400, television = false, content = { AfterVisualizerNotice { drawn = true } }) {
            frames(5)
            assertTrue(shows(title), "a visualizer that was already on shows the notice first")
            click(EnAppStrings.cancel)
            await { !AUDIO_VISUALIZATION.value() }
            assertFalse(drawn, "it never drew")
            assertFalse(AUDIO_VIZ_NOTICE_SEEN.value(), "and the notice asks again next time")
        }
    }

    /** The visualizer's row as the room's settings show it. */
    @androidx.compose.runtime.Composable
    private fun row() = AUDIO_VISUALIZATION.withControl(VisualizerNoticeControl).Render()

    /** Runs [body] with the three switches set, and puts back what was there before. */
    private fun withPrefs(seen: Boolean, on: Boolean, calm: Boolean = false, body: () -> Unit) {
        DesignHarness.initDatastore()
        val before = listOf(AUDIO_VIZ_NOTICE_SEEN.value(), AUDIO_VISUALIZATION.value(), REDUCE_MOTION.value())
        try {
            put(AUDIO_VIZ_NOTICE_SEEN, seen)
            put(AUDIO_VISUALIZATION, on)
            put(REDUCE_MOTION, calm)
            body()
        } finally {
            put(AUDIO_VIZ_NOTICE_SEEN, before[0])
            put(AUDIO_VISUALIZATION, before[1])
            put(REDUCE_MOTION, before[2])
        }
    }

    private fun put(pref: Pref<Boolean>, value: Boolean) {
        runBlocking { pref.set(value) }
        await { pref.value() == value }
    }

    /** Waits up to a second for a write to reach the snapshot the app reads. */
    private fun await(condition: () -> Boolean) {
        var tries = 0
        while (!condition() && tries++ < 50) Thread.sleep(20)
        assertTrue(condition(), "the datastore never reached the expected state")
    }
}
