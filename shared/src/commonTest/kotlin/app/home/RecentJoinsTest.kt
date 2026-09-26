package app.home

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecentJoinsTest {
    private fun join(room: String, ip: String = "syncplay.pl", port: Int = 8997, pw: String = "") =
        JoinConfig(user = "yuroyami", room = room, ip = ip, port = port, pw = pw)

    @Test
    fun theNewestJoinComesFirstAndARoomCountsOnce() {
        var list = RecentJoins.withJoin(emptyList(), join("a"))
        list = RecentJoins.withJoin(list, join("b"))
        list = RecentJoins.withJoin(list, join("a", ip = "SYNCPLAY.PL"))
        assertEquals(listOf("a", "b"), list.map { it.room })
    }

    @Test
    fun theSameRoomOnAnotherServerOrPortIsAnotherEntry() {
        var list = RecentJoins.withJoin(emptyList(), join("a"))
        list = RecentJoins.withJoin(list, join("a", port = 8999))
        list = RecentJoins.withJoin(list, join("a", ip = "192.168.1.20"))
        assertEquals(3, list.size)
    }

    @Test
    fun theListKeepsFiveEntries() {
        val list = (1..8).fold(emptyList<RecentJoin>()) { acc, i -> RecentJoins.withJoin(acc, join("room$i")) }
        assertEquals(listOf("room8", "room7", "room6", "room5", "room4"), list.map { it.room })
    }

    @Test
    fun noPasswordIsKeptOnlyTheFactThatThereWasOne() {
        val list = RecentJoins.withJoin(emptyList(), join("a", pw = "hunter2"))
        assertTrue(list.single().hasPassword)
        assertFalse("hunter2" in Json.encodeToString(list))
        assertEquals("", list.single().toJoinConfig().pw)
    }

    @Test
    fun aRoomOnThisDevicesOwnServerStaysOut() {
        assertEquals(emptyList(), RecentJoins.withJoin(emptyList(), join("a", ip = LOCAL_HOST)))
    }

    @Test
    fun aValueThatDoesNotDecodeReadsAsEmpty() {
        assertEquals(emptyList(), RecentJoins.decode("not json"))
        assertEquals(emptyList(), RecentJoins.decode(null))
    }
}
