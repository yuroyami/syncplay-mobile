package app.room.ui.bottombar

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvLocalVideoPickerTest {

    @Test
    fun usesLocalPickerOnlyWhenPlatformLauncherIsAvailable() {
        assertTrue(shouldUseTvLocalVideoPicker(launcherAvailable = true))
        assertFalse(shouldUseTvLocalVideoPicker(launcherAvailable = false))
    }
}
