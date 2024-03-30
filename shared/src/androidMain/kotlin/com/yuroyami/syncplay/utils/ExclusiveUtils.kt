package com.yuroyami.syncplay.utils

import android.content.Context
import android.content.res.Configuration
import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.provider.MediaStore
import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.yuroyami.syncplay.models.MediaFile
import java.io.File
import java.util.Locale

/** This is used specifically in the case where common code needs access to some context.
 * This is currently used to get file names for shared playlists without leaking the context globally.
 */
interface ContextObtainer {
    fun obtainAppContext(): Context
}
lateinit var contextObtainer: ContextObtainer

fun Context.changeLanguage(lang: String): Context {
    val locale = Locale(lang)
    Locale.setDefault(locale)
    val config = Configuration()
    config.setLocale(locale)
    return createConfigurationContext(config)
}

fun collectInfoLocalAndroid(media: MediaFile, context: Context) {
    with(media) {
        /** Using MiscUtils **/
        fileName = getFileName(uri!!)!!
        fileSize = getRealSizeFromUri(context, uri!!.toUri())?.toDouble()?.toLong().toString()

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

@WorkerThread
fun getPathFromURI(context: Context, contentUri: Uri): String {
    var cursor: Cursor? = null
    try {
        val proj = arrayOf(MediaStore.Video.Media.DATA)
        cursor = context.contentResolver.query(contentUri, proj, null, null, null)
        if (cursor == null || cursor.count == 0)
            return ""
        val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
        cursor.moveToFirst()
        return Uri.fromFile(File(cursor.getString(columnIndex))).toString()
    } catch (e: IllegalArgumentException) {
        return ""
    } catch (e: SecurityException) {
        return ""
    } catch (e: SQLiteException) {
        return ""
    } catch (e: NullPointerException) {
        return ""
    } finally {
        if (cursor != null && !cursor.isClosed) cursor.close()
    }
}