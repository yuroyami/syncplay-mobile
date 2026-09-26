package app.sync

import app.i18n.Localization
import app.preferences.Preferences.TLS_REQUIRED
import app.preferences.flow
import app.preferences.set
import app.protocol.wire.ControllerAuthData
import app.room.RoomViewmodel
import app.room.parseSlashCommand
import app.room.runSlashCommand
import app.server.SyncplayServer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The room's notices in the words of the original Syncplay client, and at the moment Syncplay
 * shows them. Two real clients and the app's own server, as in [TwoClientSyncTest].
 */
class RoomNoticesTest {

    /** Bob opens another file. Alice hears how her file differs from his, and Bob hears how his differs across the room. */
    @Test
    fun aDifferentFileIsReportedInSyncplaysWords() = TwoClientRoom().use { room ->
        room.joinAndLoad()
        runBlocking { room.bob.player.injectVideoURL("https://example.com/other.mp4") }
        room.waitUntil("alice hears how her file differs from bob's") {
            "Your file differs in the following way(s): name" in room.alice.viewmodel.lines()
        }
        room.waitUntil("bob hears how his file differs across the room") {
            "File differences: name" in room.bob.viewmodel.lines()
        }
    }

    @Test
    fun yourOwnJoinIsAnnouncedWithYourName() = TwoClientRoom().use { room ->
        room.waitUntil("alice connects") { room.alice.connected }
        room.waitUntil("alice's join line") { "alice has joined the room: 'harness'" in room.alice.viewmodel.lines() }
        assertTrue("Successfully connected to server" in room.alice.viewmodel.lines())
    }

    /** The secure connection line waits for the handshake, and names the version the transport reports. */
    @Test
    fun theSecureConnectionLineComesAfterTheHandshake() = TwoClientRoom(tlsServer(failure = null)).use { room ->
        room.waitUntil("alice connects") { room.alice.connected }
        val lines = room.alice.viewmodel.lines()
        val asked = lines.indexOf("Attempting secure connection")
        val established = lines.indexOf("Secure connection established (TLSv1.3)")
        assertTrue(asked >= 0 && established > asked, "The upgrade is announced after the question: $lines")
        assertTrue(lines.indexOf("Successfully connected to server") > established, "Hello follows the upgrade: $lines")
    }

    /** A failed upgrade never claims a secure connection first. */
    @Test
    fun aFailedUpgradeNeverSaysTheConnectionIsSecure() = TwoClientRoom(tlsServer(failure = "certificate expired")).use { room ->
        room.waitUntil("alice hears the failure") {
            room.alice.viewmodel.lines().any { it.startsWith("Secure connection failed: certificate expired.") }
        }
        room.waitUntil("alice hears the connection failed") { "Connection with server failed" in room.alice.viewmodel.lines() }
        assertFalse(room.alice.viewmodel.lines().any { it.startsWith("Secure connection established") }, "${room.alice.viewmodel.lines()}")
    }

    /** The app's own server has no TLS, so a client that asks hears Syncplay's line and joins in plain text. */
    @Test
    fun aServerWithoutTlsIsNamedAndJoinedInPlainText() = TwoClientRoom(::AsksForTls).use { room ->
        room.waitUntil("alice connects") { room.alice.connected }
        val lines = room.alice.viewmodel.lines()
        assertTrue("This server does not support TLS" in lines, "$lines")
        assertFalse(lines.any { it.startsWith("Secure connection established") }, "$lines")
    }

    @Test
    fun aRequiredSecureConnectionIsRefusedWithTheSettingToChange() {
        setTlsRequired(true)
        try {
            TwoClientRoom(::AsksForTls).use { room ->
                room.waitUntil("alice hears why she did not join") {
                    "This server does not support TLS. Turn off Require encryption in Settings to join." in room.alice.viewmodel.lines()
                }
            }
        } finally {
            setTlsRequired(false)
        }
    }

    /** The argument a command needs is named in the display language, not in English. */
    @Test
    fun aCommandArgumentIsNamedInTheDisplayLanguage() = TwoClientRoom().use { room ->
        room.waitUntil("alice connects") { room.alice.connected }
        val alice = room.alice.viewmodel
        runBlocking { alice.runSlashCommand(parseSlashCommand("/room")) }
        room.waitUntil("the English reply") { "/room needs a room name." in alice.lines() }

        val before = Localization.lyricist.languageTag
        Localization.apply("de")
        try {
            runBlocking { alice.runSlashCommand(parseSlashCommand("/seek")) }
            room.waitUntil("the German reply") { "/seek braucht eine Zeit wie 1:23:45 oder +30." in alice.lines() }
        } finally {
            Localization.lyricist.languageTag = before
        }
    }

    /** As in Syncplay, a refused operator password is news only to the person who typed it. */
    @Test
    fun anotherPersonsRefusedOperatorPasswordStaysQuiet() = TwoClientRoom().use { room ->
        room.waitUntil("alice connects") { room.alice.connected }
        val alice = room.alice.viewmodel
        alice.callback.onHandleControllerAuth(ControllerAuthData(user = "bob", room = "harness", success = false))
        alice.callback.onHandleControllerAuth(ControllerAuthData(user = "alice", room = "harness", success = false))
        room.waitUntil("alice hears her own refusal") { "alice failed to identify as a room operator" in alice.lines() }
        assertEquals(emptyList(), alice.lines().filter { it.startsWith("bob failed") })
    }

    private fun RoomViewmodel.lines(): List<String> =
        session.messageSequence.value.map { it.content.replace("\u2068", "").replace("\u2069", "") }

    private fun setTlsRequired(on: Boolean) = runBlocking {
        TLS_REQUIRED.set(on)
        withTimeout(2_000) { TLS_REQUIRED.flow().first { it == on } }
    }

    /** A link to a server that offers TLS. The upgrade fails with [failure], or succeeds as TLS 1.3. */
    private fun tlsServer(failure: String?): (RoomViewmodel, SyncplayServer) -> LoopbackTransport = { viewmodel, server ->
        object : AsksForTls(viewmodel, server) {
            override suspend fun writeActualString(s: String) {
                // The app's own server has no TLS, so this link answers the TLS question itself.
                if (s.contains("\"TLS\"")) handlePacket("""{"TLS":{"startTLS":"true"}}""") else super.writeActualString(s)
            }

            override suspend fun upgradeTls() {
                if (failure != null) throw IOException(failure)
                tlsVersion = "TLSv1.3"
            }
        }
    }

    /** A link whose client asks the server for TLS before Hello. */
    private open class AsksForTls(viewmodel: RoomViewmodel, server: SyncplayServer) : LoopbackTransport(viewmodel, server) {
        override fun supportsTLS() = true
    }
}
