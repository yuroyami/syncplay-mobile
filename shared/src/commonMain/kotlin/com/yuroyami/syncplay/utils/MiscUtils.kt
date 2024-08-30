package com.yuroyami.syncplay.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp


/** Retrieving which platform the native code is running on */
enum class PLATFORM { Android, IOS, }
expect fun getPlatform(): PLATFORM

/** logging functionality (Uses println on iOS, and Log.e on Android) */
expect fun loggy(s: String?, checkpoint: Int = 0)

/** Gets the default video playback engine on each platform (mpv on Android, AVPlayer on iOS) */
expect fun getDefaultEngine(): String

/** Generates the system's current Epoch millis */
expect fun generateTimestampMillis(): Long

/** Converts seconds into a readable hh:mm:ss format */
fun timeStamper(seconds: Number): String {
    val secs = seconds.toLong()
    return if (secs < 3600) {
        "${(secs / 60) % 60}:${(secs % 60).toString().padStart(2, '0')}".padStart(5, '0')
    } else {
        "${secs / 3600}:${((secs / 60) % 60).toString().padStart(2, '0')}:${(secs % 60).toString().padStart(2, '0')}".padStart(8, '0')
    }
}

/** Gets the filename based on its URI, needs context on Android */
expect fun getFileName(uri: String): String?

/** Gets the folder name based on its URI, needs context on Android */
expect fun getFolderName(uri: String): String?

/** Pings a host once and retrieves the round trip time (RTT) */
expect suspend fun pingIcmp(host: String, packet: Int): Int?

/** Gets the screen size info */
data class ScreenSizeInfo(val hPX: Int, val wPX: Int, val hDP: Dp, val wDP: Dp)
@Composable
expect fun getScreenSizeInfo(): ScreenSizeInfo

/** Formats a string and replaces placeholders with actual keys */
expect fun String.format(vararg args: String): String

/** Gets the app's current locale lang code */
expect fun getSystemLanguageCode(): String