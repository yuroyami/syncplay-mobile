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

/**************************************************************************************
 * File wrapper class. It encapsulates all information and data we need about a file  *
 **************************************************************************************/

data class MediaFile(
    /** The file  **/
    var location: MediaFileLocation? = null,

    /** The name of the file with its extension **/
    var fileName: String = "",

    /** The size of the file in bytes **/
    var fileSize: String = "",

    /** The duration of the file (seconds) **/
    var fileDuration: Double? = null,

    /** the subtitle tracks, audio tracks and chapters for this file **/
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