package com.yuroyami.syncplay.models

import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds

/**
 * Configuration data for joining a Syncplay room.
 *
 * Contains all necessary connection information including server details, credentials,
 * and user identity. Can be serialized and persisted to remember the last connection
 * or saved as shortcuts for quick access.
 *
 * @property user Username to connect with (randomized by default)
 * @property room Room name to join (randomized by default)
 * @property ip Server hostname or IP address (default: official Syncplay server)
 * @property port Server port (default: 8997)
 * @property pw Room password, if required (empty by default)
 */
@Serializable
data class JoinConfig(
    val user: String = "user" + (0..9999).random().toString(),
    val room: String = "room" + (0..9999).random().toString(),
    val ip: String = "syncplay.pl",
    val port: Int = 8997,
    val pw: String = "",
) {
    companion object {
        /**
         * Retrieves the last saved join configuration from storage.
         *
         * Attempts to load the previously saved configuration with a 250ms timeout.
         * If no saved configuration exists or loading times out, returns a new
         * JoinConfig with randomized default values.
         *
         * @return The saved JoinConfig if available, otherwise a new default instance
         */
        suspend fun savedConfig(): JoinConfig = withTimeoutOrNull(250.milliseconds) {
            Json.decodeFromString<JoinConfig>(
                pref<String?>(DataStoreKeys.MISC_JOIN_CONFIG, null)
                    ?: return@withTimeoutOrNull JoinConfig()
            )
        } ?: JoinConfig()
    }

    /**
     * Persists this join configuration to storage if the user has enabled info remembering.
     *
     * Checks the user's preference for remembering connection info. If enabled,
     * serializes and saves this configuration for future use. Otherwise, does nothing.
     *
     * This allows quick reconnection to the same room with the same credentials.
     */
    suspend fun save() {
        val saveInfo = pref(DataStoreKeys.PREF_REMEMBER_INFO, true)

        if (saveInfo) {
            writePref(DataStoreKeys.MISC_JOIN_CONFIG, Json.encodeToString(this))
        }
    }
}