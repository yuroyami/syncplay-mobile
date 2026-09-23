package app.server.model

import org.kotlincrypto.hash.sha1.SHA1
import org.kotlincrypto.hash.sha2.SHA256

/**
 * Creates controlled room names and checks their passwords. A controlled room (also called a
 * managed room) carries a hash of its password in its name, and only users who know the password
 * can control its playback. A port of RoomPasswordProvider in the Syncplay PC code
 * (syncplay/utils.py).
 *
 * Controlled room name format: `+roomBaseName:HASH12CHARS`
 * Password format: `XX-###-###` (2 uppercase letters, dash, 3 digits, dash, 3 digits)
 */
object RoomPasswordProvider {

    /* The PC code matches both patterns with re.match (anchored at the start, trailing characters
     * allowed).
     * - The room regex has its own trailing `$`, so PC requires the whole string to match. This
     *   code keeps that with matchEntire() and matches().
     * - The password regex has NO `$` in PC, so PC accepts "AB-123-456junk". This code matches it
     *   with find() and a leading `^`, exactly like re.match. matches() (full anchor) would
     *   wrongly reject input with trailing characters. */
    private val CONTROLLED_ROOM_REGEX = Regex("^\\+(.*?):(\\w{12})$")
    private val PASSWORD_REGEX = Regex("^[A-Z]{2}-\\d{3}-\\d{3}")

    fun isControlledRoom(roomName: String): Boolean {
        return CONTROLLED_ROOM_REGEX.matches(roomName)
    }

    /**
     * Checks if [password] is valid for the controlled room [roomName] using [salt].
     *
     * @throws NotControlledRoomException if [roomName] is not in the controlled room format
     * @throws IllegalArgumentException if the password format is invalid
     * @return true if the password matches
     */
    fun check(roomName: String, password: String, salt: String): Boolean {
        if (password.isEmpty() || PASSWORD_REGEX.find(password) == null) {
            throw IllegalArgumentException("Invalid password format")
        }

        val match = CONTROLLED_ROOM_REGEX.matchEntire(roomName)
            ?: throw NotControlledRoomException()

        val roomHash = match.groupValues[2]
        val computedHash = computeRoomHash(match.groupValues[1], password, salt)
        return roomHash == computedHash
    }

    /**
     * The plain name inside a managed name, or the input when it is not one.
     *
     * A request to manage a room that is already managed is about its base name. Sending the full
     * "+movie:HASH" as the target would create a managed name from a managed name.
     */
    fun baseName(roomName: String): String =
        CONTROLLED_ROOM_REGEX.matchEntire(roomName)?.groupValues?.get(1) ?: roomName

    /**
     * The characters that a managed name adds to its base: the leading `+`, the `:`, and the 12
     * characters of hash. A base name longer than the room limit minus this cannot be managed.
     */
    const val MANAGED_NAME_OVERHEAD = 14

    /** Generates a controlled room name from a base name, a password and a salt. */
    fun getControlledRoomName(roomName: String, password: String, salt: String): String {
        return "+$roomName:${computeRoomHash(roomName, password, salt)}"
    }

    /**
     * Computes the 12-character uppercase hash for a controlled room.
     * Algorithm: SHA1(SHA256(roomName + SHA256(salt)) + SHA256(salt) + password)[:12].uppercase()
     *
     * Matches the Python implementation exactly:
     * ```python
     * salt = hashlib.sha256(salt.encode('utf8')).hexdigest().encode('utf8')
     * provisionalHash = hashlib.sha256((roomName + salt).encode('utf8')).hexdigest().encode('utf8')
     * return hashlib.sha1((provisionalHash + salt + password).encode('utf8')).hexdigest()[:12].upper()
     * ```
     */
    @OptIn(ExperimentalStdlibApi::class)
    private fun computeRoomHash(roomName: String, password: String, salt: String): String {
        val saltHash = SHA256().digest(salt.encodeToByteArray()).toHexString(HexFormat.Default)
        val provisionalHash = SHA256().digest((roomName + saltHash).encodeToByteArray()).toHexString(HexFormat.Default)
        val finalHash = SHA1().digest((provisionalHash + saltHash + password).encodeToByteArray()).toHexString(HexFormat.Default)
        return finalHash.take(12).uppercase()
    }

    /** Generates a random controlled room password in the format XX-###-###. */
    fun generateRoomPassword(): String {
        val letters = ('A'..'Z').toList()
        val part1 = (1..2).map { letters.random() }.joinToString("")
        val part2 = (1..3).map { (0..9).random() }.joinToString("")
        val part3 = (1..3).map { (0..9).random() }.joinToString("")
        return "$part1-$part2-$part3"
    }
}

class NotControlledRoomException : Exception("Room is not a controlled room")
