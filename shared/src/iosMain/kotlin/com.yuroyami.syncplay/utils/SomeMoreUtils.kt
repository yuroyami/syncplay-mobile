package com.yuroyami.syncplay.utils

import com.yuroyami.syncplay.managers.NetworkManager
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.utils.CommonUtils.sha256
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber

fun collectInfoLocaliOS(media: MediaFile) {
    media.apply {
        /** Using MiscUtils **/
        fileName = getFileName(uri!!).toString()
        fileSize = getFileSize(uri!!).toString()

        /** Hashing name and size in case they're used **/
        fileNameHashed = sha256(fileName).toHexString(HexFormat.UpperCase)
        fileSizeHashed = sha256(fileSize).toHexString(HexFormat.UpperCase)
    }
}

fun getFileSize(s: String): Long {
    val fileAttributes = NSFileManager.defaultManager.attributesOfItemAtPath(s, null)
    val fileSize = fileAttributes?.get(NSFileSize) as? NSNumber
    return fileSize?.longValue ?: 0
}

var instantiateSwiftNioNetworkManager: (() -> NetworkManager)? = null

