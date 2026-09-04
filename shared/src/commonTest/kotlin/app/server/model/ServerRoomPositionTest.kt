package app.server.model

import app.server.SyncplayServer
import app.utils.SyncClock
import app.utils.TestClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * [ServerRoom.getPosition] adopts the slowest watcher and rewrites the room's own state, which
 * is faithful to Python's `Room.getPosition()`. These tests pin the two ways that bites: two
 * reads in a row disagree, and the second read inherits a `setBy` the first one chose.
 */
class ServerRoomPositionTest {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var server: SyncplayServer
    private val clock = TestClock()

    @BeforeTest
    fun setup() {
        clock.install()
        server = SyncplayServer(ServerConfig(), scope)
    }

    @AfterTest
    fun teardown() {
        SyncClock.reset()
        scope.cancel()
    }

    /**
     * Joins the room first, then sets the position. Order matters: [ServerRoom.addWatcher]
     * hands a joiner the room's own position, so a position set before joining is thrown away.
     */
    private fun ServerRoom.joinAt(name: String, position: Double): ServerWatcher =
        ServerWatcher(server, name).also { addWatcher(it); it.setPosition(position) }

    @Test
    fun adopting_the_slowest_watcher_rewrites_setBy() {
        val room = ServerRoom("r")
        room.joinAt("fast", 100.0)
        val slow = room.joinAt("slow", 10.0)
        clock.advanceSeconds(5.0)

        assertEquals(10.0, room.getPosition())
        assertSame(slow, room.getSetBy(), "the read chose a setBy; that is the side effect")
    }

    @Test
    fun peeking_never_adopts_and_never_touches_setBy() {
        val room = ServerRoom("r")
        room.joinAt("slow", 10.0)
        clock.advanceSeconds(5.0)

        assertEquals(0.0, room.peekPosition(), "a paused room's peek is its own position")
        assertNull(room.getSetBy(), "peeking must not choose a setBy")
        assertEquals(0.0, room.peekPosition(), "and it must be repeatable")
    }

    @Test
    fun a_controlled_room_follows_its_controllers_not_its_watchers() {
        val room = ControlledServerRoom("+r:HASH12345678")
        room.joinAt("bystander", 5.0)
        val controller = room.joinAt("boss", 60.0)
        room.addController(controller)
        clock.advanceSeconds(5.0)

        assertEquals(60.0, room.getPosition(), "the slowest bystander must not drag the room")
        assertSame(controller, room.getSetBy())
    }

    @Test
    fun two_reads_in_a_row_disagree_which_is_why_callers_must_read_once() {
        val room = ServerRoom("r")
        room.joinAt("slow", 10.0)
        room.setPaused(ServerRoom.STATE_PLAYING)
        clock.advanceSeconds(5.0)

        val first = room.getPosition()
        clock.advanceSeconds(0.5)
        val second = room.getPosition()
        assertNotEquals(
            first, second,
            "the first read reset _lastUpdate, so the second no longer carries the same age"
        )
    }
}
