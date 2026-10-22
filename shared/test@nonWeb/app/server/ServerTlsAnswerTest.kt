package app.server

import app.server.model.ServerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A connection switches to TLS only when the host turned TLS on, and only once. */
class ServerTlsAnswerTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun stop() = scope.cancel()

    private class Probe {
        val sent = mutableListOf<String>()
        val upgraded = mutableListOf<String>()
    }

    private fun ask(offerTls: Boolean, times: Int = 1): Probe = runBlocking {
        val probe = Probe()
        val connection = ClientConnection(
            server = SyncplayServer(ServerConfig(offerTls = offerTls), scope),
            sendFn = { probe.sent += it },
            dropFn = {},
            upgradeFn = { probe.upgraded += it },
        )
        repeat(times) { connection.handlePacket("{\"TLS\": {\"startTLS\": \"send\"}}") }
        probe
    }

    @Test
    fun withTlsOffTheAnswerIsNoAndNothingSwitches() {
        val probe = ask(offerTls = false)
        assertEquals(emptyList(), probe.upgraded)
        assertTrue(probe.sent.single().contains("\"false\""), probe.sent.toString())
    }

    @Test
    fun withTlsOnTheYesGoesThroughTheSwitchOnce() {
        val probe = ask(offerTls = true, times = 2)
        assertTrue(probe.upgraded.single().contains("\"true\""), probe.upgraded.toString())
        assertTrue(probe.sent.single().contains("\"false\""), "a second request is refused: ${probe.sent}")
    }
}
