package app.sync

import app.preferences.Preferences.SYNC_FASTFORWARD
import app.preferences.flow
import app.preferences.set
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Two clients and the app's own server in one room: the sync decision, the acknowledgement and
 * the server's forwarding, end to end. See [TwoClientRoom].
 */
class TwoClientSyncTest {

    @Test
    fun aPlayStartsTheOtherClientAtTheSamePosition() = TwoClientRoom().use { room ->
        room.joinAndLoad()
        room.alice.play()
        room.waitUntil("bob plays") { room.bob.player.playing }
        room.waitUntil("the playheads meet") { room.gapMs() < IN_SYNC_MS }
        Thread.sleep(1_000)
        assertTrue(room.alice.positionMs() > 900, "The room plays on: ${room.alice.positionMs()} ms")
        assertTrue(room.gapMs() < IN_SYNC_MS, "The playheads stay together: ${room.gapMs()} ms apart")
    }

    @Test
    fun aSeekMovesTheOtherClient() = TwoClientRoom().use { room ->
        room.joinAndLoad()
        room.alice.play()
        room.waitUntil("the playheads meet") { room.bob.player.playing && room.gapMs() < IN_SYNC_MS }
        room.alice.seek(120_000)
        room.waitUntil("bob follows the seek") { room.bob.positionMs() > 119_000 && room.gapMs() < IN_SYNC_MS }
    }

    /** No seek and no speed change without a reason: a still room must stay still. */
    @Test
    fun aRoomInStepMakesNoCorrections() = TwoClientRoom().use { room ->
        room.joinAndLoad()
        room.alice.play()
        room.waitUntil("the playheads meet") { room.bob.player.playing && room.gapMs() < IN_SYNC_MS }
        Thread.sleep(500)
        val seeks = room.alice.player.seeks.size to room.bob.player.seeks.size
        val speeds = room.alice.player.speeds.size to room.bob.player.speeds.size
        Thread.sleep(3_000)
        assertEquals(seeks, room.alice.player.seeks.size to room.bob.player.seeks.size, "Seeks while in step")
        assertEquals(speeds, room.alice.player.speeds.size to room.bob.player.speeds.size, "Speed changes while in step")
        assertTrue(room.alice.player.playing && room.bob.player.playing, "Both still play")
        assertTrue(room.gapMs() < IN_SYNC_MS, "Still together: ${room.gapMs()} ms apart")
    }

    /**
     * A playhead that runs ahead of the room past the rewind threshold (4 s by default) goes back
     * to the room. The others stay where they are.
     */
    @Test
    fun aClientThatRunsAheadIsPulledBack() = TwoClientRoom().use { room ->
        room.joinAndLoad()
        room.alice.play()
        room.waitUntil("the playheads meet") { room.bob.player.playing && room.gapMs() < IN_SYNC_MS }
        val aliceSeeks = room.alice.player.seeks.size
        room.bob.player.jumpBy(8_000)
        room.waitUntil("bob comes back to the room") { room.gapMs() < IN_SYNC_MS }
        assertEquals(aliceSeeks, room.alice.player.seeks.size, "The room does not follow the one that ran ahead")
    }

    /**
     * The server follows the slowest watcher, and it learns each watcher's position from the
     * acknowledgement of every State. So a playhead that falls behind pulls the room back: the
     * others rewind to it. Fast-forward is off here, or the one behind could jump forward first.
     */
    @Test
    fun aClientThatFallsBehindPullsTheRoomBack() = withoutFastForward {
        TwoClientRoom().use { room ->
            room.joinAndLoad()
            room.alice.play()
            room.waitUntil("the playheads meet") { room.bob.player.playing && room.gapMs() < IN_SYNC_MS }
            room.alice.seek(60_000)
            room.waitUntil("bob follows the seek") { room.bob.positionMs() > 59_000 && room.gapMs() < IN_SYNC_MS }
            room.bob.player.jumpBy(-8_000)
            val bobSeeks = room.bob.player.seeks.size
            room.waitUntil("alice rewinds to bob") { room.gapMs() < IN_SYNC_MS }
            assertEquals(bobSeeks, room.bob.player.seeks.size, "The one behind stays where it is")
        }
    }

    private fun withoutFastForward(block: () -> Unit) = runBlocking {
        SYNC_FASTFORWARD.set(false)
        withTimeout(2_000) { SYNC_FASTFORWARD.flow().first { !it } }
        try {
            block()
        } finally {
            SYNC_FASTFORWARD.set(SYNC_FASTFORWARD.default)
        }
    }

    private companion object {
        /** Loopback has no real latency, so two playheads in step differ by the polling slack only. */
        const val IN_SYNC_MS = 300L
    }
}
