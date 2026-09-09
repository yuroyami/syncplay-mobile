package app.player.vlc

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class VlcSeekCompletionTest {
    @Test
    fun waits_for_native_progress_instead_of_the_displayed_target() = runTest {
        var native = 1_000L
        val results = mutableListOf<VlcSeekCompletion.Result>()
        val completion = VlcSeekCompletion(results::add)
        launch { completion.await({ true }, { 20_000L }, { native }) }
        runCurrent()
        advanceTimeBy(100)
        assertTrue(results.isEmpty())
        native = 20_200L
        advanceUntilIdle()
        assertEquals(listOf(VlcSeekCompletion.Result.CONVERGED), results)
    }

    @Test
    fun paused_seek_with_unmoved_native_clock_finishes_unconfirmed() = runTest {
        val results = mutableListOf<VlcSeekCompletion.Result>()
        VlcSeekCompletion(results::add).await({ true }, { 20_000L }, { 1_000L })
        assertEquals(listOf(VlcSeekCompletion.Result.TIMED_OUT), results)
        assertEquals(2_000L, testScheduler.currentTime)
    }

    @Test
    fun replacement_or_newer_seek_finishes_without_waiting_for_the_old_target() = runTest {
        var current = true
        val results = mutableListOf<VlcSeekCompletion.Result>()
        launch { VlcSeekCompletion(results::add).await({ current }, { 20_000L }, { 1_000L }) }
        runCurrent()
        advanceTimeBy(100)
        current = false
        advanceUntilIdle()
        assertEquals(listOf(VlcSeekCompletion.Result.SUPERSEDED), results)
        assertEquals(100L, testScheduler.currentTime)
    }

    @Test
    fun rejected_seek_completes_once_without_waiting() = runTest {
        val results = mutableListOf<VlcSeekCompletion.Result>()
        val completion = VlcSeekCompletion(results::add)
        completion.await({ true }, { null }, { 1_000L })
        completion.finish(VlcSeekCompletion.Result.CANCELLED)
        assertEquals(listOf(VlcSeekCompletion.Result.UNAVAILABLE), results)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun cancellation_and_job_completion_cannot_call_the_system_twice() = runTest {
        val results = mutableListOf<VlcSeekCompletion.Result>()
        val completion = VlcSeekCompletion(results::add)
        val job = launch { completion.await({ true }, { 20_000L }, { 1_000L }) }
        job.invokeOnCompletion { completion.finish(VlcSeekCompletion.Result.CANCELLED) }
        runCurrent()
        advanceTimeBy(100)
        job.cancel()
        advanceUntilIdle()
        assertEquals(listOf(VlcSeekCompletion.Result.CANCELLED), results)
    }

    @Test
    fun cancellation_before_the_coroutine_starts_still_releases_completion() = runTest {
        val results = mutableListOf<VlcSeekCompletion.Result>()
        val completion = VlcSeekCompletion(results::add)
        val job = launch(start = CoroutineStart.LAZY) {
            completion.await({ true }, { 20_000L }, { 1_000L })
        }
        job.invokeOnCompletion { completion.finish(VlcSeekCompletion.Result.CANCELLED) }
        job.cancel()
        advanceUntilIdle()
        assertEquals(listOf(VlcSeekCompletion.Result.CANCELLED), results)
    }

    @Test
    fun deferred_target_can_be_clamped_when_duration_becomes_known() = runTest {
        var target = 50_000L
        val results = mutableListOf<VlcSeekCompletion.Result>()
        launch { VlcSeekCompletion(results::add).await({ true }, { target }, { 30_000L }) }
        runCurrent()
        advanceTimeBy(100)
        target = 30_000L
        advanceUntilIdle()
        assertEquals(listOf(VlcSeekCompletion.Result.CONVERGED), results)
    }

    @Test
    fun a_deferred_request_cannot_complete_from_a_close_but_unsubmitted_native_clock() = runTest {
        var pending = true
        val results = mutableListOf<VlcSeekCompletion.Result>()
        launch {
            VlcSeekCompletion(results::add).await({ true }, { 500L }, {
                if (pending) null else 600L
            })
        }
        runCurrent()
        advanceTimeBy(100)
        assertTrue(results.isEmpty())
        pending = false
        advanceUntilIdle()
        assertEquals(listOf(VlcSeekCompletion.Result.CONVERGED), results)
    }

    @Test
    fun native_read_failure_releases_the_system_callback_exactly_once() = runTest {
        val results = mutableListOf<VlcSeekCompletion.Result>()
        val completion = VlcSeekCompletion(results::add)
        assertFailsWith<IllegalStateException> {
            completion.await({ true }, { 20_000L }, { error("native read failed") })
        }
        completion.finish(VlcSeekCompletion.Result.CANCELLED)
        assertEquals(listOf(VlcSeekCompletion.Result.FAILED), results)
    }
}
