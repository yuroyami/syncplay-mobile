package app.sync

import app.i18n.Localization
import app.protocol.models.ConnectionState
import app.room.RoomViewmodel
import app.server.SyncplayServer
import app.server.model.ServerConfig
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * A server that refuses a client sends an Error and then closes the socket. The room shows the
 * error once and stops, as Syncplay PC does. Before, the room retried the refused join without end.
 */
class ServerErrorTest {

    /** Counts the dials. A dial is one attempt to open a socket. */
    private open class CountingTransport(viewmodel: RoomViewmodel, server: SyncplayServer) : LoopbackTransport(viewmodel, server) {
        val dials = AtomicInteger()

        override suspend fun connectSocket() {
            dials.incrementAndGet()
            super.connectSocket()
        }
    }

    /** A server that closes the first socket at once and sends no Error. */
    private class DropsFirstSocket(viewmodel: RoomViewmodel, server: SyncplayServer) : CountingTransport(viewmodel, server) {
        override suspend fun connectSocket() {
            super.connectSocket()
            if (dials.get() == 1) sever()
        }
    }

    /** A server that offers TLS and then shows a certificate that nothing trusts. */
    private class UntrustedCertificate(viewmodel: RoomViewmodel, server: SyncplayServer) : CountingTransport(viewmodel, server) {
        override fun supportsTLS() = true

        override suspend fun writeActualString(s: String) {
            if ("startTLS" in s) handlePacket("""{"TLS": {"startTLS": "true"}}""") else super.writeActualString(s)
        }

        override suspend fun upgradeTls() {
            reportUntrustedCertificate("AA:BB:CC")
            error("The certificate is not trusted")
        }
    }

    private fun lines(viewmodel: RoomViewmodel) = viewmodel.session.messageSequence.value.map { it.content }

    @Test
    fun aWrongServerPasswordShowsTheErrorOnceAndStopsTheRetries(): Unit = TwoClientRoom(
        transport = ::CountingTransport,
        serverConfig = ServerConfig(isolateRooms = false, password = "secret"),
        serverPassword = "not the secret",
    ).use { room ->
        val alice = room.alice.viewmodel
        val transport = alice.networkManager as CountingTransport
        room.waitUntil("alice hears that the retries stopped") { Localization.strings.roomDisconnected in lines(alice) }
        // Longer than the first pause of a retry campaign, which is 2 seconds by default.
        Thread.sleep(FIRST_RETRY_PAUSE_MS)
        assertEquals(1, transport.dials.get(), "No second attempt starts after the server refused the join")
        assertEquals(ConnectionState.DISCONNECTED, alice.networkManager.state.value)
        assertEquals(1, lines(alice).count { it == "Wrong password supplied" }, "The server's error shows once")
        assertEquals(1, lines(alice).count { it == Localization.strings.roomDisconnected }, "The end of the retries shows once")
        assertFalse(Localization.strings.roomConnectionFailed in lines(alice), "The closed socket adds no failure line after the error")
    }

    @Test
    fun aSocketClosedWithoutAnErrorStillStartsTheRetries(): Unit = TwoClientRoom(::DropsFirstSocket).use { room ->
        val transport = room.alice.viewmodel.networkManager as CountingTransport
        room.waitUntil("alice dials a second time", timeoutMs = 10_000) { transport.dials.get() >= 2 }
        room.waitUntil("alice connects on the second socket") { room.alice.connected }
    }

    @Test
    fun aCertificateThatNothingTrustsAsksOnceAndStartsNoSecondAttempt(): Unit = TwoClientRoom(::UntrustedCertificate).use { room ->
        val alice = room.alice.viewmodel
        val transport = alice.networkManager as CountingTransport
        room.waitUntil("the room asks about the certificate") { alice.networkManager.untrustedCertificate.value != null }
        Thread.sleep(FIRST_RETRY_PAUSE_MS)
        assertEquals(1, transport.dials.get(), "No retry runs while the room asks about the certificate")
        assertEquals(ConnectionState.DISCONNECTED, alice.networkManager.state.value)
        assertNotNull(alice.networkManager.untrustedCertificate.value, "The question stays until the person answers")
    }

    private companion object {
        const val FIRST_RETRY_PAUSE_MS = 3_500L
    }
}
