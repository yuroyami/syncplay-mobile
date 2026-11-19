package app.sync

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * A phone that left the screen without background sound is held: it stays paused on this device
 * and follows the room again when it comes back. Nothing the room does while it is away may start
 * it, because a locked phone in a pocket would start to play by itself.
 */
class BackgroundHoldTest {

    private fun TwoClientRoom.bothPlayThenAliceLeaves() {
        joinAndLoad()
        alice.play()
        waitUntil("both play") { alice.player.playing && bob.player.playing }
        runOnMain { alice.viewmodel.uiState.onLifecycleStop() }
        waitUntil("alice holds") { !alice.player.playing }
    }

    private fun TwoClientRoom.aliceComesBackInStep() {
        runOnMain { alice.viewmodel.uiState.onLifecycleStart() }
        waitUntil("alice plays with the room again") { alice.player.playing && gapMs() < 1_000 }
    }

    @Test
    fun aReconnectWhileHeldDoesNotStartPlayback() = TwoClientRoom().use { room ->
        room.bothPlayThenAliceLeaves()
        val before = room.alice.viewmodel.protocol.lastStateReceivedAt
        (room.alice.viewmodel.networkManager as LoopbackTransport).sever()
        room.waitUntil("alice reconnects and hears the room", timeoutMs = 20_000) {
            room.alice.connected && room.alice.viewmodel.protocol.lastStateReceivedAt.let { it != null && before != null && it > before }
        }
        Thread.sleep(500)
        assertFalse(room.alice.player.playing, "A held phone stays paused through a reconnect")
        room.aliceComesBackInStep()
    }

    @Test
    fun aNewFileWhileHeldDoesNotStartPlayback() = TwoClientRoom().use { room ->
        room.bothPlayThenAliceLeaves()
        runBlocking { room.alice.player.injectVideoURL("https://example.com/second.mp4") }
        room.waitUntil("alice holds the second file") { room.alice.viewmodel.media?.fileName == "second.mp4" }
        Thread.sleep(1_500)
        assertFalse(room.alice.player.playing, "A held phone stays paused when a new file opens")
    }
}

/** Runs [block] on the main thread and waits for it, as the platform calls the lifecycle hooks. */
internal fun runOnMain(block: () -> Unit) = runBlocking(kotlinx.coroutines.Dispatchers.Main) { block() }
