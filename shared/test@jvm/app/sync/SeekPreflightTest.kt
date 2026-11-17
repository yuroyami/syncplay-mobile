package app.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * The rules of the seek preflight in the room dispatcher. Before the room hears about a seek, the
 * engine prepares the target (`prepareSeekTarget`). Only the VLC engine changes the target there,
 * so these tests give the clock engine a preflight of their own.
 */
class SeekPreflightTest {

    /** A refused target moves nothing and tells nobody. */
    @Test
    fun aRefusedTargetIsNeverAnnounced() = TwoClientRoom().use { room ->
        room.joinAndLoad()
        val start = room.alice.positionMs()
        val seeksBefore = room.alice.player.seeks.toList()

        room.alice.player.prepare = { null }
        assertNull(runBlocking { room.alice.viewmodel.dispatcher.seekByMillis(120_000) })
        assertEquals(seeksBefore, room.alice.player.seeks.toList(), "The engine did not seek")

        // A later seek reaches bob. The refused one would have reached him before it.
        room.alice.player.prepare = { it }
        runBlocking { room.alice.viewmodel.dispatcher.seekByMillis(60_000) }
        room.waitUntil("bob follows the later seek") { room.bob.player.seeks.any { it.near(start + 60_000) } }
        assertFalse(room.bob.player.seeks.any { it.near(start + 120_000) }, "bob never heard the refused seek")
    }

    /** The prepared target is the one that the engine gets, the room hears and the call returns. */
    @Test
    fun thePreparedTargetIsTheOneUsed() = TwoClientRoom().use { room ->
        room.joinAndLoad()
        val start = room.alice.positionMs()
        room.alice.player.prepare = { 30_000L }

        assertEquals(30_000L, runBlocking { room.alice.viewmodel.dispatcher.seekByMillis(90_000) })
        assertEquals(30_000L, room.alice.player.seeks.last())
        room.waitUntil("bob follows to the prepared target") { room.bob.player.seeks.any { it.near(30_000) } }
        assertFalse(room.bob.player.seeks.any { it.near(start + 90_000) }, "bob never heard the asked target")
    }

    /** A new file while the engine prepares cancels the seek. */
    @Test
    fun aNewFileDuringThePreflightCancelsTheSeek() = TwoClientRoom().use { room ->
        room.joinAndLoad()
        val start = room.alice.positionMs()
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        room.alice.player.prepare = { entered.complete(Unit); gate.await(); it }

        runBlocking {
            val seek = async(Dispatchers.Default) { room.alice.viewmodel.dispatcher.seekByMillis(200_000) }
            entered.await()
            val before = room.alice.viewmodel.media
            room.alice.player.injectVideoURL("https://example.com/other.mp4")
            room.waitUntil("alice holds the new file") { room.alice.viewmodel.media.let { it != null && it !== before } }
            gate.complete(Unit)
            assertNull(seek.await())
        }
        assertFalse(room.alice.player.seeks.any { it.near(start + 200_000) }, "The engine did not seek")
        assertFalse(room.bob.player.seeks.any { it.near(start + 200_000) }, "bob never heard the seek")
    }

    /** A player that stops being ready while the engine prepares cancels the seek. */
    @Test
    fun aLostPlayerDuringThePreflightCancelsTheSeek() = TwoClientRoom().use { room ->
        room.joinAndLoad()
        val start = room.alice.positionMs()
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        room.alice.player.prepare = { entered.complete(Unit); gate.await(); it }

        runBlocking {
            val seek = async(Dispatchers.Default) { room.alice.viewmodel.dispatcher.seekByMillis(200_000) }
            entered.await()
            room.alice.viewmodel.playerManager.isPlayerReady.value = false
            gate.complete(Unit)
            assertNull(seek.await())
        }
        assertFalse(room.alice.player.seeks.any { it.near(start + 200_000) }, "The engine did not seek")
    }

    /** A relative seek keeps an offset below one second, as system controls send it. */
    @Test
    fun aRelativeSeekKeepsMilliseconds() = TwoClientRoom().use { room ->
        room.joinAndLoad()
        val start = room.alice.positionMs()

        assertEquals(start + 250, runBlocking { room.alice.viewmodel.dispatcher.seekByMillis(250) })
        assertEquals(start + 250, room.alice.player.seeks.last())
    }

    private fun Long.near(targetMs: Long) = abs(this - targetMs) < 1_000
}
