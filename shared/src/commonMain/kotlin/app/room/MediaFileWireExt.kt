package app.room

import app.player.models.MediaFile
import app.player.models.MediaFile.Companion.hashed
import app.preferences.Preferences
import app.preferences.value
import app.protocol.wire.FileData

/**
 * Converts a [MediaFile] into the wire [FileData] sent in `Set.file`, applying the
 * user's privacy preferences (`HASH_FILENAME`, `HASH_FILESIZE`):
 * - `"1"` — send raw value
 * - `"2"` — send 12-char SHA hash
 * - anything else — send empty string
 *
 * Lives on the client side because it depends on user [Preferences] and the player's
 * [MediaFile] model — neither belongs in the protocol layer.
 */
fun MediaFile.toFileData(): FileData {
    val nameBehavior = Preferences.HASH_FILENAME.value()
    val sizeBehavior = Preferences.HASH_FILESIZE.value()
    return FileData(
        duration = fileDuration ?: -1.0,
        name = when (nameBehavior) {
            "1" -> fileName
            "2" -> fileName.hashed().take(12)
            else -> ""
        },
        size = when (sizeBehavior) {
            "1" -> fileSize
            "2" -> fileSize.hashed().take(12)
            else -> ""
        }
    )
}
