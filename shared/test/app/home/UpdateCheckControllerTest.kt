package app.home

import app.home.components.UpdateCheck
import app.home.components.UpdateCheckController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateCheckControllerTest {
    @Test
    fun repeatedTapsShareOneRequestAndKeepTheCurrentReleaseResult() = runTest {
        var requests = 0
        val response = CompletableDeferred<UpdateCheck.Result>()
        val controller = UpdateCheckController(this) {
            requests++
            response.await()
        }

        repeat(10) { controller.check() }
        assertTrue(controller.isChecking)
        assertFalse(controller.canCheck)
        runCurrent()
        assertEquals(1, requests)

        response.complete(UpdateCheck.Result.UpToDate)
        advanceUntilIdle()
        repeat(10) { controller.check() }
        advanceUntilIdle()
        assertEquals(1, requests)
        assertEquals(UpdateCheck.Result.UpToDate, controller.result)
        assertFalse(controller.isChecking)
        assertFalse(controller.canCheck)
    }

    @Test
    fun newerReleaseRemainsAvailableWithoutAnotherRequest() = runTest {
        var requests = 0
        val release = UpdateCheck.Result.Newer("0.25.0", "https://example.com/release")
        val controller = UpdateCheckController(this) { requests++; release }
        controller.check()
        advanceUntilIdle()
        controller.check()
        advanceUntilIdle()
        assertEquals(1, requests)
        assertEquals(release, controller.result)
        assertFalse(controller.canCheck)
    }

    @Test
    fun failedCheckCanBeRetried() = runTest {
        var requests = 0
        val controller = UpdateCheckController(this) {
            requests++
            if (requests == 1) UpdateCheck.Result.Unreachable else UpdateCheck.Result.UpToDate
        }
        controller.check()
        advanceUntilIdle()
        assertTrue(controller.canCheck)
        controller.check()
        advanceUntilIdle()
        assertEquals(2, requests)
        assertEquals(UpdateCheck.Result.UpToDate, controller.result)
        assertFalse(controller.canCheck)
    }
}
