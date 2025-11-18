package com.yuroyami.syncplay.models

import com.yuroyami.syncplay.utils.sha256

/**************************************************************************************
 * File wrapper class. It encapsulates all information and data we need about a file  *
 **************************************************************************************/

data class MediaFile(

    /** The path Uri of the file **/
    var uri: String? = null,

    /** The URL of the file **/
    var url: String? = null,

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
    }
}