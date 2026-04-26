package app.server

import app.protocol.WireMessage
import app.protocol.WireMessageDeserializer
import app.protocol.syncplayJson
import app.protocol.wire.HelloData
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
 * Server-side integration tests — drive [SyncplayServer] / [ClientConnection] with raw
 * JSON lines and inspect what they emit, simulating a real client over a fake socket.
 *
 * These would have caught the [WireMessage.ListRequest] regression at the integration
 * layer too: the server never replied with a List, the user list stayed empty, and you
 * see it directly in the captured outbound packets.
 *
 * Each test wires a fresh [TestClient] into a single-instance [SyncplayServer], so two
 * "clients" can join the same room and observe each other's broadcasts.
 */
class ServerProtocolFlowTest {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @AfterTest
    fun teardown() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    /**
     * Fake client connection: captures everything the server sends and lets the test
     * push raw JSON lines back as if they came from a real socket. Encoding goes through
     * [WireMessage.toJson] so even if a test passes an interface-typed reference, the
     * wire format stays correct.
     */
    class TestClient(server: SyncplayServer) {
        val sent = mutableListOf<WireMessage>()
        var dropped = false
        val connection: ClientConnection = ClientConnection(
            server = server,
            sendFn = { line -> sent += syncplayJson.decodeFromString(WireMessageDeserializer, line) },
            dropFn = { dropped = true }
        )

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
    // List request — the bug we just fixed
    // -----------------------------------------------------------

    @Test
    fun `ListRequest after Hello yields a populated ListResponse`(): Unit = runBlocking {
        val client = TestClient(server())
        client.receive(helloFor("alice", "lobby"))
        client.sent.clear()

        client.receive(WireMessage.listRequest())

        val list = client.lastOf<WireMessage.ListResponse>()
        assertNotNull(list, "Server should send a ListResponse for a List request")
        assertTrue(list.rooms.containsKey("lobby"), "Response should include alice's room")
        assertTrue(list.rooms["lobby"]!!.containsKey("alice"), "Response should include alice")
    }

    /**
     * Bug repro: feeds the broken `{}` shape that the previous
     * `ListRequest(@SerialName("List") val placeholder: JsonElement? = null)` would emit.
     * The server should drop the client because it's an unparseable line — proving that
     * the only reason this didn't manifest as a crash was the silent SerializationException.
     */
    @Test
    fun `empty object payload is unparseable and drops the client`(): Unit = runBlocking {
        val client = TestClient(server())
        client.receive(helloFor("alice", "lobby"))
        client.sent.clear()

        client.receiveRaw("{}")

        assertEquals(true, client.dropped, "An empty {} should not be silently treated as a list request")
    }

    @Test
    fun `ListRequest before Hello is rejected`(): Unit = runBlocking {
        val client = TestClient(server())
        client.receive(WireMessage.listRequest())
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

        bob.sent.clear()
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
        alice.sent.clear()

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
        bob.sent.clear()

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
        bob.sent.clear()

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
        bob.sent.clear()

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
        alice.sent.clear()

        alice.receive(WireMessage.roomChange("foyer"))
        alice.receive(WireMessage.listRequest())

        val list = alice.lastOf<WireMessage.ListResponse>()
        assertNotNull(list)
        assertTrue(list.rooms.containsKey("foyer"), "Alice should now be listed in 'foyer'")
        assertTrue(list.rooms["foyer"]!!.containsKey("alice"))
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
        client.sent.clear()
        client.receiveRaw("""{"NotARealMessage":42}""")
        assertEquals(true, client.dropped)
    }
}
