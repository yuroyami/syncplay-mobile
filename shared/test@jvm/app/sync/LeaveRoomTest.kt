package app.sync

import androidx.compose.runtime.mutableStateListOf
import app.Screen
import kotlin.test.Test
import kotlin.test.assertEquals

/** Leaving a room removes the room's page once, however many times the leave runs. */
class LeaveRoomTest {

    @Test
    fun aRepeatedLeaveKeepsTheHomePage() {
        val backStack = mutableStateListOf<Screen>(Screen.Home, Screen.Room(null))
        withSoloRoom(backStack) { viewmodel, _ ->
            // Two quick taps on Yes, or two leave questions answered at once, each run a leave.
            repeat(3) { viewmodel.goHome() }
            assertEquals(listOf<Screen>(Screen.Home), backStack.toList())
        }
    }

    @Test
    fun aLeaveRemovesTheRoomEvenWhenAnotherPageIsOnTop() {
        // On desktop, Ctrl+Comma opens the settings over the room.
        val backStack = mutableStateListOf<Screen>(Screen.Home, Screen.Room(null), Screen.Settings())
        withSoloRoom(backStack) { viewmodel, _ ->
            viewmodel.goHome()
            assertEquals(listOf<Screen>(Screen.Home, Screen.Settings()), backStack.toList())
        }
    }
}
