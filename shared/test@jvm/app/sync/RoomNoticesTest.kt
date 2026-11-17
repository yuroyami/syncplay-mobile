package app.sync

import app.i18n.Localization
import app.preferences.Preferences.TLS_REQUIRED
import app.preferences.flow
import app.preferences.set
import app.protocol.wire.ControllerAuthData
import app.room.RoomViewmodel
import app.room.parseSlashCommand
import app.room.runSlashCommand
import app.room.switchRoom
import app.server.SyncplayServer
import app.server.model.ServerConfig
import app.utils.SyncClock
import app.utils.TestClock
import kotlinx.coroutines.Dispatchers
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

    /** As in Syncplay, the server does not send you your own join, so a connect writes no join line. */
    @Test
    fun yourOwnJoinHasNoLine() = TwoClientRoom().use { room ->
        room.waitUntil("alice connects") { room.alice.connected }
        room.waitUntil("the connect line") { "Successfully connected to server" in room.alice.viewmodel.lines() }
        assertFalse("alice has joined the room: 'harness'" in room.alice.viewmodel.lines(), "${room.alice.viewmodel.lines()}")
    }

    /** As in Syncplay, the message of the day comes before the connect line. */
    @Test
    fun theMessageOfTheDayComesFirst() = TwoClientRoom(serverConfig = ServerConfig(isolateRooms = false, motd = "House rules")).use { room ->
        room.waitUntil("the connect line") { "Successfully connected to server" in room.alice.viewmodel.lines() }
        val lines = room.alice.viewmodel.lines()
        assertTrue(lines.indexOf("House rules") in 0..<lines.indexOf("Successfully connected to server"), "$lines")
    }

    /** A server with persistent rooms gets Syncplay's notice before its message of the day. */
    @Test
    fun aServerWithPersistentRoomsIsAnnounced() = TwoClientRoom(
        transport = rewritesHello { it.replace("\"persistentRooms\":false", "\"persistentRooms\":true") },
        serverConfig = ServerConfig(isolateRooms = false, motd = "House rules"),
    ).use { room ->
        room.waitUntil("the connect line") { "Successfully connected to server" in room.alice.viewmodel.lines() }
        val lines = room.alice.viewmodel.lines()
        val notice = lines.indexOfFirst { it.startsWith("NOTICE: This server uses persistent rooms") }
        assertTrue(notice >= 0 && notice < lines.indexOf("House rules"), "$lines")
    }

    /** As in Syncplay, a server that leaves the persistentRooms field out has no persistent rooms. */
    @Test
    fun aMissingPersistentRoomsFieldMeansNone() = TwoClientRoom(
        transport = rewritesHello { it.replace("\"persistentRooms\":false,", "") },
    ).use { room ->
        room.waitUntil("the connect line") { "Successfully connected to server" in room.alice.viewmodel.lines() }
        assertFalse(room.alice.viewmodel.lines().any { it.startsWith("NOTICE:") }, "${room.alice.viewmodel.lines()}")
    }

    /** As in Syncplay, the room says why the playlist is missing: the server turned it off, or is too old. */
    @Test
    fun aServerWithoutSharedPlaylistsSaysWhy() {
        TwoClientRoom(transport = rewritesHello { it.replace("\"sharedPlaylists\":true", "\"sharedPlaylists\":false") }).use { room ->
            room.waitUntil("the disabled line") {
                room.alice.viewmodel.lines().any { it.startsWith("The shared playlist feature has been disabled") }
            }
            val error = room.alice.viewmodel.session.messageSequence.value.first { it.content.startsWith("The shared playlist feature") }
            assertTrue(error.isError)
        }
        TwoClientRoom(transport = rewritesHello { it.replace("\"realversion\":\"1.7.5\"", "\"realversion\":\"1.3.4\"") }).use { room ->
            room.waitUntil("the not supported line") {
                room.alice.viewmodel.lines().any { it.contains("requires a server running Syncplay 1.4.0+, but the server is running Syncplay 1.3.4.") }
            }
        }
    }

    /** As in Syncplay, the first socket of the room names the address it reached, and a reconnect does not repeat it. */
    @Test
    fun theReachedAddressIsSaidOnce() = TwoClientRoom(transport = { viewmodel, server ->
        object : LoopbackTransport(viewmodel, server) {
            override fun serverIpAddress() = "127.0.0.1"
        }
    }).use { room ->
        room.waitUntil("the connect line") { "Successfully connected to server" in room.alice.viewmodel.lines() }
        val lines = room.alice.viewmodel.lines()
        val reached = lines.indexOf("Successfully reached loopback (127.0.0.1)")
        assertTrue(reached in lines.indexOf("Attempting to connect to loopback:8999")..<lines.indexOf("Successfully connected to server"), "$lines")

        (room.alice.viewmodel.networkManager as LoopbackTransport).sever()
        room.waitUntil("alice reconnects", timeoutMs = 15_000) {
            room.alice.viewmodel.lines().count { it == "Successfully connected to server" } == 2
        }
        assertEquals(1, room.alice.viewmodel.lines().count { it.startsWith("Successfully reached") })
    }

    /** As in Syncplay, a silent server is called timed out before the reconnect line. */
    @Test
    fun aSilentServerTimesOutBeforeTheReconnect() = TwoClientRoom().use { room ->
        room.waitUntil("alice connects") { room.alice.connected }
        room.alice.viewmodel.callback.onServerTimedOut()
        room.waitUntil("the reconnect line") { "Connection with server lost, attempting to reconnect" in room.alice.viewmodel.lines() }
        val lines = room.alice.viewmodel.lines()
        assertTrue(lines.indexOf("Connection with server timed out") in 0..<lines.indexOf("Connection with server lost, attempting to reconnect"), "$lines")
    }

    /** As in Syncplay, sending an operator password writes it in chat. */
    @Test
    fun anOperatorPasswordIsShownWhenSent() = TwoClientRoom().use { room ->
        room.waitUntil("alice connects") { room.alice.connected }
        runBlocking { room.alice.viewmodel.runSlashCommand(parseSlashCommand("/op ab-123-456")) }
        room.waitUntil("the identifying line") {
            "Identifying as room operator with password 'AB-123-456'..." in room.alice.viewmodel.lines()
        }
    }

    /** As in Syncplay, a pause within 3 seconds of a leave gets one line that names both. */
    @Test
    fun aPauseRightAfterALeaveNamesBoth() = TwoClientRoom().use { room ->
        room.waitUntil("alice connects") { room.alice.connected }
        val alice = room.alice.viewmodel
        val clock = TestClock().apply { install() }
        try {
            alice.callback.onSomeoneLeft("carol")
            runBlocking(Dispatchers.Main) { alice.callback.onSomeonePaused("bob") }
            room.waitUntil("one line for both") { "carol left, bob paused" in alice.lines() }

            clock.advanceSeconds(3.5)
            runBlocking(Dispatchers.Main) { alice.callback.onSomeonePaused("bob") }
            room.waitUntil("the plain pause line") { alice.lines().any { it.startsWith("bob paused at ") } }
        } finally {
            SyncClock.reset()
        }
    }

    /** As in Syncplay, the line about your own file names the room, and other people hear it without the room. */
    @Test
    fun yourOwnFileLineNamesTheRoom() = TwoClientRoom().use { room ->
        room.joinAndLoad()
        room.waitUntil("alice hears her own file with the room") {
            room.alice.viewmodel.lines().any { it.startsWith("alice is playing 'clip.mp4'") && it.endsWith(" in room: 'harness'") }
        }
        room.waitUntil("bob hears alice's file") { room.bob.viewmodel.lines().any { it.startsWith("alice is playing 'clip.mp4'") } }
        assertFalse(room.bob.viewmodel.lines().any { it.startsWith("alice is playing") && "in room" in it }, "${room.bob.viewmodel.lines()}")
    }

    /** As in Syncplay, the slowdown and the return to normal speed are written in chat. */
    @Test
    fun aSlowdownIsWrittenInChat() = TwoClientRoom().use { room ->
        room.joinAndLoad()
        room.alice.play()
        room.waitUntil("both play") { room.alice.player.playing && room.bob.player.playing }

        room.alice.player.jumpBy(2_000)
        room.waitUntil("alice hears the slowdown") {
            room.alice.viewmodel.lines().any { it.startsWith("Slowing down due to time difference with ") }
        }
        room.alice.player.jumpBy(-2_000)
        room.waitUntil("alice hears the normal speed", timeoutMs = 15_000) {
            "Reverting speed back to normal" in room.alice.viewmodel.lines()
        }
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

    /** As in Syncplay, every room switch writes a join line that names the new room. */
    @Test
    fun aRoomSwitchIsAnnouncedOnBothSides() = TwoClientRoom().use { room ->
        room.waitUntil("both connect") { room.alice.connected && room.bob.connected }
        val alice = room.alice.viewmodel
        val bob = room.bob.viewmodel
        room.waitUntil("bob sees alice") { bob.session.userList.value.any { it.name == "alice" } }

        alice.switchRoom("other")
        room.waitUntil("alice hears her own switch") { "alice has joined the room: 'other'" in alice.lines() }
        room.waitUntil("bob hears where alice went") { "alice has joined the room: 'other'" in bob.lines() }
        room.waitUntil("alice leaves bob's list") { bob.session.userList.value.none { it.name == "alice" } }

        bob.switchRoom("other")
        room.waitUntil("alice hears bob join her room") { "bob has joined the room: 'other'" in alice.lines() }
    }

    /** As in Syncplay, an operator success in another room stays quiet, even when the server sends it to every room. */
    @Test
    fun anOperatorInAnotherRoomStaysQuiet() = TwoClientRoom().use { room ->
        room.waitUntil("alice connects") { room.alice.connected }
        val alice = room.alice.viewmodel
        alice.callback.onHandleControllerAuth(ControllerAuthData(user = "carol", room = "+other:ABC", success = true))
        alice.callback.onHandleControllerAuth(ControllerAuthData(user = "bob", room = "harness", success = true))
        room.waitUntil("alice hears the operator of her room") { "bob authenticated as a room operator" in alice.lines() }
        assertEquals(emptyList(), alice.lines().filter { it.startsWith("carol") })
    }

    private fun RoomViewmodel.lines(): List<String> =
        session.messageSequence.value.map { it.content.replace("\u2068", "").replace("\u2069", "") }

    /** A link that changes the server's Hello line with [rewrite] before the client reads it. */
    private fun rewritesHello(rewrite: (String) -> String): (RoomViewmodel, SyncplayServer) -> LoopbackTransport = { viewmodel, server ->
        object : LoopbackTransport(viewmodel, server) {
            override fun deliver(line: String) = super.deliver(if ("\"Hello\"" in line) rewrite(line) else line)
        }
    }

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
