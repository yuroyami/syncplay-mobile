package app.server.model

import app.utils.md5

/**
 * The settings of one built-in server instance. They mirror the arguments of the Syncplay PC
 * server (SyncFactory.__init__ in syncplay/server.py).
 */
data class ServerConfig(
    /** TCP port to listen on. */
    val port: Int = DEFAULT_PORT,

    /** The server password in plain text. It is MD5-hashed for comparison. Empty means none. */
    val password: String = "",

    /** When true, rooms are isolated: users only see their own room. */
    val isolateRooms: Boolean = true,

    /**
     * Turns off the readiness feature. The server advertises it as off and reports every user's
     * readiness as unknown (null).
     */
    val disableReady: Boolean = false,

    /** Disable chat messages. */
    val disableChat: Boolean = false,

    /** Maximum allowed chat message length. */
    val maxChatMessageLength: Int = MAX_CHAT_MESSAGE_LENGTH,

    /** Maximum allowed username length. */
    val maxUsernameLength: Int = MAX_USERNAME_LENGTH,

    /** Salt for generating controlled room password hashes. */
    val salt: String = generateSalt(),

    /** Message of the day shown to connecting clients. */
    val motd: String = "",

    /** Seconds without a State from a client before it is dropped as dead. */
    val protocolTimeoutSeconds: Double = PROTOCOL_TIMEOUT_SECONDS,

    /** Milliseconds a new socket has to send a valid Hello before it is dropped. */
    val handshakeDeadlineMs: Long = HANDSHAKE_DEADLINE_MS,
) {
    /** The MD5-hashed password in hex, or an empty string when no password is set. */
    val hashedPassword: String
        get() = if (password.isNotEmpty()) {
            md5(password).toHexString(HexFormat.Default)
        } else ""

    companion object {
        const val DEFAULT_PORT = 8999
        const val MAX_CHAT_MESSAGE_LENGTH = 150
        const val MAX_USERNAME_LENGTH = 16
        const val MAX_ROOM_NAME_LENGTH = 35
        const val MAX_FILENAME_LENGTH = 250
        const val PROTOCOL_TIMEOUT_SECONDS = 12.5
        const val HANDSHAKE_DEADLINE_MS = 15_000L
        const val SERVER_STATE_INTERVAL_MS = 1000L

        fun generateSalt(): String {
            val chars = ('A'..'Z') + ('a'..'z')
            return (1..10).map { chars.random() }.joinToString("")
        }
    }
}
