package app.sync

import androidx.lifecycle.viewModelScope
import app.room.RoomViewmodel
import app.server.SyncplayServer
import app.utils.SyncClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A queue that builds up on the way down to one client: each State waits longer than the one
 * before, then the queue drains. The client ages each State by that message's own delay, measured
 * with the clock offset, so its view of the room position stays on the other client's playhead.
 * The smoothed estimate alone trails such a queue by one step, and misses a State that arrives
 * with no echo of the client's last reply.
 */
class ClockOffsetAgeTest {

    @Test
    fun aQueueOnTheWayDownDoesNotMoveTheRoomPosition() = TwoClientRoom(transport = { viewmodel, server ->
        if (viewmodel.joinConfig?.user == "alice") QueueOnTheWayDown(viewmodel, server) else LoopbackTransport(viewmodel, server)
    }).use { room ->
        room.joinAndLoad()
        room.alice.play()
        room.waitUntil("both play") { room.alice.player.playing && room.bob.player.playing }
        val link = room.alice.viewmodel.networkManager as QueueOnTheWayDown

        room.waitUntil("the queue starts building", timeoutMs = 20_000) { link.states >= FAST_STATES }
        val worst = mutableListOf<Long>()
        while (link.states < FAST_STATES + DOWNLINK_MS.size + 1) {
            val roomAsAliceSeesIt = room.alice.viewmodel.protocol.extrapolatedGlobalPositionMs().toLong()
            worst += abs(roomAsAliceSeesIt - room.bob.positionMs())
            Thread.sleep(25)
        }
        println("ClockOffsetAgeTest: worst gap ${worst.max()}ms over ${worst.size} samples")
        assertTrue(worst.max() < MAX_ERROR_MS, "Alice's room position strays ${worst.max()}ms from Bob's playhead")
    }

    /** Alice's way down: fast at first, then a queue that builds up for a few States and drains. */
    private class QueueOnTheWayDown(viewmodel: RoomViewmodel, server: SyncplayServer) : LoopbackTransport(viewmodel, server) {
        private val lock = Any()
        private var lastDueMs = 0L
        private val lines = Channel<Pair<Long, String>>(Channel.UNLIMITED)

        /** How many States the server has sent down this link so far. */
        @Volatile
        var states = 0
            private set

        init {
            viewmodel.viewModelScope.launch(Dispatchers.Default) {
                for ((due, line) in lines) {
                    delay(due - SyncClock.nowMillis())
                    handlePacket(line)
                }
            }
        }

        // Each line keeps its place: a line never overtakes the one before it.
        override fun deliver(line: String) {
            val due = synchronized(lock) {
                val wait = if (line.contains("\"State\"")) DOWNLINK_MS.getOrElse(states++ - FAST_STATES) { BASE_MS } else BASE_MS
                maxOf(SyncClock.nowMillis() + wait, lastDueMs).also { lastDueMs = it }
            }
            lines.trySend(due to line)
        }
    }

    private companion object {
        /** The fast States first, so the clock offset has enough samples. */
        const val FAST_STATES = 8
        const val BASE_MS = 20L

        /** The queue on the way down, one State a second: it builds to 1.2 s and drains. */
        val DOWNLINK_MS = listOf(300L, 600L, 900L, 1200L, 900L, 600L, 300L)

        /** Well under the 300 ms step of the queue, and over the harness's own timing noise. */
        const val MAX_ERROR_MS = 150L
    }
}
