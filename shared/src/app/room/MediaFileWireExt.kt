package app.room

import app.player.models.MediaFile
import app.preferences.Preferences
import app.preferences.value
import app.protocol.wire.FileData
import app.utils.FileComparison

/**
 * Converts a [MediaFile] into the wire [FileData] sent in `Set.file`. The user's privacy
 * preferences (`HASH_FILENAME`, `HASH_FILESIZE`) pick what is sent:
 * - `"1"`: the raw value.
 * - `"2"`: the first 12 hex characters of a SHA-256 hash, as in Python's `utils.hashFilename`
 *   and `utils.hashFilesize`. The name is URL-decoded and stripped of separators before it is
 *   hashed. A hash of the raw name would never match the hash that PC clients compute.
 * - Anything else: Python's placeholders, `**Hidden filename**` for the name and `0` for the
 *   size. PC's `sameFilename` and `sameFilesize` treat exactly these values as "matches
 *   anything". An empty string would show a permanent file-mismatch warning on every PC peer.
 *
 * This function is on the client side because it depends on user [Preferences] and on the
 * player's [MediaFile] model. Neither belongs in the protocol layer.
 */
fun MediaFile.toFileData(): FileData {
    val nameBehavior = Preferences.HASH_FILENAME.value()
    val sizeBehavior = Preferences.HASH_FILESIZE.value()
    return FileData(
        // PC sends 0 for an unknown duration, so this client does the same.
        duration = fileDuration ?: 0.0,
        name = when (nameBehavior) {
            "1" -> fileName
            "2" -> FileComparison.hashFilename(fileName)
            else -> FileComparison.PRIVACY_HIDDENFILENAME
        },
        size = when (sizeBehavior) {
            "1" -> fileSize.ifBlank { "0" }
            "2" -> FileComparison.hashFilesize(fileSize.ifBlank { "0" })
            else -> "0"
        }
    )
}
