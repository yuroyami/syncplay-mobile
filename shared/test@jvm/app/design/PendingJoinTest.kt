package app.design

import androidx.compose.runtime.mutableStateListOf
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModelStore
import app.Screen
import app.home.HomeScreenUI
import app.home.HomeViewmodel
import app.home.JoinConfig
import app.home.PendingJoin
import app.preferences.Preferences
import app.preferences.datastore
import app.preferences.prefKey
import app.preferences.value
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A join from a link or a shortcut reaches Home and joins once, whether it came before Home or during it. */
class PendingJoinTest {

    private val link = JoinConfig(user = "alice", room = "movie night", ip = "example.org", port = 8999)

    private fun rooms(backStack: List<Screen>) = backStack.filterIsInstance<Screen.Room>().map { it.joinConfig?.room }

    private fun withHome(block: DesignHarness.Driver.(MutableList<Screen>) -> Unit) {
        DesignHarness.initDatastore()
        // A join saves the room to the recent rooms and the join form. Put both back for the next test.
        val recentBefore = Preferences.RECENT_JOINS.value()
        val joinBefore = Preferences.JOIN_CONFIG.value()
        val store = ViewModelStore()
        val backStack = mutableStateListOf<Screen>(Screen.Home)
        val viewmodel = HomeViewmodel(backStack).also { store.put("home", it) }
        try {
            DesignHarness.drive(widthDp = 400, heightDp = 800, television = false, content = { HomeScreenUI(viewmodel) }) {
                block(backStack)
            }
        } finally {
            store.clear()
            PendingJoin.take()
            // The join opens the room first and saves afterwards, so wait for the save before the restore.
            var tries = 0
            while (Preferences.RECENT_JOINS.value() == recentBefore && tries++ < 50) Thread.sleep(20)
            runBlocking {
                datastore.edit { preferences ->
                    if (recentBefore == null) preferences.remove(Preferences.RECENT_JOINS.prefKey())
                    else preferences[Preferences.RECENT_JOINS.prefKey()] = recentBefore
                    if (joinBefore == null) preferences.remove(Preferences.JOIN_CONFIG.prefKey())
                    else preferences[Preferences.JOIN_CONFIG.prefKey()] = joinBefore
                }
            }
        }
    }

    private fun DesignHarness.Driver.waitForRoom(backStack: List<Screen>) {
        repeat(100) {
            if (rooms(backStack).isNotEmpty()) return
            frames(2)
            Thread.sleep(20)
        }
    }

    @Test
    fun aLinkThatStartsTheAppJoinsWhenHomeAppears() {
        PendingJoin.post(link)
        withHome { backStack ->
            waitForRoom(backStack)
            assertEquals(listOf("movie night"), rooms(backStack))
            assertNull(PendingJoin.waiting.value, "the join runs once")
        }
    }

    @Test
    fun aLinkThatArrivesWhileHomeShowsJoinsAtOnce() {
        withHome { backStack ->
            assertEquals(emptyList(), rooms(backStack))
            PendingJoin.post(link)
            waitForRoom(backStack)
            assertEquals(listOf("movie night"), rooms(backStack))
        }
    }
}
