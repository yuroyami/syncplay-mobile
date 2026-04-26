package app.server.model

import org.kotlincrypto.hash.sha1.SHA1
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Tests for [RoomPasswordProvider] — the controlled-room hash chain.
 *
 * Wire-compatibility with the Python reference server is non-negotiable: a divergence
 * here means a controlled room created on a Python server can't be authenticated on a
 * mobile server (and vice versa). The hash-parity test pins the algorithm step by step
 * against the Python spec from `syncplay-pc-src-master/syncplay/utils.py`.
 */
@OptIn(ExperimentalStdlibApi::class)
class RoomPasswordProviderTest {

    private val testSalt = "testsalt12"
    private val validPassword = "AB-123-456"

    // -----------------------------------------------------------
    // Hash algorithm — step-by-step Python parity
    // -----------------------------------------------------------

    /**
     * Replicates the Python algorithm verbatim and asserts our implementation matches.
     * From `syncplay-pc-src-master/syncplay/utils.py`:
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
    // check() — round-trip with right and wrong inputs
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
    // generateRoomPassword() — format only (random output)
    // -----------------------------------------------------------

    @Test
    fun `generated passwords match the XX-NNN-NNN format`() {
        val regex = Regex("[A-Z]{2}-\\d{3}-\\d{3}")
        repeat(20) {
            val pw = RoomPasswordProvider.generateRoomPassword()
            assertTrue(regex.matches(pw), "Generated '$pw' does not match XX-###-###")
        }
    }
}
