package app.design

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModelStore
import app.Screen
import app.home.HomeScreenUI
import app.home.HomeViewmodel
import app.home.JoinConfig
import app.home.PendingJoin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A join from a link or a shortcut reaches Home and joins once, whether it came before Home or during it. */
class PendingJoinTest {

    private val link = JoinConfig(user = "alice", room = "movie night", ip = "example.org", port = 8999)

    private fun rooms(backStack: List<Screen>) = backStack.filterIsInstance<Screen.Room>().map { it.joinConfig?.room }

    private fun withHome(block: DesignHarness.Driver.(MutableList<Screen>) -> Unit) {
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
