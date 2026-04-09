package app.server.model

import org.kotlincrypto.hash.sha1.SHA1
import org.kotlincrypto.hash.sha2.SHA256

/**
 * Handles controlled room name generation and password verification.
 * Port of Python's RoomPasswordProvider (syncplay-pc-src-master/syncplay/utils.py).
 *
 * Controlled room name format: `+roomBaseName:HASH12CHARS`
 * Password format: `XX-###-###` (2 uppercase letters, dash, 3 digits, dash, 3 digits)
 */
object RoomPasswordProvider {

    private val CONTROLLED_ROOM_REGEX = Regex("^\\+(.*?):(\\w{12})$")
    private val PASSWORD_REGEX = Regex("[A-Z]{2}-\\d{3}-\\d{3}")

    fun isControlledRoom(roomName: String): Boolean {
        return CONTROLLED_ROOM_REGEX.matches(roomName)
    }

    /**
     * Checks if [password] is valid for the controlled room [roomName] using [salt].
     *
     * @throws NotControlledRoomException if roomName is not a controlled room format
     * @throws IllegalArgumentException if password format is invalid
     * @return true if the password matches
     */
    fun check(roomName: String, password: String, salt: String): Boolean {
        if (password.isEmpty() || !PASSWORD_REGEX.matches(password)) {
            throw IllegalArgumentException("Invalid password format")
        }

        val match = CONTROLLED_ROOM_REGEX.matchEntire(roomName)
            ?: throw NotControlledRoomException()

        val roomHash = match.groupValues[2]
        val computedHash = computeRoomHash(match.groupValues[1], password, salt)
        return roomHash == computedHash
    }

    /**
     * Generates a controlled room name from a base name, password, and salt.
     */
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

    /**
     * Generates a random controlled room password in format XX-###-###.
     */
    fun generateRoomPassword(): String {
        val letters = ('A'..'Z').toList()
        val part1 = (1..2).map { letters.random() }.joinToString("")
        val part2 = (1..3).map { (0..9).random() }.joinToString("")
        val part3 = (1..3).map { (0..9).random() }.joinToString("")
        return "$part1-$part2-$part3"
    }
}

class NotControlledRoomException : Exception("Room is not a controlled room")
