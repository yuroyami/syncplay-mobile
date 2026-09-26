package app.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures the app with and without its baseline profile: the cold start to Home, and the frames
 * from Home to an open room. Compare the two results of each pair on one device.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartWithoutProfile() = coldStart(CompilationMode.None())

    @Test
    fun coldStartWithProfile() = coldStart(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test
    fun openRoomWithoutProfile() = openRoom(CompilationMode.None())

    @Test
    fun openRoomWithProfile() = openRoom(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun coldStart(mode: CompilationMode) = rule.measureRepeated(
        packageName = targetAppId,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = ITERATIONS,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        waitForHome()
    }

    private fun openRoom(mode: CompilationMode) = rule.measureRepeated(
        packageName = targetAppId,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = ITERATIONS,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            waitForHome()
        },
    ) {
        openSoloRoom()
    }

    private companion object {
        const val ITERATIONS = 10
    }
}
