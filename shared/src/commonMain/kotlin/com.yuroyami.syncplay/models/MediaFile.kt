package com.yuroyami.syncplay.models

import com.eygraber.uri.Uri
import com.yuroyami.syncplay.utils.getFileName
import com.yuroyami.syncplay.utils.getFileSize
import com.yuroyami.syncplay.utils.sha256
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
    var fileDuration: Double = -1.0,

    /** the subtitle tracks, audio tracks and chapters for this file **/
    var audioTracks: MutableList<Track> = mutableListOf(),
    var subtitleTracks: MutableList<Track> = mutableListOf(),
    val chapters: MutableList<Chapter> = mutableListOf(),

    /** This refers to any external subtitle file that was loaded **/
    var externalSub: Any? = null,

    /** MediaInfo chart for this file **/
    var mediainfo: MediaInfo = MediaInfo()
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

                    fileName = Uri.parseOrNull(loc.url)?.pathSegments?.last() ?: getString(Res.string.undefined)
                    fileSize = "0"
                }
            }
        }
    }
}