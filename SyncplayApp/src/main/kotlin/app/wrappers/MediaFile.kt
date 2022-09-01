package app.wrappers

import android.content.Context
import android.net.Uri
import app.utils.SyncplayUtils
import app.utils.SyncplayUtils.getFileName
import app.utils.SyncplayUtils.toHex
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import kotlin.math.roundToInt

/**************************************************************************************
 * File wrapper class. It encapsulates all information and data we need about a file  *
 **************************************************************************************/

class MediaFile {

    /** The path Uri of the file **/
    var uri: Uri? = null

    /** The name of the file with its extension **/
    var fileName = ""
    var fileNameHashed = ""

    /** The size of the file in bytes **/
    var fileSize: String = ""
    var fileSizeHashed = ""

    /** The duration of the file in hh:mm:ss format **/
    var fileDuration: Double = 0.0

    /** the subtitle and audio tracks for this file **/
    var audioTracks: MutableList<Track> = mutableListOf()
    var subtitleTracks: MutableList<Track> = mutableListOf()

    /** This refers to any external subtitle file that was loaded **/
    var externalSub: MediaItem.SubtitleConfiguration? = null

    /** MediaInfo chart for this file **/
    var mediainfo: MediaInfo = MediaInfo()

    /** The following functions are used to parse certain information from the file **/

    /** In ExoPlayer, a MediaItem consists of TrackGroups
     * Each TrackGroup can contain one track (format) or more.
     * Basically, ExoPlayer gathers similar tracks (same language, different bitrate, for example)
     * into one track group. There can exist for example 4 track groups, 3 of them of audio or text
     *
     * Anyway, in order to manipulate track selection, you need to know how to retrieve those trackgroups
     * and also, how to retrieve the tracks inside them, and check which one is selected...etc
     *
     * I do all of this here.
     */
    fun analyzeTracks(exoplayer: ExoPlayer) {
        audioTracks.clear()
        subtitleTracks.clear()
        val tracks = exoplayer.currentTracks
        for (group in tracks.groups) {
            val trackGroup = group.mediaTrackGroup
            val trackType = group.type
            if (trackType == C.TRACK_TYPE_AUDIO || trackType == C.TRACK_TYPE_TEXT) {
                for (i in (0 until trackGroup.length)) {
                    val format = trackGroup.getFormat(i)
                    val index = trackGroup.indexOf(format)

                    /** Creating a custom Track instance for every track in a track group **/
                    val track = Track()
                    track.trackGroup = trackGroup
                    track.trackType = trackType
                    track.index = index
                    track.format = format
                    track.selected = group.isTrackSelected(index)

                    if (trackType == C.TRACK_TYPE_TEXT) {
                        subtitleTracks.add(track)
                    } else {
                        audioTracks.add(track)
                    }
                }
            }
        }
    }


    /** This method is responsible for getting the size and filename of our media **/
    fun collectInfo(context: Context) {
        /** Using SyncplayUtils **/
        fileName = context.getFileName(uri!!)!!
        fileSize =
            SyncplayUtils.getRealSizeFromUri(context, uri!!)?.toDouble()?.roundToInt().toString()

        /** Hashing name and size in case they're used **/
        fileNameHashed = SyncplayUtils.sha256(fileName).toHex()
        fileSizeHashed = SyncplayUtils.sha256(fileSize).toHex()
    }
}