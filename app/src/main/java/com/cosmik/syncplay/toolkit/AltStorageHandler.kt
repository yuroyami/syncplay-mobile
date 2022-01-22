package com.cosmik.syncplay.toolkit

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import androidx.annotation.Nullable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

class AltStorageHandler {

    @Nullable
    fun alternativeUriToPath(context: Context, uri: Uri): String? {
        val contentResolver = context.contentResolver
        // Create file path inside app's data dir
        val filePath = "${context.applicationInfo.dataDir}${File.separator}temp_file"
        val file = File(filePath)
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val outputStream: OutputStream = FileOutputStream(file)
            val buf = ByteArray(1024)
            var len: Int
            while (inputStream.read(buf).also { len = it } > 0) outputStream.write(buf, 0, len)
            outputStream.close()
            inputStream.close()
        } catch (ignore: IOException) {
            return null
        }
        return file.absolutePath
    }

    fun getRealSizeFromUri(context: Context, uri: Uri): String? {
        var cursor: Cursor? = null
        return try {
            val proj = arrayOf(MediaStore.Video.Media.SIZE)
            cursor = context.contentResolver.query(uri, proj, null, null, null)
            val columnindex: Int = cursor?.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)!!
            cursor.moveToFirst()
            cursor.getString(columnindex)
        } finally {
            cursor?.close()
        }
    }

}