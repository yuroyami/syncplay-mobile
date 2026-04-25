package app.protocol.server

import kotlinx.serialization.Serializable

/**
 * File metadata exchanged in `Set.file` messages and inside [Set.UserSetData] broadcasts.
 *
 * `size` is a string per the Syncplay wire protocol — the client may send it raw,
 * hashed, or as an empty string depending on privacy settings.
 */
@Serializable
data class FileData(
    /** File name (with extension), possibly hashed or empty per privacy preference. */
    val name: String? = null,

    /** File duration in seconds. */
    val duration: Double? = null,

    /** File size in bytes as a string, possibly hashed or empty per privacy preference. */
    val size: String? = null
)
