package com.yuroyami.syncplay.utils

import android.content.ContentResolver
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.provider.OpenableColumns
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import com.yuroyami.syncplay.utils.CommonUtils.loggy
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.Locale
import kotlin.math.roundToInt

actual fun getPlatform(): String = "Android"

actual fun changeLanguage(lang: String, context: Any?) {
    val appCompatWay = false
    if (appCompatWay) {
        val localesList: LocaleListCompat = LocaleListCompat.forLanguageTags(lang)
        AppCompatDelegate.setApplicationLocales(localesList)
    } else {
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration()
        config.setLocale(locale)
        val ctx = context as Context
        ctx.resources.updateConfiguration(config, ctx.resources.displayMetrics)
    }
}

actual fun generateTimestampMillis(): Long {
    return System.currentTimeMillis()
}

actual fun timeStamper(seconds: Long): String {
    return if (seconds < 3600) {
        String.format("%02d:%02d", (seconds / 60) % 60, seconds % 60)
    } else {
        String.format("%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60)
    }
}

actual fun getFileName(uri: String, context: Any?): String? {
    val actualuri = uri.toUri()
    return when (actualuri.scheme) {
        ContentResolver.SCHEME_CONTENT -> (context as? Context)?.getContentFileName(actualuri)
        else -> actualuri.path?.let(::File)?.name
    }
}

private fun Context.getContentFileName(uri: Uri): String? = runCatching {
    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        cursor.moveToFirst()
        return@use cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
            .let(cursor::getString)
    }
}.getOrNull()


actual fun pingIcmp(host: String, packet: Int): Int? {
    try {
        val pingprocess = Runtime.getRuntime().exec("/system/bin/ping -c 1 -w 1 -s $packet $host") ?: return null
        val inputStream = BufferedReader(InputStreamReader(pingprocess.inputStream))
        val pingOutput = inputStream.use { it.readText() }

        return if (pingOutput.contains("100% packet loss")) {
            null
        } else {
            val time = ((pingOutput.substringAfter("time=").substringBefore(" ms").trim()
                .toDouble()) / 1000.0)
            time.roundToInt()
        }
    } catch (e: Exception) {
        loggy(e.stackTraceToString())
        return null
    }
}
