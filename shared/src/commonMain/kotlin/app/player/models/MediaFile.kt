package app.player.models

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import app.i18n.Localization
import app.utils.getFileName
import app.utils.getFileSize
import app.utils.ioDispatcher
import com.eygraber.uri.Uri
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import kotlinx.coroutines.withContext
import syncplaymobile.shared.generated.resources.undefined

/** A media file that the player can load, with what the app knows about it. */
data class MediaFile(
    /** Where the file is: a local file or a remote URL. */
    var location: MediaFileLocation? = null,

    /** The file name with its extension. For a resolved URL, this is the title from the resolver. */
    var fileName: String = "",

    /** The file size in bytes, as text. It is "0" for a URL. */
    var fileSize: String = "",

    /** The duration in seconds, or null while it is unknown. */
    var fileDuration: Double? = null,

    /** The audio, subtitle and video tracks of this file. */
    var tracks: SnapshotStateList<Track> = mutableStateListOf(),
    val chapters: SnapshotStateList<Chapter> = mutableStateListOf(),
) {
    companion object {
        suspend fun PlatformFile.mediaFromFile(): MediaFile {
            val loc = MediaFileLocation.Local(this)
           return withContext(ioDispatcher) {
               MediaFile().apply {
                   location = loc

                   fileName = getFileName(loc.file) ?: loc.file.name
                   fileSize = getFileSize(loc.file)?.toString() ?: "0"
               }
           }
        }

        suspend fun String.mediaFromUrl(): MediaFile {
            val loc = MediaFileLocation.Remote(this)
            return withContext(ioDispatcher) {
                MediaFile().apply {
                    location = loc

                    fileName = Uri.Companion.parseOrNull(loc.url)?.pathSegments?.lastOrNull()?.takeIf { it.isNotBlank() }
                        ?: Localization.strings.undefined
                    fileSize = "0"
                }
            }
        }
    }
}