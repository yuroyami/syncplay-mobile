package app.room

import app.preferences.Preferences.HUD_AUTO_HIDE_SECONDS
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HudAutoHideTest {
    private val playing = HudAutoHideState(
        idleSeconds = 15,
        hudVisible = true,
        hasVideo = true,
        isPlaying = true,
        isBuffering = false,
        held = false,
        activity = 0,
    )

    private fun TestScope.startTimer(initial: HudAutoHideState): MutableStateFlow<HudAutoHideState> {
        val states = MutableStateFlow(initial)
        backgroundScope.launch {
            autoHideHud(states) { states.value = states.value.copy(hudVisible = it) }
        }
        runCurrent()
        return states
    }

    private fun TestScope.elapse(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun defaultIsFifteenSecondsAndNoTimeAccruesBeforePlayback() = runTest {
        assertEquals(15, HUD_AUTO_HIDE_SECONDS.default)
        val states = startTimer(playing.copy(hasVideo = false, isPlaying = false))
        elapse(60_000)
        assertTrue(states.value.hudVisible)

        states.value = states.value.copy(hasVideo = true)
        runCurrent()
        elapse(60_000)
        assertTrue(states.value.hudVisible)

        states.value = states.value.copy(isPlaying = true)
        runCurrent()
        elapse(14_999)
        assertTrue(states.value.hudVisible)
        elapse(1)
        assertFalse(states.value.hudVisible)
    }

    @Test
    fun pauseCancelsTheCountdownAndResumeStartsAFullWindow() = runTest {
        val states = startTimer(playing)
        elapse(14_000)
        states.value = states.value.copy(isPlaying = false)
        runCurrent()
        elapse(30_000)
        assertTrue(states.value.hudVisible)

        states.value = states.value.copy(isPlaying = true)
        runCurrent()
        elapse(14_999)
        assertTrue(states.value.hudVisible)
        elapse(1)
        assertFalse(states.value.hudVisible)
    }

    @Test
    fun bufferingNeverHidesEvenWhenTheEngineStillReportsPlaying() = runTest {
        val states = startTimer(playing)
        elapse(14_000)
        states.value = states.value.copy(isBuffering = true)
        runCurrent()
        elapse(30_000)
        assertTrue(states.value.hudVisible)

        states.value = states.value.copy(isBuffering = false)
        runCurrent()
        elapse(14_999)
        assertTrue(states.value.hudVisible)
        elapse(1)
        assertFalse(states.value.hudVisible)
    }

    @Test
    fun pausedOrLoadingPlaybackRevealsOnlyAutomaticallyHiddenControls() = runTest {
        for (stopped in listOf(
            playing.copy(isPlaying = false),
            playing.copy(isBuffering = true),
            playing.copy(hasVideo = false),
        )) {
            val states = startTimer(playing)
            elapse(15_000)
            assertFalse(states.value.hudVisible)
            states.value = stopped.copy(hudVisible = false)
            runCurrent()
            assertTrue(states.value.hudVisible)

            // A later deliberate hide stays hidden, including while paused or loading.
            states.value = states.value.copy(hudVisible = false, activity = 1)
            runCurrent()
            elapse(30_000)
            assertFalse(states.value.hudVisible)
        }
    }

    @Test
    fun manuallyHiddenControlsRemainHiddenOnPause() = runTest {
        val states = startTimer(playing)
        elapse(1_000)
        states.value = states.value.copy(hudVisible = false, activity = 1)
        runCurrent()
        states.value = states.value.copy(isPlaying = false)
        runCurrent()
        elapse(30_000)
        assertFalse(states.value.hudVisible)
    }

    @Test
    fun interactionAndHoldReleaseEachRestartTheFullWindow() = runTest {
        val states = startTimer(playing)
        elapse(14_000)
        states.value = states.value.copy(activity = 1)
        runCurrent()
        elapse(14_000)
        assertTrue(states.value.hudVisible)

        states.value = states.value.copy(held = true)
        runCurrent()
        elapse(30_000)
        assertTrue(states.value.hudVisible)

        states.value = states.value.copy(held = false)
        runCurrent()
        elapse(14_999)
        assertTrue(states.value.hudVisible)
        elapse(1)
        assertFalse(states.value.hudVisible)
    }

    @Test
    fun disabledTimeoutNeverHidesControls() = runTest {
        val states = startTimer(playing.copy(idleSeconds = 0))
        elapse(60_000)
        assertTrue(states.value.hudVisible)
    }

    /** A screen reader's gestures send no presses, so the timer would hide the controls mid-read. */
    @Test
    fun aRunningScreenReaderNeverHidesTheControls() = runTest {
        val states = startTimer(playing.copy(screenReader = true))
        elapse(60_000)
        assertTrue(states.value.hudVisible)
    }

    @Test
    fun aScreenReaderThatStartsBringsBackControlsTheTimerHid() = runTest {
        val states = startTimer(playing)
        elapse(15_000)
        assertFalse(states.value.hudVisible)
        states.value = states.value.copy(screenReader = true)
        runCurrent()
        assertTrue(states.value.hudVisible)
    }

    @Test
    fun controlsTheUserHidStayHiddenWhenAScreenReaderStarts() = runTest {
        val states = startTimer(playing)
        states.value = states.value.copy(hudVisible = false, activity = 1)
        runCurrent()
        states.value = states.value.copy(screenReader = true)
        runCurrent()
        elapse(30_000)
        assertFalse(states.value.hudVisible)
    }
}
