package com.yuroyami.syncplay.utils

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.utils.CommonUtils.sha256
import java.util.Locale

/** This is used specifically in the case where common code needs access to some context.
 * This is currently used to get file names for shared playlists without leaking the context globally.
 */
interface ContextObtainer {
    fun obtainAppContext(): Context
}
lateinit var contextObtainer: ContextObtainer

@Suppress("DEPRECATION")
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
        fileNameHashed = sha256(fileName).toHexString(HexFormat.UpperCase)
        fileSizeHashed = sha256(fileSize).toHexString(HexFormat.UpperCase)
    }
}

/** Helps determine the size of a file in bytes, needing only its Uri and a context */
fun getRealSizeFromUri(context: Context, uri: Uri): Long? {
    val df = DocumentFile.fromSingleUri(context, uri) ?: return null
    return df.length()
}

fun ComponentActivity.bindWatchdog() {
    /* TODO
    val watchdog = viewmodel!!.lifecycleWatchdog
    val lifecycleObserver = object: LifecycleEventObserver {
        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            when (event) {
                Lifecycle.Event.ON_CREATE -> watchdog.onCreate()
                Lifecycle.Event.ON_START -> watchdog.onStart()
                Lifecycle.Event.ON_RESUME -> watchdog.onResume()
                Lifecycle.Event.ON_PAUSE -> watchdog.onPause()
                Lifecycle.Event.ON_STOP -> watchdog.onStop()
                else -> {}
            }
        }
    }

    lifecycle.addObserver(lifecycleObserver)

     */
}

