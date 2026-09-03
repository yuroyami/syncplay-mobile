package app.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The invite link is the one thing a stranger pastes into this app, so it is parsed defensively
 * and round-trips exactly.
 */
class InviteLinkTest {

    @Test
    fun `a built link parses back to the same room`() {
        val config = JoinConfig(user = "alice", room = "movie night", ip = "example.org", port = 8999, pw = "s3cret")
        val parsed = InviteLink.parse(InviteLink.build(config))
        assertTrue(parsed != null)
        assertEquals("movie night", parsed.room)
        assertEquals("example.org", parsed.ip)
        assertEquals(8999, parsed.port)
        assertEquals("s3cret", parsed.pw)
    }

    @Test
    fun `the official server is named, not the address the client dials`() {
        val link = InviteLink.build(JoinConfig(room = "r", ip = "151.80.32.178", port = 8997))
        assertTrue("syncplay.pl" in link, link)
        assertTrue("151.80.32.178" !in link, link)
    }

    @Test
    fun `a room with no password carries none`() {
        val link = InviteLink.build(JoinConfig(room = "r", pw = ""))
        assertTrue("password" !in link, link)
    }

    @Test
    fun `names that need encoding survive the trip`() {
        val config = JoinConfig(room = "salon & thé", ip = "h.example", pw = "a b+c%d")
        val parsed = InviteLink.parse(InviteLink.build(config))!!
        assertEquals("salon & thé", parsed.room)
        assertEquals("a b+c%d", parsed.pw)
    }

    @Test
    fun `anything that is not one of our links is refused`() {
        assertNull(InviteLink.parse("https://syncplay.pl/"))
        assertNull(InviteLink.parse("synkplay://join"))
        assertNull(InviteLink.parse("synkplay://join?server=h&port=1"))
        assertNull(InviteLink.parse("just some text"))
    }

    @Test
    fun `a hostile link cannot hand the room an unbounded name`() {
        val long = "x".repeat(5000)
        val parsed = InviteLink.parse("synkplay://join?room=$long&password=$long")!!
        assertTrue(parsed.room.length <= 34, "room was ${parsed.room.length}")
        assertTrue(parsed.pw.length <= 149, "password was ${parsed.pw.length}")
    }

    @Test
    fun `an out of range port falls back to the default`() {
        assertEquals(8997, InviteLink.parse("synkplay://join?room=r&port=70000")!!.port)
        assertEquals(8997, InviteLink.parse("synkplay://join?room=r&port=abc")!!.port)
    }

    @Test
    fun `the operator string the app prints splits into a room and a password`() {
        val (room, password) = InviteLink.splitOperatorRoom("+movies:AB12CD34EF56:XY-123-456")
        assertEquals("+movies:AB12CD34EF56", room)
        assertEquals("XY-123-456", password)
    }

    @Test
    fun `a plain room name is left alone`() {
        assertEquals("lobby" to "", InviteLink.splitOperatorRoom("lobby"))
        assertEquals("+movies:AB12CD34EF56" to "", InviteLink.splitOperatorRoom("+movies:AB12CD34EF56"))
    }
}
