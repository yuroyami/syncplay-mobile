package app.player.models

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import app.utils.getFileName
import app.utils.getFileSize
import app.utils.sha256
import com.eygraber.uri.Uri
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import syncplaymobile.shared.generated.resources.Res
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
        fun String.hashed() = sha256(this).toHexString(HexFormat.UpperCase)

        suspend fun PlatformFile.mediaFromFile(): MediaFile {
            val loc = MediaFileLocation.Local(this)
           return withContext(Dispatchers.IO) {
               MediaFile().apply {
                   location = loc

                   fileName = getFileName(loc.file)!!
                   fileSize = getFileSize(loc.file)?.toString() ?: "0"
               }
           }
        }

        suspend fun String.mediaFromUrl(): MediaFile {
            val loc = MediaFileLocation.Remote(this)
            return withContext(Dispatchers.IO) {
                MediaFile().apply {
                    location = loc

                    fileName = Uri.Companion.parseOrNull(loc.url)?.pathSegments?.last() ?: getString(Res.string.undefined)
                    fileSize = "0"
                }
            }
        }
    }
}