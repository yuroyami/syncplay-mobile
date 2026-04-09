package app.server.model

import app.utils.md5

/**
 * Configuration for a Syncplay server instance.
 *
 * Mirrors the arguments accepted by the original Syncplay server
 * (syncplay-pc-src-master/syncplay/server.py SyncFactory.__init__).
 */
data class ServerConfig(
    /** TCP port to listen on. */
    val port: Int = DEFAULT_PORT,

    /** Server password (raw, will be MD5-hashed for comparison). Empty = no password. */
    val password: String = "",

    /** When true, rooms are isolated — users only see their own room. */
    val isolateRooms: Boolean = true,

    /** Disable the readiness feature (all users always considered ready). */
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
    val motd: String = ""
) {
    /** Returns the MD5-hashed password, or empty string if no password set. */
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
        const val SERVER_STATE_INTERVAL_MS = 1000L

        fun generateSalt(): String {
            val chars = ('A'..'Z') + ('a'..'z')
            return (1..10).map { chars.random() }.joinToString("")
        }
    }
}
