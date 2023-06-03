package app.wrappers

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import app.player.mpv.MPVView
import app.utils.MiscUtils
import app.utils.MiscUtils.getFileName
import app.utils.MiscUtils.toHex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.net.MalformedURLException
import java.net.URL
import kotlin.math.roundToInt

/**************************************************************************************
 * File wrapper class. It encapsulates all information and data we need about a file  *
 **************************************************************************************/

class MediaFile {

    /** The path Uri of the file **/
    var uri: Uri? = null

    /** The URL of the file **/
    var url: String? = null

    /** The name of the file with its extension **/
    var fileName = ""
    var fileNameHashed = ""

    /** The size of the file in bytes **/
    var fileSize: String = ""
    var fileSizeHashed = ""

    /** The duration of the file (ms) **/
    var fileDuration: Double = -1.0

    /** the subtitle and audio tracks for this file **/
    var audioTracks: MutableList<Track> = mutableListOf()
    var subtitleTracks: MutableList<Track> = mutableListOf()

    /** This refers to any external subtitle file that was loaded **/
    var externalSub: MediaItem.SubtitleConfiguration? = null

    /** MediaInfo chart for this file **/
    var mediainfo: MediaInfo = MediaInfo()

    /** This method is responsible for getting the size and filename of our media file **/
    fun collectInfo(context: Context) {
        /** Using MiscUtils **/
        fileName = context.getFileName(uri!!)!!
        fileSize = MiscUtils.getRealSizeFromUri(context, uri!!)?.toDouble()?.toLong().toString()

        /** Hashing name and size in case they're used **/
        fileNameHashed = MiscUtils.sha256(fileName).toHex()
        fileSizeHashed = MiscUtils.sha256(fileSize).toHex()
    }

    /** This method is responsible for getting the size and filename of our media URL source **/
    fun collectInfoURL() {
        try {
            /** Using SyncplayUtils **/
            fileName = URL(url).path.substringAfterLast("/")
            fileSize = 0L.toString()
        } catch (e: MalformedURLException) {
            e.printStackTrace()
        }

        /** Hashing name and size in case they're used **/
        fileNameHashed = MiscUtils.sha256(fileName).toHex()
        fileSizeHashed = MiscUtils.sha256(fileSize).toHex()
    }
}