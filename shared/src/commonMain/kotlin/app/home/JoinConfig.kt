package app.home

import app.preferences.Preferences
import app.preferences.set
import app.preferences.value
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds
import app.protocol.OFFICIAL_SERVER_NAME

/**
 * The details needed to join a Syncplay room: the server, the room, the username and the
 * passwords. A room is the group of people who watch together. The app saves the last join
 * details, and a launcher shortcut or an invite link carries them too.
 *
 * @property user Username to join with (random by default)
 * @property room Room name to join (random by default)
 * @property ip Server host name or IP address (default: the official Syncplay server)
 * @property port Server port (default: 8997)
 * @property pw Server password, empty when the server needs none
 * @property operatorPassword Operator password of a managed room (a room where only its operators
 *   control playback), when one was pasted with the room name
 */
@Serializable
data class JoinConfig(
    val user: String = "user" + (0..9999).random().toString(),
    val room: String = "room" + (0..9999).random().toString(),
    val ip: String = OFFICIAL_SERVER_NAME,
    val port: Int = 8997,
    val pw: String = "",
    val operatorPassword: String = "",
) {
    companion object {
        /**
         * Reads the last saved join details, with a 250 ms timeout.
         *
         * Unlike [savedConfigNow], a saved value that fails to decode throws here.
         *
         * @return The saved [JoinConfig], or a new one with random names when none is saved or
         *   the read times out
         */
        suspend fun savedConfig(): JoinConfig = withTimeoutOrNull(250.milliseconds) {
            Preferences.JOIN_CONFIG.value()?.let { Json.decodeFromString<JoinConfig>(it) }
        } ?: JoinConfig()

        /**
         * The same read without suspending: the preference store is an in-memory snapshot, so
         * nothing waits. A saved value that fails to decode gives a new [JoinConfig].
         */
        fun savedConfigNow(): JoinConfig =
            runCatching { Preferences.JOIN_CONFIG.value()?.let { Json.decodeFromString<JoinConfig>(it) } }.getOrNull()
                ?: JoinConfig()
    }

    /**
     * Saves these join details when the [Preferences.REMEMBER_INFO] setting is on, so the join
     * form shows them next time. Does nothing when the setting is off.
     */
    suspend fun save() {
        val saveInfo = Preferences.REMEMBER_INFO.value()

        if (saveInfo) {
            Preferences.JOIN_CONFIG.set(Json.encodeToString(this))
        }
    }
}

/** The port in [text], or null when it is not a whole number from 1 to 65535. */
fun parsePort(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 1..65535 }