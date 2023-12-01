package com.yuroyami.syncplay.utils

import android.content.ContentResolver
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.provider.OpenableColumns
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import com.yuroyami.syncplay.utils.CommonUtils.loggy
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.Locale
import kotlin.math.roundToInt

actual fun getPlatform(): String = "Android"

var defaultEngineAndroid = "exo"

actual fun getDefaultEngine(): String = defaultEngineAndroid

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

actual fun generateTimestampMillis() = System.currentTimeMillis()

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
            pingOutput.substringAfter("time=").substringBefore(" ms").trim()
                .toDouble().roundToInt()
        }
    } catch (e: Exception) {
        loggy(e.stackTraceToString())
        return null
    }
}

@Composable
actual fun getScreenSizeInfo(): ScreenSizeInfo {
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val hDp = config.screenHeightDp.dp
    val wDp = config.screenWidthDp.dp

    return remember(density, config) {
        ScreenSizeInfo(
            hPX = with(density) { hDp.roundToPx() },
            wPX = with(density) { wDp.roundToPx() },
            hDP = hDp,
            wDP = wDp
        )
    }
}
