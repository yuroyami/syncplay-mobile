package app.sync

import app.i18n.Localization
import app.protocol.models.ConnectionState
import app.room.RoomViewmodel
import app.server.SyncplayServer
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * The handshake with no deadline: a connection that never completed sat in the connecting state
 * forever, where no watchdog runs. The network manager now gives the handshake a deadline.
 */
class HandshakeDeadlineTest {

    /** A server that accepts the socket and never answers, and a short deadline for the test. */
    private class SilentServer(viewmodel: RoomViewmodel, server: SyncplayServer) : LoopbackTransport(viewmodel, server) {
        override val handshakeTimeout: Duration = DEADLINE
        override suspend fun writeActualString(s: String) = Unit
    }

    @Test
    fun aHandshakeThatNeverCompletesFailsAtItsDeadline() = TwoClientRoom(::SilentServer).use { room ->
        val alice = room.alice.viewmodel
        room.waitUntil("alice starts connecting") { alice.networkManager.state.value == ConnectionState.CONNECTING }
        val connectingAt = System.nanoTime()
        // The notice names the reason: the server did not answer in time.
        room.waitUntil("the failed-connection path runs") {
            alice.session.messageSequence.value.any { it.content == Localization.strings.roomConnectionFailedTimeout }
        }
        val waitedMs = (System.nanoTime() - connectingAt) / 1_000_000
        assertTrue(waitedMs >= DEADLINE.inWholeMilliseconds - 100, "It waits for the deadline: ${waitedMs}ms")
        assertTrue(alice.networkManager.state.value != ConnectionState.CONNECTED)
    }

    private companion object {
        val DEADLINE = 600.milliseconds
    }
}
