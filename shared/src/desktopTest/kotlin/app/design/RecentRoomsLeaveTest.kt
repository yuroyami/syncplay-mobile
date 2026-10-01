package app.design

import androidx.compose.runtime.mutableStateListOf
import androidx.datastore.preferences.core.edit
import app.Screen
import app.home.HomeScreenUI
import app.home.HomeViewmodel
import app.home.JoinConfig
import app.i18n.EnAppStrings
import app.preferences.Preferences
import app.preferences.datastore
import app.preferences.prefKey
import app.preferences.set
import app.preferences.value
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A join saves the room to the recent rooms while the home screen is still fading out under the
 * room. The list must not pop up in that fade: it shows the joined room only after coming back.
 */
class RecentRoomsLeaveTest {

    private val title = EnAppStrings.homeRecentTitle.uppercase()

    @Test
    fun theJoinedRoomJoinsTheListOnlyAfterComingBack() {
        DesignHarness.initDatastore()
        val before = listOf(
            Preferences.NEVER_SHOW_TIPS.value(), Preferences.REMEMBER_INFO.value(),
        )
        val recentBefore = Preferences.RECENT_JOINS.value()
        val joinBefore = Preferences.JOIN_CONFIG.value()
        try {
            runBlocking {
                Preferences.NEVER_SHOW_TIPS.set(true)
                Preferences.REMEMBER_INFO.set(true)
                datastore.edit { it.remove(Preferences.RECENT_JOINS.prefKey()) }
            }
            await { Preferences.NEVER_SHOW_TIPS.value() && Preferences.REMEMBER_INFO.value() && Preferences.RECENT_JOINS.value() == null }
            val backStack = mutableStateListOf<Screen>(Screen.Home)
            val viewmodel = HomeViewmodel(backStack)
            DesignHarness.drive(widthDp = 400, heightDp = 900, television = false, content = { HomeScreenUI(viewmodel) }) {
                frames(10)
                assertFalse(shows(title), "no recent rooms yet, so no list")
                runBlocking { viewmodel.joinRoom(JoinConfig(user = "tester", room = "leaving")) }
                await { Preferences.RECENT_JOINS.value() != null }
                frames(10)
                assertTrue(backStack.last() is Screen.Room, "the join opened the room")
                assertFalse(shows(title), "the list must not appear while the room opens over the home screen")
                DesignHarness.onUiThread { backStack.removeAt(backStack.lastIndex) }
                frames(10)
                assertTrue(shows(title), "back home, the joined room is in the list")
            }
        } finally {
            runBlocking {
                Preferences.NEVER_SHOW_TIPS.set(before[0])
                Preferences.REMEMBER_INFO.set(before[1])
                datastore.edit { preferences ->
                    if (recentBefore == null) preferences.remove(Preferences.RECENT_JOINS.prefKey())
                    else preferences[Preferences.RECENT_JOINS.prefKey()] = recentBefore
                    if (joinBefore == null) preferences.remove(Preferences.JOIN_CONFIG.prefKey())
                    else preferences[Preferences.JOIN_CONFIG.prefKey()] = joinBefore
                }
            }
        }
    }

    /** Waits up to a second for a write to reach the snapshot the app reads. */
    private fun await(condition: () -> Boolean) {
        var tries = 0
        while (!condition() && tries++ < 50) Thread.sleep(20)
        assertTrue(condition(), "the datastore never reached the expected state")
    }
}
