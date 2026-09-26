package app.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

/** The application id of the copy under test. The build passes it, suffix included. */
val targetAppId: String
    get() = InstrumentationRegistry.getArguments().getString("targetAppId")
        ?: error("targetAppId is missing: run the tests through Gradle, which passes it")

private const val WAIT_MS = 10_000L

/** Waits for Home. The logo row starts with the app name, which is the same in every language. */
fun MacrobenchmarkScope.waitForHome() {
    check(device.wait(Until.hasObject(By.textStartsWith("Synkplay")), WAIT_MS)) { "Home did not appear" }
}

/**
 * Opens a room from Home without a server: the logo opens About, and its Watch alone button
 * opens a room of one. The button text is English, so the device language must be English.
 */
fun MacrobenchmarkScope.openSoloRoom() {
    device.findObject(By.textStartsWith("Synkplay")).click()
    check(device.wait(Until.hasObject(By.text("Watch alone")), WAIT_MS)) { "About did not open" }
    device.findObject(By.text("Watch alone")).click()
    // The room has drawn when About's button is gone and the screen is still.
    check(device.wait(Until.gone(By.text("Watch alone")), WAIT_MS)) { "The room did not open" }
    device.waitForIdle()
}
