package app.protocol

import app.protocol.models.RoomFeatures
import app.protocol.wire.FileData
import app.protocol.wire.HelloData
import app.protocol.wire.IgnoringOnTheFlyData
import app.protocol.wire.ListUserData
import app.protocol.wire.PingData
import app.protocol.wire.PlaystateData
import app.protocol.wire.Room
import app.protocol.wire.StateData
import app.protocol.wire.UserEvent
import app.protocol.wire.UserSetData
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Wire-format tests for the unified [WireMessage] hierarchy.
 *
 * These cover the two kinds of regressions that historically bit the protocol layer:
 *
 *  1. **Encoding shape** — the Syncplay JSON config (`explicitNulls = false`) silently
 *     drops null fields, which collapsed [WireMessage.ListRequest] to `{}` once and broke
 *     the user list end-to-end. Every variant gets a "the top-level key is present"
 *     assertion to catch any future occurrence of the same trap.
 *
 *  2. **Deserializer routing** — [WireMessageDeserializer] disambiguates the two
 *     direction-asymmetric keys (`Chat`, `List`) by payload shape. Tests cover all four
 *     branches plus the symmetric variants.
 *
 * Round-trip tests (encode → decode → assert equality of the typed object) protect against
 * either side drifting away from the other.
 */
class WireMessageTest {

    // ============================================================
    // Encoding — every top-level variant must emit its envelope key
    // ============================================================

    @Test
    fun `Hello encodes with all fields`() {
        val msg = WireMessage.Hello(
            HelloData(
                username = "alice",
                password = "deadbeef",
                room = Room(name = "lobby"),
                version = "1.7.3",
                realversion = "1.7.3",
                features = RoomFeatures()
            )
        )
        val json = syncplayJson.encodeToString(msg)
        assertContainsKey(json, "Hello")
        assertContainsKey(json, "username")
        assertContainsKey(json, "room")
    }

    @Test
    fun `State encodes with playstate and ping`() {
        val msg = WireMessage.State(
            StateData(
                playstate = PlaystateData(position = 12.5, paused = false, doSeek = false),
                ping = PingData(latencyCalculation = 1.0, clientLatencyCalculation = 2.0, clientRtt = 0.05)
            )
        )
        val json = syncplayJson.encodeToString(msg)
        assertContainsKey(json, "State")
        assertContainsKey(json, "playstate")
        assertContainsKey(json, "position")
    }

    @Test
    fun `Set room encodes`() {
        val json = syncplayJson.encodeToString(WireMessage.roomChange("newroom"))
        assertContainsKey(json, "Set")
        assertContainsKey(json, "room")
        assertContainsKey(json, "name")
    }

    @Test
    fun `Set file encodes`() {
        val json = syncplayJson.encodeToString(
            WireMessage.file(FileData(name = "movie.mkv", duration = 7200.0, size = "1024"))
        )
        assertContainsKey(json, "Set")
        assertContainsKey(json, "file")
        assertContainsKey(json, "movie.mkv")
    }

    @Test
    fun `FileData size encodes digits as a JSON number and a hash as a string`() {
        // Python wire shape: raw byte counts (and the hidden-size sentinel 0) are numbers,
        // only the 12-char privacy hash is a string.
        val numeric = syncplayJson.encodeToString(
            WireMessage.file(FileData(name = "movie.mkv", duration = 7200.0, size = "1024"))
        )
        assertTrue("\"size\":1024" in numeric, "raw size should encode as a number: $numeric")

        val hashed = syncplayJson.encodeToString(
            WireMessage.file(FileData(name = "abc123", duration = 7200.0, size = "deadbeef0000"))
        )
        assertTrue("\"size\":\"deadbeef0000\"" in hashed, "hashed size should stay a string: $hashed")
    }

    @Test
    fun `FileData size accepts JSON number from PC client at default privacy`() {
        val raw = """{"name":"ReZero.mp4","duration":1420.138,"size":351594465}"""
        val decoded = syncplayJson.decodeFromString(FileData.serializer(), raw)
        assertEquals("ReZero.mp4", decoded.name)
        assertEquals(1420.138, decoded.duration)
        assertEquals("351594465", decoded.size)
    }

    @Test
    fun `FileData size accepts JSON string from hashed-privacy client`() {
        val raw = """{"name":"a1b2c3","duration":0.0,"size":"deadbeef0000"}"""
        val decoded = syncplayJson.decodeFromString(FileData.serializer(), raw)
        assertEquals("deadbeef0000", decoded.size)
    }

    @Test
    fun `Set readiness encodes`() {
        val json = syncplayJson.encodeToString(WireMessage.readiness(isReady = true, manuallyInitiated = false))
        assertContainsKey(json, "Set")
        assertContainsKey(json, "ready")
        assertContainsKey(json, "isReady")
    }

    @Test
    fun `Set playlistChange encodes`() {
        val json = syncplayJson.encodeToString(WireMessage.playlistChange(listOf("a.mkv", "b.mkv")))
        assertContainsKey(json, "Set")
        assertContainsKey(json, "playlistChange")
        assertContainsKey(json, "files")
    }

    @Test
    fun `Set playlistIndex encodes`() {
        val json = syncplayJson.encodeToString(WireMessage.playlistIndex(2))
        assertContainsKey(json, "Set")
        assertContainsKey(json, "playlistIndex")
    }

    @Test
    fun `Set controllerAuth encodes`() {
        val json = syncplayJson.encodeToString(
            WireMessage.controllerAuth(room = "+lobby:HASH123", password = "AB-123-456")
        )
        assertContainsKey(json, "Set")
        assertContainsKey(json, "controllerAuth")
    }

    @Test
    fun `Set newControlledRoom encodes`() {
        val json = syncplayJson.encodeToString(WireMessage.newControlledRoom("+lobby:HASH123", "AB-123-456"))
        assertContainsKey(json, "Set")
        assertContainsKey(json, "newControlledRoom")
    }

    @Test
    fun `Set userBroadcast encodes`() {
        val json = syncplayJson.encodeToString(
            WireMessage.userBroadcast(mapOf("alice" to UserSetData(event = UserEvent(joined = JsonPrimitive(true)))))
        )
        assertContainsKey(json, "Set")
        assertContainsKey(json, "user")
        assertContainsKey(json, "alice")
        assertContainsKey(json, "joined")
    }

    @Test
    fun `TLS request encodes with send`() {
        val json = syncplayJson.encodeToString(WireMessage.tlsRequest())
        assertEquals("""{"TLS":{"startTLS":"send"}}""", json)
    }

    @Test
    fun `TLS response encodes true and false`() {
        assertEquals("""{"TLS":{"startTLS":"true"}}""", syncplayJson.encodeToString(WireMessage.tlsResponse(true)))
        assertEquals("""{"TLS":{"startTLS":"false"}}""", syncplayJson.encodeToString(WireMessage.tlsResponse(false)))
    }

    @Test
    fun `Error encodes with message`() {
        val json = syncplayJson.encodeToString(WireMessage.error("boom"))
        assertEquals("""{"Error":{"message":"boom"}}""", json)
    }

    /**
     * Regression: with `explicitNulls = false`, a nullable-with-default field collapses
     * out of the JSON. ListRequest's `placeholder` defaults to [JsonNull] (not Kotlin
     * `null`) so the envelope key is preserved. If someone ever switches it back to
     * `JsonElement?`, this test catches it.
     */
    @Test
    fun `ListRequest preserves the List key with null payload`() {
        val json = syncplayJson.encodeToString(WireMessage.listRequest())
        assertEquals("""{"List":null}""", json)
    }

    @Test
    fun `ListResponse encodes nested rooms map`() {
        val msg = WireMessage.ListResponse(rooms = mapOf(
            "lobby" to mapOf("alice" to ListUserData(position = 0.0, isReady = true, controller = false))
        ))
        val json = syncplayJson.encodeToString(msg)
        assertTrue(json.startsWith("""{"List":{"""))
        assertContainsKey(json, "lobby")
        assertContainsKey(json, "alice")
    }

    @Test
    fun `ChatRequest encodes bare-string payload`() {
        assertEquals("""{"Chat":"hello"}""", syncplayJson.encodeToString(WireMessage.chatRequest("hello")))
    }

    @Test
    fun `ChatBroadcast encodes object payload`() {
        val json = syncplayJson.encodeToString(WireMessage.chatBroadcast("alice", "hi"))
        assertEquals("""{"Chat":{"username":"alice","message":"hi"}}""", json)
    }

    // ============================================================
    // Deserializer — routing for all variants and asymmetric keys
    // ============================================================

    @Test fun `decodes Hello`()  = decodesAs<WireMessage.Hello>("""{"Hello":{"username":"x","room":{"name":"r"},"version":"1.7.3","realversion":"1.7.3"}}""")
    @Test fun `decodes State`()  = decodesAs<WireMessage.State>("""{"State":{"playstate":{"position":0.0,"paused":true}}}""")
    @Test fun `decodes Set`()    = decodesAs<WireMessage.Set>("""{"Set":{"ready":{"isReady":true,"manuallyInitiated":true}}}""")
    @Test fun `decodes TLS`()    = decodesAs<WireMessage.TLS>("""{"TLS":{"startTLS":"true"}}""")
    @Test fun `decodes Error`()  = decodesAs<WireMessage.Error>("""{"Error":{"message":"oops"}}""")

    @Test fun `decodes List null as ListRequest`()                = decodesAs<WireMessage.ListRequest>("""{"List":null}""")
    @Test fun `decodes List array as ListRequest`()               = decodesAs<WireMessage.ListRequest>("""{"List":[]}""")
    @Test fun `decodes List object even empty as ListResponse`()  = decodesAs<WireMessage.ListResponse>("""{"List":{}}""")
    @Test fun `decodes List populated object as ListResponse`()   = decodesAs<WireMessage.ListResponse>(
        """{"List":{"lobby":{"alice":{"position":0.0,"isReady":true,"controller":false}}}}"""
    )

    @Test fun `decodes Chat string as ChatRequest`()  = decodesAs<WireMessage.ChatRequest>("""{"Chat":"hi"}""")
    @Test fun `decodes Chat object as ChatBroadcast`() = decodesAs<WireMessage.ChatBroadcast>(
        """{"Chat":{"username":"alice","message":"hi"}}"""
    )

    @Test
    fun `unknown top-level key throws SerializationException`() {
        try {
            syncplayJson.decodeFromString(WireMessageDeserializer, """{"Banana":42}""")
            fail("Expected SerializationException")
        } catch (_: kotlinx.serialization.SerializationException) { /* expected */ }
    }

    // ============================================================
    // Round-trip — encode then decode and assert equality on key fields
    // ============================================================

    @Test
    fun `roundtrip Hello preserves fields`() = roundTrip(
        WireMessage.Hello(HelloData(username = "alice", room = Room("lobby"), version = "1.7.3", realversion = "1.7.3"))
    ) { decoded ->
        decoded as WireMessage.Hello
        assertEquals("alice", decoded.data.username)
        assertEquals("lobby", decoded.data.room?.name)
    }

    @Test
    fun `roundtrip State preserves position and pause`() = roundTrip(
        WireMessage.State(StateData(playstate = PlaystateData(position = 42.5, paused = true)))
    ) { decoded ->
        decoded as WireMessage.State
        assertEquals(42.5, decoded.data.playstate?.position)
        assertEquals(true, decoded.data.playstate?.paused)
    }

    @Test
    fun `roundtrip Set ready preserves nested fields`() = roundTrip(
        WireMessage.readiness(isReady = true, manuallyInitiated = true, username = "alice")
    ) { decoded ->
        decoded as WireMessage.Set
        assertEquals(true, decoded.data.ready?.isReady)
        assertEquals("alice", decoded.data.ready?.username)
    }

    @Test
    fun `roundtrip ListRequest is ListRequest not ListResponse`() = roundTrip(WireMessage.listRequest()) { decoded ->
        assertTrue(decoded is WireMessage.ListRequest, "Got ${decoded::class.simpleName}")
    }

    @Test
    fun `roundtrip ListResponse preserves room and user`() = roundTrip(
        WireMessage.ListResponse(mapOf("lobby" to mapOf("alice" to ListUserData(position = 1.5, isReady = true))))
    ) { decoded ->
        decoded as WireMessage.ListResponse
        assertEquals(1, decoded.rooms.size)
        val user = decoded.rooms["lobby"]?.get("alice")
        assertNotNull(user)
        assertEquals(1.5, user.position)
        assertEquals(true, user.isReady)
    }

    @Test
    fun `roundtrip ChatRequest preserves raw message`() = roundTrip(WireMessage.chatRequest("hello world")) { decoded ->
        assertEquals("hello world", (decoded as WireMessage.ChatRequest).message)
    }

    @Test
    fun `roundtrip ChatBroadcast preserves username and message`() =
        roundTrip(WireMessage.chatBroadcast("alice", "hi")) { decoded ->
            decoded as WireMessage.ChatBroadcast
            assertEquals("alice", decoded.data.username)
            assertEquals("hi", decoded.data.message)
        }

    @Test
    fun `roundtrip preserves ignoringOnTheFly counters`() = roundTrip(
        WireMessage.State(StateData(ignoringOnTheFly = IgnoringOnTheFlyData(server = 3, client = 1)))
    ) { decoded ->
        decoded as WireMessage.State
        assertEquals(3, decoded.data.ignoringOnTheFly?.server)
        assertEquals(1, decoded.data.ignoringOnTheFly?.client)
    }

    // ============================================================
    // Dispatch — visitor pattern hits the right handler method
    // ============================================================

    @Test
    fun `dispatch routes each variant to its corresponding handler method`() = runBlocking {
        val seen = mutableListOf<String>()
        val handler = object : WireMessageHandler {
            override suspend fun onHello(message: WireMessage.Hello) { seen += "Hello" }
            override suspend fun onState(message: WireMessage.State) { seen += "State" }
            override suspend fun onSet(message: WireMessage.Set) { seen += "Set" }
            override suspend fun onTLS(message: WireMessage.TLS) { seen += "TLS" }
            override suspend fun onError(message: WireMessage.Error) { seen += "Error" }
            override suspend fun onListRequest(message: WireMessage.ListRequest) { seen += "ListRequest" }
            override suspend fun onListResponse(message: WireMessage.ListResponse) { seen += "ListResponse" }
            override suspend fun onChatRequest(message: WireMessage.ChatRequest) { seen += "ChatRequest" }
            override suspend fun onChatBroadcast(message: WireMessage.ChatBroadcast) { seen += "ChatBroadcast" }
        }

        WireMessage.Hello(HelloData(username = "x")).dispatch(handler)
        WireMessage.State(StateData()).dispatch(handler)
        WireMessage.roomChange("r").dispatch(handler)
        WireMessage.tlsRequest().dispatch(handler)
        WireMessage.error("e").dispatch(handler)
        WireMessage.listRequest().dispatch(handler)
        WireMessage.ListResponse(emptyMap()).dispatch(handler)
        WireMessage.chatRequest("m").dispatch(handler)
        WireMessage.chatBroadcast("u", "m").dispatch(handler)

        assertEquals(
            listOf("Hello", "State", "Set", "TLS", "Error", "ListRequest", "ListResponse", "ChatRequest", "ChatBroadcast"),
            seen
        )
    }

    @Test
    fun `default no-op handlers don't throw for unhandled variants`() = runBlocking {
        val emptyHandler = object : WireMessageHandler {}
        // Should not throw — defaults are Unit.
        WireMessage.Hello(HelloData()).dispatch(emptyHandler)
        WireMessage.State(StateData()).dispatch(emptyHandler)
        WireMessage.listRequest().dispatch(emptyHandler)
        WireMessage.chatBroadcast("u", "m").dispatch(emptyHandler)
    }

    // ============================================================
    // Polymorphic discriminator trap — toJson must avoid it
    // ============================================================

    /**
     * Regression: Kotlinx Serialization injects a `"type"` class discriminator when you
     * encode a sealed `@Serializable` interface via the interface type itself — that
     * extra field is not legal Syncplay wire format and used to break ServerProtocolFlowTest
     * before [WireMessage.toJson] was introduced. This test pins the fix: encoding via
     * the interface goes through `toJson()` (overridden per subclass) and emits the same
     * clean shape as encoding via the concrete subclass.
     */
    @Test
    fun `toJson produces clean wire format even when message is held by interface type`() {
        val variants: List<Pair<WireMessage, String>> = listOf(
            WireMessage.Hello(HelloData(username = "alice", room = Room("lobby"), version = "1.7.3", realversion = "1.7.3")) to "Hello",
            WireMessage.State(StateData(playstate = PlaystateData(position = 1.0, paused = false))) to "State",
            WireMessage.roomChange("lobby") to "Set",
            WireMessage.tlsRequest() to "TLS",
            WireMessage.error("oops") to "Error",
            WireMessage.listRequest() to "List",
            WireMessage.ListResponse(emptyMap()) to "List",
            WireMessage.chatRequest("hi") to "Chat",
            WireMessage.chatBroadcast("alice", "hi") to "Chat",
        )
        for ((message, expectedTopKey) in variants) {
            val json = message.toJson()
            assertTrue(
                !json.contains("\"type\""),
                "toJson() for ${message::class.simpleName} leaked a class discriminator: $json"
            )
            assertContainsKey(json, expectedTopKey)
        }
    }

    // ============================================================
    // Wire-format compatibility — exact JSON matches Python protocol
    // ============================================================

    @Test
    fun `tlsRequest matches Python wire format`() {
        // syncplay/protocols.py — sendTLS sends {"TLS": {"startTLS": "send"}}
        assertEquals("""{"TLS":{"startTLS":"send"}}""", syncplayJson.encodeToString(WireMessage.tlsRequest()))
    }

    @Test
    fun `listRequest matches Python wire format`() {
        // syncplay/protocols.py — sendList sends {"List": None}
        assertEquals("""{"List":null}""", syncplayJson.encodeToString(WireMessage.listRequest()))
    }

    @Test
    fun `chatRequest matches Python wire format`() {
        // syncplay/protocols.py — sendChatMessage sends {"Chat": "<message>"}
        assertEquals("""{"Chat":"hello"}""", syncplayJson.encodeToString(WireMessage.chatRequest("hello")))
    }

    @Test
    fun `chatBroadcast matches Python server wire format`() {
        // syncplay/server.py — sendChat sends {"Chat": {"username": ..., "message": ...}}
        assertEquals(
            """{"Chat":{"username":"alice","message":"hi"}}""",
            syncplayJson.encodeToString(WireMessage.chatBroadcast("alice", "hi"))
        )
    }

    // ============================================================
    // Lenient inbound shapes — loosely-typed python protocol drift
    // ============================================================
    //
    // The wire protocol is duck-typed on the python side and periodically hands us a
    // value in a shape our strict @Serializable models don't expect. Historically each
    // such shape aborted the WHOLE message decode (one bad sub-field blanks the entire
    // user list and spins the reconnect loop). These pin the two that bit in production
    // (issue #152) and assert the decode survives. They run on the plain JVM test target
    // with no R8 / minification — proving the failures are source-level wire-shape
    // mismatches, not a release-build obfuscation problem.

    /**
     * Issue #152 (madeline-celeste, v0.22.2): a self-hosted server sent a user's `features`
     * as an empty array `[]` instead of an object. The strict RoomFeatures serializer threw
     * "Expected object, but had array … JSON input: []", aborting the List decode and
     * blanking the user-info tab. The whole line (incl. a bare-number `size`) must now decode.
     */
    @Test
    fun `List user with features as empty array decodes to defaults`() {
        val json = """{"List":{"myroom":{"alice":{"position":0,"file":{"name":"x.mkv","duration":1420.024064,"size":401358678},"controller":false,"isReady":false,"features":[]}}}}"""
        val decoded = syncplayJson.decodeFromString(WireMessageDeserializer, json)
        assertTrue(decoded is WireMessage.ListResponse, "Got ${decoded::class.simpleName}")
        val user = decoded.rooms["myroom"]?.get("alice")
        assertNotNull(user, "alice should be present despite features:[]")
        assertNotNull(user.features, "features:[] should fall back to defaults, not null")
        assertEquals(true, user.features.supportsChat)
        // bare-number `size` normalizes to its string form (FileSizeSerializer)
        assertEquals("401358678", user.file?.size)
    }

    @Test
    fun `List user with features as object preserves flags`() {
        val json = """{"List":{"r":{"bob":{"position":0,"features":{"chat":false,"readiness":true,"maxChatMessageLength":200}}}}}"""
        val decoded = syncplayJson.decodeFromString(WireMessageDeserializer, json) as WireMessage.ListResponse
        val f = decoded.rooms["r"]?.get("bob")?.features
        assertNotNull(f)
        assertEquals(false, f.supportsChat)
        assertEquals(true, f.supportsReadiness)
        assertEquals(200, f.maxChatMessageLength)
    }

    @Test
    fun `joined event carries version and features like PC`() {
        // PC server.py:167 sends {"joined": True, "version": ..., "features": {...}};
        // features must also survive the [] shape from minimal servers.
        val json = """{"Set":{"user":{"erin":{"room":{"name":"r"},"event":{"joined":true,"version":"1.7.0","features":{"chat":false}}}}}}"""
        val decoded = syncplayJson.decodeFromString(WireMessageDeserializer, json) as WireMessage.Set
        val event = decoded.data.user?.get("erin")?.event
        assertNotNull(event)
        assertEquals("1.7.0", event.version)
        assertEquals(false, event.features?.supportsChat)

        val arrayShape = """{"Set":{"user":{"frank":{"event":{"joined":true,"features":[]}}}}}"""
        val tolerant = syncplayJson.decodeFromString(WireMessageDeserializer, arrayShape) as WireMessage.Set
        assertNotNull(tolerant.data.user?.get("frank")?.event?.features)
    }

    @Test
    fun `List user with unknown future feature keys is tolerated`() {
        // PC clients send uiMode / setOthersReadiness that our model doesn't declare.
        val json = """{"List":{"r":{"carol":{"position":0,"features":{"chat":true,"uiMode":"GUI","setOthersReadiness":true}}}}}"""
        val decoded = syncplayJson.decodeFromString(WireMessageDeserializer, json) as WireMessage.ListResponse
        assertNotNull(decoded.rooms["r"]?.get("carol")?.features)
    }

    @Test
    fun `Set user broadcast with features as empty array decodes`() {
        val json = """{"Set":{"user":{"dave":{"file":{"name":"y.mkv","duration":10.0,"size":"hashed"},"features":[]}}}}"""
        val decoded = syncplayJson.decodeFromString(WireMessageDeserializer, json) as WireMessage.Set
        val u = decoded.data.user?.get("dave")
        assertNotNull(u)
        assertEquals("hashed", u.file?.size)
    }

    @Test
    fun `Hello with features as empty array decodes to defaults`() {
        val json = """{"Hello":{"username":"x","room":{"name":"r"},"version":"1.7.3","realversion":"1.7.3","features":[]}}"""
        val decoded = syncplayJson.decodeFromString(WireMessageDeserializer, json) as WireMessage.Hello
        assertEquals(true, decoded.data.features.supportsChat)
    }

    @Test
    fun `FileData size decodes from both a number and a string`() {
        val asNumber = """{"Set":{"file":{"name":"a.mkv","duration":1.0,"size":401358678}}}"""
        val asString = """{"Set":{"file":{"name":"a.mkv","duration":1.0,"size":"401358678"}}}"""
        val n = syncplayJson.decodeFromString(WireMessageDeserializer, asNumber) as WireMessage.Set
        val s = syncplayJson.decodeFromString(WireMessageDeserializer, asString) as WireMessage.Set
        assertEquals("401358678", n.data.file?.size)
        assertEquals("401358678", s.data.file?.size)
    }

    /**
     * The python server relays a roommate's file dict verbatim, so the `size` value's JSON
     * type is fully attacker/bug-controlled. A non-primitive size must surface as
     * [SerializationException] — the only type [app.protocol.network.NetworkManager]'s
     * skip-a-poisoned-line catch covers. FileSizeSerializer used `error()`
     * (IllegalStateException), which escaped that catch and killed the process.
     */
    @Test
    fun `FileData size as object or array fails as SerializationException`() {
        val asObject = """{"Set":{"user":{"eve":{"file":{"name":"z.mkv","duration":1.0,"size":{"k":1}}}}}}"""
        val asArray = """{"Set":{"user":{"eve":{"file":{"name":"z.mkv","duration":1.0,"size":[1,2]}}}}}"""
        assertFailsWith<SerializationException> {
            syncplayJson.decodeFromString(WireMessageDeserializer, asObject)
        }
        assertFailsWith<SerializationException> {
            syncplayJson.decodeFromString(WireMessageDeserializer, asArray)
        }
    }

    // ============================================================
    // Helpers
    // ============================================================

    private fun assertContainsKey(json: String, key: String) {
        assertTrue(json.contains("\"$key\""), "Expected key '$key' in JSON: $json")
    }

    private inline fun <reified T : WireMessage> decodesAs(json: String) {
        val decoded = syncplayJson.decodeFromString(WireMessageDeserializer, json)
        assertTrue(decoded is T, "Expected ${T::class.simpleName} for '$json', got ${decoded::class.simpleName}")
    }

    private inline fun <reified T : WireMessage> roundTrip(message: T, assertions: (WireMessage) -> Unit) {
        val json = syncplayJson.encodeToString(message)
        val decoded = syncplayJson.decodeFromString(WireMessageDeserializer, json)
        assertions(decoded)
    }
}
