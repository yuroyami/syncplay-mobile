package com.yuroyami.syncplay.utils

import com.yuroyami.syncplay.models.MediaFile

fun collectInfoLocaliOS(media: MediaFile) {
    with (media) {
        /** Using MiscUtils **/
        fileName = getFileName(uri!!)
        fileSize = "000000" //TODO

        /** Hashing name and size in case they're used **/
        fileNameHashed = sha256(fileName).toHex()
        fileSizeHashed = sha256(fileSize).toHex()
    }
}

const val gb = 2L * 1024L * 1024L * 1024L

fun getFileName(s: String): String {
    return "IDK"
}