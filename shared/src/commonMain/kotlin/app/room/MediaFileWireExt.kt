package app.room

import app.player.models.MediaFile
import app.preferences.Preferences
import app.preferences.value
import app.protocol.wire.FileData
import app.utils.FileComparison

/**
 * Converts a [MediaFile] into the wire [FileData] sent in `Set.file`, applying the
 * user's privacy preferences (`HASH_FILENAME`, `HASH_FILESIZE`):
 * - `"1"` — send raw value
 * - `"2"` — send the 12-char SHA-256 hash (python's `utils.hashFilename`/`hashFilesize`,
 *   which strips separators and URL-decodes BEFORE hashing — a raw-string hash would
 *   never match what PC clients compute for the same file)
 * - anything else — send python's sentinels: `**Hidden filename**` for the name and
 *   `0` for the size. PC's `sameFilename`/`sameFilesize` treat exactly these as
 *   "matches anything"; an empty string instead would trip a permanent file-mismatch
 *   warning on every PC peer.
 *
 * Lives on the client side because it depends on user [Preferences] and the player's
 * [MediaFile] model — neither belongs in the protocol layer.
 */
fun MediaFile.toFileData(): FileData {
    val nameBehavior = Preferences.HASH_FILENAME.value()
    val sizeBehavior = Preferences.HASH_FILESIZE.value()
    return FileData(
        // PC sends 0 for an unknown duration; -1 would round to a different value than a
        // peer's 0 and trip their duration-mismatch warning.
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
