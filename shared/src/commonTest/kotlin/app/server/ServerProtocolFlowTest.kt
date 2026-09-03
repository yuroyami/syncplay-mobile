package app.server

import app.protocol.WireMessage
import app.protocol.WireMessageDeserializer
import app.protocol.syncplayJson
import app.protocol.wire.HelloData
import app.protocol.wire.PingData
import app.protocol.wire.PlaystateData
import app.protocol.wire.StateData
import app.utils.md5
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import app.protocol.wire.Room
import app.server.model.ServerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Server-side integration tests: drive [SyncplayServer] / [ClientConnection] with raw JSON lines
 * and assert on what they emit, simulating real clients over a fake socket. Each test wires a
 * fresh [TestClient] into a shared [SyncplayServer] instance so multiple clients can join the
 * same room and observe each other's broadcasts.
 */
@OptIn(ExperimentalStdlibApi::class)
class ServerProtocolFlowTest {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @AfterTest
    fun teardown() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    /**
     * Fake client connection: captures everything the server sends and lets the test push raw
     * JSON lines back as if from a real socket. Outbound encoding goes through [WireMessage.toJson]
     * so the wire format stays correct even when a test holds an interface-typed reference.
     */
    class TestClient(server: SyncplayServer) {
        /* Guarded: the server's per-watcher state timer writes here from its own thread while the
         * test reads, so a plain list could tear or drop an entry and turn a real failure green. */
        private val lock = SynchronizedObject()
        private val captured = mutableListOf<WireMessage>()

        /** A snapshot; iterate this, never the live list. */
        val sent: List<WireMessage> get() = synchronized(lock) { captured.toList() }

        @Volatile
        var dropped = false
            private set

        val connection: ClientConnection = ClientConnection(
            server = server,
            sendFn = { line ->
                val message = syncplayJson.decodeFromString(WireMessageDeserializer, line)
                synchronized(lock) { captured += message }
            },
            dropFn = { dropped = true }
        )

        fun clearSent() = synchronized(lock) { captured.clear() }

        suspend fun receive(message: WireMessage) {
            connection.handlePacket(message.toJson())
        }

        suspend fun receiveRaw(json: String) = connection.handlePacket(json)

        inline fun <reified T : WireMessage> lastOf(): T? = sent.filterIsInstance<T>().lastOrNull()
        inline fun <reified T : WireMessage> allOf(): List<T> = sent.filterIsInstance<T>()
    }

    private fun server(config: ServerConfig = ServerConfig(isolateRooms = false)) =
        SyncplayServer(config, scope)

    private fun helloFor(username: String, room: String, password: String? = null) = WireMessage.Hello(
        HelloData(
            username = username,
            password = password,
            room = Room(name = room),
            version = "1.7.3",
            realversion = "1.7.3"
        )
    )

    // -----------------------------------------------------------
    // Hello handshake
    // -----------------------------------------------------------

    @Test
    fun `server replies with Hello after a successful handshake`(): Unit = runBlocking {
        val client = TestClient(server())
        client.receive(helloFor("alice", "lobby"))

        val helloReply = client.lastOf<WireMessage.Hello>()
        assertNotNull(helloReply, "Server should send a Hello reply")
        assertEquals("alice", helloReply.data.username)
        assertEquals("lobby", helloReply.data.room?.name)
        assertEquals(false, client.dropped)
    }

    @Test
    fun `server drops a client whose Hello is missing required fields`(): Unit = runBlocking {
        val client = TestClient(server())
        client.receive(WireMessage.Hello(HelloData(username = "")))
        assertEquals(true, client.dropped, "Server should drop a malformed Hello")
        val error = client.lastOf<WireMessage.Error>()
        assertNotNull(error)
    }

    @Test
    fun `server drops a client supplying a wrong password`(): Unit = runBlocking {
        val client = TestClient(server(ServerConfig(password = "secret")))
        // MD5("wrong") doesn't match MD5("secret").
        client.receive(helloFor("alice", "lobby", password = "wronghash"))
        assertEquals(true, client.dropped)
    }

    // -----------------------------------------------------------
    // List request
    // -----------------------------------------------------------

    @Test
    fun `ListRequest after Hello yields a populated ListResponse`(): Unit = runBlocking {
        val client = TestClient(server())
        client.receive(helloFor("alice", "lobby"))
        client.clearSent()

        client.receive(WireMessage.listRequest())

        val list = client.lastOf<WireMessage.ListResponse>()
        assertNotNull(list, "Server should send a ListResponse for a List request")
        assertTrue(list.rooms.containsKey("lobby"), "Response should include alice's room")
        assertTrue(list.rooms["lobby"]!!.containsKey("alice"), "Response should include alice")
    }

    /**
     * A bare `{}` line carries no recognizable top-level key, so it must be treated as unparseable
     * and drop the client rather than being silently accepted as a list request.
     */
    @Test
    fun `empty object payload is unparseable and drops the client`(): Unit = runBlocking {
        val client = TestClient(server())
        client.receive(helloFor("alice", "lobby"))
        client.clearSent()

        client.receiveRaw("{}")

        assertEquals(true, client.dropped, "An empty {} should not be silently treated as a list request")
    }

    @Test
    fun `a correct server password is accepted`(): Unit = runBlocking {
        val client = TestClient(server(ServerConfig(password = "secret")))
        // The client sends the MD5 hex of the password, which is what the server compares against.
        val hashed = md5("secret").toHexString(HexFormat.Default)
        client.receive(helloFor("alice", "lobby", password = hashed))

        assertEquals(false, client.dropped, "the right password must get in")
        assertNotNull(client.lastOf<WireMessage.Hello>(), "an accepted client gets a Hello back")
    }

    @Test
    fun `ListRequest before Hello is rejected`(): Unit = runBlocking {
        val client = TestClient(server())
        client.receive(WireMessage.listRequest())
        // The gate itself, not just its side effect: an unauthenticated request is answered with
        // an error and the socket goes. Asserting only "no list came back" passed even with the
        // whole requireLogged() check deleted, because sendList returns early with no watcher.
        assertEquals(true, client.dropped, "an unauthenticated request must drop the connection")
        assertNotNull(client.lastOf<WireMessage.Error>(), "and say why")
        // Either dropped or no list response — but definitely no list reply since not logged in.
        assertEquals(0, client.allOf<WireMessage.ListResponse>().size)
    }

    // -----------------------------------------------------------
    // Multi-client room broadcast
    // -----------------------------------------------------------

    @Test
    fun `joining clients see each other in the user list`(): Unit = runBlocking {
        val srv = server()
        val alice = TestClient(srv)
        val bob = TestClient(srv)

        alice.receive(helloFor("alice", "lobby"))
        bob.receive(helloFor("bob", "lobby"))

        bob.clearSent()
        bob.receive(WireMessage.listRequest())

        val list = bob.lastOf<WireMessage.ListResponse>()
        assertNotNull(list)
        val users = list.rooms["lobby"]
        assertNotNull(users)
        assertTrue(users.containsKey("alice"), "Bob should see alice in the user list")
        assertTrue(users.containsKey("bob"), "Bob should see himself in the user list")
    }

    @Test
    fun `server broadcasts a join event to existing room members`(): Unit = runBlocking {
        val srv = server()
        val alice = TestClient(srv)
        val bob = TestClient(srv)

        alice.receive(helloFor("alice", "lobby"))
        alice.clearSent()

        bob.receive(helloFor("bob", "lobby"))

        // Alice should receive a Set with `user.<bob>.event.joined`.
        val set = alice.allOf<WireMessage.Set>().firstOrNull { it.data.user?.containsKey("bob") == true }
        assertNotNull(set, "Alice should be notified that bob joined")
        val bobEvent = set.data.user!!["bob"]!!
        val event = bobEvent.event
        assertNotNull(event, "User entry should carry an event")
        assertNotNull(event.joined, "Event should be a join")
    }

    @Test
    fun `username collisions are auto-suffixed`(): Unit = runBlocking {
        val srv = server()
        val first = TestClient(srv)
        val second = TestClient(srv)

        first.receive(helloFor("alice", "lobby"))
        second.receive(helloFor("alice", "lobby"))

        val firstHello = first.lastOf<WireMessage.Hello>()!!
        val secondHello = second.lastOf<WireMessage.Hello>()!!
        assertEquals("alice", firstHello.data.username)
        assertTrue(secondHello.data.username != "alice", "Second alice should be renamed (e.g. alice_)")
    }

    // -----------------------------------------------------------
    // Chat
    // -----------------------------------------------------------

    @Test
    fun `chat from one client is broadcast to others as a ChatBroadcast`(): Unit = runBlocking {
        val srv = server()
        val alice = TestClient(srv)
        val bob = TestClient(srv)

        alice.receive(helloFor("alice", "lobby"))
        bob.receive(helloFor("bob", "lobby"))
        bob.clearSent()

        alice.receive(WireMessage.chatRequest("hello bob"))

        val chat = bob.lastOf<WireMessage.ChatBroadcast>()
        assertNotNull(chat, "Bob should receive a ChatBroadcast")
        assertEquals("alice", chat.data.username)
        assertEquals("hello bob", chat.data.message)
    }

    @Test
    fun `chat is suppressed when the server has chat disabled`(): Unit = runBlocking {
        val srv = server(ServerConfig(isolateRooms = false, disableChat = true))
        val alice = TestClient(srv)
        val bob = TestClient(srv)

        alice.receive(helloFor("alice", "lobby"))
        bob.receive(helloFor("bob", "lobby"))
        bob.clearSent()

        alice.receive(WireMessage.chatRequest("hello bob"))
        assertEquals(0, bob.allOf<WireMessage.ChatBroadcast>().size, "Chat should be dropped on server side")
    }

    // -----------------------------------------------------------
    // Set sub-commands
    // -----------------------------------------------------------

    @Test
    fun `setting a file is broadcast to room peers`(): Unit = runBlocking {
        val srv = server()
        val alice = TestClient(srv)
        val bob = TestClient(srv)

        alice.receive(helloFor("alice", "lobby"))
        bob.receive(helloFor("bob", "lobby"))
        bob.clearSent()

        alice.receive(WireMessage.file(app.protocol.wire.FileData(name = "movie.mkv", duration = 7200.0, size = "1024")))

        val fileSet = bob.allOf<WireMessage.Set>().lastOrNull { it.data.user?.get("alice")?.file != null }
        assertNotNull(fileSet, "Bob should be notified of alice's file")
        assertEquals("movie.mkv", fileSet.data.user!!["alice"]?.file?.name)
    }

    @Test
    fun `room change moves the watcher to a different room`(): Unit = runBlocking {
        val srv = server()
        val alice = TestClient(srv)
        alice.receive(helloFor("alice", "lobby"))
        alice.clearSent()

        alice.receive(WireMessage.roomChange("foyer"))
        alice.receive(WireMessage.listRequest())

        val list = alice.lastOf<WireMessage.ListResponse>()
        assertNotNull(list)
        assertTrue(list.rooms.containsKey("foyer"), "Alice should now be listed in 'foyer'")
        assertTrue(list.rooms["foyer"]!!.containsKey("alice"))
    }

    // -----------------------------------------------------------
    // State handling
    // -----------------------------------------------------------

    private fun stateOf(position: Double?, paused: Boolean?, ping: PingData? = null) = WireMessage.State(
        StateData(
            playstate = if (position == null && paused == null) null else PlaystateData(position = position, paused = paused),
            ping = ping
        )
    )

    private suspend fun TestClient.positionSeenByServer(name: String, room: String): Double? {
        clearSent()
        receive(WireMessage.listRequest())
        return lastOf<WireMessage.ListResponse>()?.rooms?.get(room)?.get(name)?.position
    }

    @Test
    fun `a ping-only State does not rewind the watcher to zero`(): Unit = runBlocking {
        val srv = server()
        val alice = TestClient(srv)
        alice.receive(helloFor("alice", "lobby"))
        alice.receive(WireMessage.file(app.protocol.wire.FileData(name = "movie.mkv", duration = 7200.0, size = "1")))

        alice.receive(stateOf(position = 100.0, paused = true))
        assertEquals(100.0, alice.positionSeenByServer("alice", "lobby"))

        // A keep-alive with a ping and no playstate must leave the position alone.
        alice.receive(stateOf(position = null, paused = null, ping = PingData(clientRtt = 0.05, clientLatencyCalculation = 1.0)))
        assertEquals(100.0, alice.positionSeenByServer("alice", "lobby"))
    }

    @Test
    fun `a missing ping echo does not age the position by decades`(): Unit = runBlocking {
        val srv = server()
        val alice = TestClient(srv)
        alice.receive(helloFor("alice", "lobby"))
        alice.receive(WireMessage.file(app.protocol.wire.FileData(name = "movie.mkv", duration = 7200.0, size = "1")))

        // Playing, with a ping block that carries no latencyCalculation echo: the server must not
        // compute an RTT against the epoch and add half of it as message age.
        alice.receive(stateOf(position = 100.0, paused = false, ping = PingData(clientRtt = 0.05, clientLatencyCalculation = 1.0)))
        val seen = alice.positionSeenByServer("alice", "lobby")
        assertNotNull(seen)
        assertTrue(seen in 100.0..101.0, "position should stay near 100, was $seen")
    }

    @Test
    fun `a client that stops sending State is dropped after the protocol timeout`(): Unit = runBlocking {
        val srv = server(ServerConfig(isolateRooms = false, protocolTimeoutSeconds = 0.3))
        val alice = TestClient(srv)
        alice.receive(helloFor("alice", "lobby"))
        assertEquals(false, alice.dropped)

        // The state timer ticks once a second; two ticks are enough for the timeout to be seen.
        kotlinx.coroutines.delay(2_500)
        assertEquals(true, alice.dropped, "a silent client must be dropped")

        val bob = TestClient(srv)
        bob.receive(helloFor("bob", "lobby"))
        bob.clearSent()
        bob.receive(WireMessage.listRequest())
        val users = bob.lastOf<WireMessage.ListResponse>()!!.rooms["lobby"]!!
        assertTrue("alice" !in users, "the dropped watcher must leave the room")
    }

    @Test
    fun `a playlistChange without files does not wipe the room playlist`(): Unit = runBlocking {
        val srv = server()
        val alice = TestClient(srv)
        val bob = TestClient(srv)
        alice.receive(helloFor("alice", "lobby"))
        bob.receive(helloFor("bob", "lobby"))

        alice.receive(WireMessage.playlistChange(listOf("a.mkv", "b.mkv")))
        bob.clearSent()
        alice.receiveRaw("""{"Set":{"playlistChange":{"user":"alice"}}}""")
        assertEquals(0, bob.allOf<WireMessage.Set>().count { it.data.playlistChange != null }, "no playlist broadcast for a fileless change")

        bob.clearSent()
        bob.receive(WireMessage.roomChange("lobby"))
        val playlist = bob.allOf<WireMessage.Set>().last { it.data.playlistChange != null }.data.playlistChange!!.files
        assertEquals(listOf("a.mkv", "b.mkv"), playlist)
    }

    // -----------------------------------------------------------
    // Controlled rooms
    // -----------------------------------------------------------

    /** Creates a controlled room the way a client does: ask in a plain room, then join the minted name. */
    private suspend fun TestClient.createControlledRoom(baseRoom: String, password: String): String {
        receive(WireMessage.controllerAuth(room = baseRoom, password = password))
        val minted = lastOf<WireMessage.Set>()!!.data.newControlledRoom!!
        receive(WireMessage.roomChange(minted.roomName))
        receive(WireMessage.controllerAuth(room = minted.roomName, password = minted.password))
        return minted.roomName
    }

    @Test
    fun `the right password grants control of its own room`(): Unit = runBlocking {
        val srv = server()
        val alice = TestClient(srv)
        alice.receive(helloFor("alice", "lobby"))
        val room = alice.createControlledRoom("lobby", "AB-123-456")

        val auth = alice.allOf<WireMessage.Set>().last { it.data.controllerAuth != null }.data.controllerAuth!!
        assertEquals(true, auth.success)
        alice.clearSent()
        alice.receive(WireMessage.listRequest())
        assertEquals(true, alice.lastOf<WireMessage.ListResponse>()!!.rooms[room]!!["alice"]!!.controller)
    }

    @Test
    fun `a valid password for another controlled room grants nothing`(): Unit = runBlocking {
        val srv = server()
        val alice = TestClient(srv)
        val mallory = TestClient(srv)
        alice.receive(helloFor("alice", "lobby"))
        mallory.receive(helloFor("mallory", "den"))
        val aliceRoom = alice.createControlledRoom("lobby", "AB-123-456")
        // Mallory mints a room of her own, so she holds one valid controlled-room password.
        val malloryRoom = mallory.createControlledRoom("den", "CD-789-012")

        // She then walks into alice's room and presents the password she legitimately owns.
        mallory.receive(WireMessage.roomChange(aliceRoom))
        mallory.clearSent()
        mallory.receive(WireMessage.controllerAuth(room = malloryRoom, password = "CD-789-012"))

        val auth = mallory.allOf<WireMessage.Set>().last { it.data.controllerAuth != null }.data.controllerAuth!!
        assertEquals(false, auth.success, "a password proves control of the room it was minted for")

        mallory.clearSent()
        mallory.receive(WireMessage.listRequest())
        assertEquals(false, mallory.lastOf<WireMessage.ListResponse>()!!.rooms[aliceRoom]!!["mallory"]!!.controller)
    }

    @Test
    fun `a second Hello on a live connection is refused`(): Unit = runBlocking {
        val client = TestClient(server())
        client.receive(helloFor("alice", "lobby"))
        client.receive(helloFor("alice2", "lobby"))
        assertEquals(true, client.dropped)
    }

    @Test
    fun `an isolated room switch re-announces the file to the new room`(): Unit = runBlocking {
        val srv = server(ServerConfig(isolateRooms = true))
        val alice = TestClient(srv)
        val bob = TestClient(srv)
        alice.receive(helloFor("alice", "lobby"))
        alice.receive(WireMessage.file(app.protocol.wire.FileData(name = "movie.mkv", duration = 7200.0, size = "1")))
        bob.receive(helloFor("bob", "foyer"))
        bob.clearSent()

        alice.receive(WireMessage.roomChange("foyer"))

        val fileSet = bob.allOf<WireMessage.Set>().lastOrNull { it.data.user?.get("alice")?.file != null }
        assertNotNull(fileSet, "bob should learn alice's file when she switches into his room")
        assertEquals("movie.mkv", fileSet.data.user!!["alice"]?.file?.name)
    }

    // -----------------------------------------------------------
    // Bad input handling
    // -----------------------------------------------------------

    @Test
    fun `garbage JSON drops the client with an Error`(): Unit = runBlocking {
        val client = TestClient(server())
        client.receiveRaw("not json at all")
        assertEquals(true, client.dropped)
    }

    @Test
    fun `unknown top-level key drops the client`(): Unit = runBlocking {
        val client = TestClient(server())
        client.receive(helloFor("alice", "lobby"))
        client.clearSent()
        client.receiveRaw("""{"NotARealMessage":42}""")
        assertEquals(true, client.dropped)
    }
}
