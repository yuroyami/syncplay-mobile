package app.protocol.wire

import kotlinx.serialization.Serializable

/**
 * File metadata exchanged in `Set.file` (client→server) and inside [UserSetData] broadcasts
 * (server→client).
 *
 * `size` is a string per the Syncplay wire protocol — the client may send it raw, hashed,
 * or as an empty string depending on privacy settings.
 */
@Serializable
data class FileData(
    val name: String? = null,
    val duration: Double? = null,
    val size: String? = null
)
