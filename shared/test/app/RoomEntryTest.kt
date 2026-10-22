package app

import androidx.navigation3.runtime.entryProvider
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The page transition crossfades room changes, so it must find the room's entry. */
class RoomEntryTest {

    private val entries = entryProvider<Screen> {
        entry<Screen.Home> {}
        roomEntry {}
    }

    @Test
    fun `the room entry is found`() {
        assertTrue(entries(Screen.Room(joinConfig = null)).isRoom)
    }

    @Test
    fun `the home entry is not the room`() {
        assertFalse(entries(Screen.Home).isRoom)
    }
}
