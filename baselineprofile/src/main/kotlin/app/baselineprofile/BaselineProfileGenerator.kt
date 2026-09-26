package app.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the code that the app runs from a cold start to an open room. The plugin writes the
 * result to androidApp/src/main/generated/baselineProfiles, and the release builds package it.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startToRoom() = rule.collect(packageName = targetAppId, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        waitForHome()
        openSoloRoom()
    }
}
