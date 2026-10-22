package app.server.model

import org.kotlincrypto.hash.sha1.SHA1
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Tests for [RoomPasswordProvider], the hash chain behind controlled rooms. In a controlled room,
 * only operators who know the room password can control playback.
 *
 * The hash must match the Python reference server exactly. Otherwise a controlled room created on
 * a Python server cannot be authenticated on the app's server, and the other way round. The parity
 * tests pin the algorithm to `syncplay/utils.py` in the Syncplay PC source.
 */
@OptIn(ExperimentalStdlibApi::class)
class RoomPasswordProviderTest {

    private val testSalt = "testsalt12"
    private val validPassword = "AB-123-456"

    // -----------------------------------------------------------
    // Hash algorithm: step-by-step Python parity
    // -----------------------------------------------------------

    /**
     * The expected value comes from the Python reference implementation, not from this code:
     *
     *   salt   = sha256("testsalt12").hexdigest()
     *   prov   = sha256("lobby" + salt).hexdigest()
     *   sha1(prov + salt + "AB-123-456").hexdigest()[:12].upper()  ->  EB6501C30B97
     *
     * The next test recomputes the chain with the same hash library. That proves the steps are
     * wired together, but it misses a drift from Python that the code and the test share.
     */
    @Test
    fun `hash matches the value the Python server produces`() {
        assertEquals(
            "+lobby:EB6501C30B97",
            RoomPasswordProvider.getControlledRoomName("lobby", validPassword, testSalt),
        )
    }

    /**
     * Replicates the Python algorithm from `syncplay/utils.py` and checks that the app's
     * implementation matches:
     *
     *   salt = sha256(salt).hexdigest()
     *   provisional = sha256(roomName + salt).hexdigest()
     *   return sha1(provisional + salt + password).hexdigest()[:12].upper()
     */
    @Test
    fun `hash matches Python step-by-step computation`() {
        val roomName = "lobby"
        val saltHash = SHA256().digest(testSalt.encodeToByteArray()).toHexString()
        val provisional = SHA256().digest((roomName + saltHash).encodeToByteArray()).toHexString()
        val expectedHash = SHA1().digest((provisional + saltHash + validPassword).encodeToByteArray())
            .toHexString().take(12).uppercase()

        // The provider should produce a controlled room name like "+lobby:<expectedHash>".
        val controlledName = RoomPasswordProvider.getControlledRoomName(roomName, validPassword, testSalt)
        assertEquals("+$roomName:$expectedHash", controlledName)
    }

    @Test
    fun `same inputs produce same hash deterministically`() {
        val a = RoomPasswordProvider.getControlledRoomName("room1", validPassword, testSalt)
        val b = RoomPasswordProvider.getControlledRoomName("room1", validPassword, testSalt)
        assertEquals(a, b)
    }

    @Test
    fun `different password produces different hash`() {
        val a = RoomPasswordProvider.getControlledRoomName("lobby", "AB-123-456", testSalt)
        val b = RoomPasswordProvider.getControlledRoomName("lobby", "AB-123-457", testSalt)
        assertTrue(a != b, "Different passwords must yield different controlled room names")
    }

    @Test
    fun `different salt produces different hash`() {
        val a = RoomPasswordProvider.getControlledRoomName("lobby", validPassword, "salt-A")
        val b = RoomPasswordProvider.getControlledRoomName("lobby", validPassword, "salt-B")
        assertTrue(a != b)
    }

    @Test
    fun `different room name produces different hash`() {
        val a = RoomPasswordProvider.getControlledRoomName("lobby", validPassword, testSalt)
        val b = RoomPasswordProvider.getControlledRoomName("foyer", validPassword, testSalt)
        assertTrue(a != b)
    }

    // -----------------------------------------------------------
    // check(): round-trip with right and wrong inputs
    // -----------------------------------------------------------

    @Test
    fun `check returns true for the correct password`() {
        val controlledName = RoomPasswordProvider.getControlledRoomName("lobby", validPassword, testSalt)
        assertTrue(RoomPasswordProvider.check(controlledName, validPassword, testSalt))
    }

    @Test
    fun `check returns false for the wrong password`() {
        val controlledName = RoomPasswordProvider.getControlledRoomName("lobby", validPassword, testSalt)
        assertFalse(RoomPasswordProvider.check(controlledName, "ZZ-999-999", testSalt))
    }

    @Test
    fun `check returns false for the wrong salt`() {
        val controlledName = RoomPasswordProvider.getControlledRoomName("lobby", validPassword, testSalt)
        assertFalse(RoomPasswordProvider.check(controlledName, validPassword, "different-salt"))
    }

    @Test
    fun `check throws for non-controlled room name`() {
        try {
            RoomPasswordProvider.check("plain-room", validPassword, testSalt)
            fail("Expected NotControlledRoomException")
        } catch (_: NotControlledRoomException) { /* expected */ }
    }

    @Test
    fun `check throws for empty password`() {
        try {
            RoomPasswordProvider.check("+lobby:HASH12CHAR12", "", testSalt)
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { /* expected */ }
    }

    @Test
    fun `check throws for malformed password`() {
        for (bad in listOf("ABC-123-456", "AB-12-456", "ab-123-456", "AB-1234-56", "AB123456", "")) {
            try {
                RoomPasswordProvider.check("+lobby:HASH12CHAR12", bad, testSalt)
                fail("Expected IllegalArgumentException for password '$bad'")
            } catch (_: IllegalArgumentException) { /* expected */ }
        }
    }

    // -----------------------------------------------------------
    // Format / regex predicates
    // -----------------------------------------------------------

    @Test
    fun `isControlledRoom recognizes valid controlled room names`() {
        assertTrue(RoomPasswordProvider.isControlledRoom("+lobby:ABCDEF123456"))
        assertTrue(RoomPasswordProvider.isControlledRoom("+room with spaces:ABCDEF123456"))
        assertTrue(RoomPasswordProvider.isControlledRoom("+x:ABCDEF123456"))
    }

    @Test
    fun `isControlledRoom rejects names without the leading plus`() {
        assertFalse(RoomPasswordProvider.isControlledRoom("lobby:ABCDEF123456"))
    }

    @Test
    fun `isControlledRoom rejects wrong-length hashes`() {
        assertFalse(RoomPasswordProvider.isControlledRoom("+lobby:SHORT"))
        assertFalse(RoomPasswordProvider.isControlledRoom("+lobby:ABCDEF1234567")) // 13 chars
    }

    @Test
    fun `isControlledRoom rejects non-controlled rooms`() {
        assertFalse(RoomPasswordProvider.isControlledRoom("plain"))
        assertFalse(RoomPasswordProvider.isControlledRoom(""))
        assertFalse(RoomPasswordProvider.isControlledRoom("+nohashmissing"))
    }

    // -----------------------------------------------------------
    // generateRoomPassword(): format only (random output)
    // -----------------------------------------------------------

    @Test
    fun `generated passwords match the XX-NNN-NNN format`() {
        val regex = Regex("[A-Z]{2}-\\d{3}-\\d{3}")
        repeat(20) {
            val pw = RoomPasswordProvider.generateRoomPassword()
            assertTrue(regex.matches(pw), "Generated '$pw' does not match XX-###-###")
        }
    }

    @Test
    fun `baseName strips a managed suffix and leaves plain names alone`() {
        assertEquals("movie", RoomPasswordProvider.baseName("+movie:ABCDEF123456"))
        assertEquals("movie", RoomPasswordProvider.baseName("movie"))
        // A plus with no hash is not a managed name, so it is its own base.
        assertEquals("+odd", RoomPasswordProvider.baseName("+odd"))
    }
}
