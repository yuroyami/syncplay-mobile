package com.yuroyami.syncplay.utils

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.yuroyami.syncplay.models.MediaFile
import java.net.MalformedURLException
import java.net.URL

fun collectInfoLocalAndroid(media: MediaFile, context: Context) {
    with (media) {
        /** Using MiscUtils **/
        fileName = getFileName(uri!!, context)!!
        fileSize = getRealSizeFromUri(context, uri!!.toUri())?.toDouble()?.toLong().toString()

        /** Hashing name and size in case they're used **/
        fileNameHashed = sha256(fileName).toHex()
        fileSizeHashed = sha256(fileSize).toHex()
    }
}

fun collectInfoURLAndroid(media: MediaFile) {
    with (media) {
        try {
            /** Using SyncplayUtils **/
            fileName = URL(url).path.substringAfterLast("/")
            fileSize = 0L.toString()
        } catch (e: MalformedURLException) {
            e.printStackTrace()
        }

        /** Hashing name and size in case they're used **/
        fileNameHashed = sha256(fileName).toHex()
        fileSizeHashed = sha256(fileSize).toHex()
    }
}

val gb = 2L * 1024L * 1024L * 1024L

/** Helps determine the size of a file in bytes, needing only its Uri and a context */
fun getRealSizeFromUri(context: Context, uri: Uri): Long? {
    val df = DocumentFile.fromSingleUri(context, uri) ?: return null
    return df.length()
}