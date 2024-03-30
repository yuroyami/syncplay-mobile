package com.yuroyami.syncplay.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import cocoapods.SPLPing.SPLPing
import cocoapods.SPLPing.SPLPingConfiguration
import com.yuroyami.syncplay.player.BasePlayer
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.cstr
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import platform.Foundation.NSDate
import platform.Foundation.NSLocale
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.Foundation.lastPathComponent
import platform.Foundation.timeIntervalSince1970
import kotlin.math.roundToInt
import kotlin.math.roundToLong

actual fun getPlatform(): PLATFORM = PLATFORM.IOS

actual fun loggy(s: String?, checkpoint: Int) = println(s.toString())

actual fun getDefaultEngine(): String = BasePlayer.ENGINE.IOS_VLC.name

actual fun generateTimestampMillis(): Long {
    return (NSDate().timeIntervalSince1970 * 1000).roundToLong()
}

actual fun timeStamper(seconds: Long): String {
    return if (seconds < 3600) {
        "${(seconds / 60) % 60}:${(seconds % 60).toString().padStart(2, '0')}".padStart(5, '0')
    } else {
        "${seconds / 3600}:${((seconds / 60) % 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}".padStart(8, '0')
    }
}

actual fun getFileName(uri: String): String? {
    return NSURL.fileURLWithPath(uri).lastPathComponent
}

actual suspend fun pingIcmp(host: String, packet: Int): Int? {
    val future = CompletableDeferred<Int>()
    SPLPing.pingOnce(
        host = host,
        configuration = SPLPingConfiguration(
            pingInterval = 1000.0, timeoutInterval = 1000.0, timeToLive = 1000L, payloadSize = packet.toULong()
        )
    ) {
        it?.let { response ->
            future.complete(
                (response.duration * 1000.0).roundToInt()
            )
        }
    }
    return withTimeoutOrNull(1000) { future.await() }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun getScreenSizeInfo(): ScreenSizeInfo {
    val density = LocalDensity.current
    val config = LocalWindowInfo.current.containerSize


    return remember(density, config) {
        ScreenSizeInfo(
            hPX = config.height,
            wPX = config.width,
            hDP = with(density) { config.height.toDp() },
            wDP = with(density) { config.width.toDp() }
        )
    }
}

@OptIn(BetaInteropApi::class)
actual fun String.format(vararg args: String): String {
    // This ugly work around is because varargs can't be passed to Objective-C...
    // NSString format works with NSObjects via %@, we should change standard format to %@
    //val objcFormat = this@format.replace(Regex("%((?:\\.|\\d|\\$)*)[abcdefs]"), "%$1@")
    val f = this@format //.replace("%[\\d|.]*[sdf]|%".toRegex(), "@")
    @Suppress("MagicNumber")
    return when (args.size) {
        0 -> this //NSString.stringWithFormat(objcFormat)
        1 -> NSString.create(f, locale = NSLocale.currentLocale, args[0].cstr).toString()
        2 -> NSString.create(f, locale = NSLocale.currentLocale, args[0].cstr, args[1].cstr).toString()
        3 -> NSString.create(f,locale = NSLocale.currentLocale, args[0].cstr, args[1].cstr, args[2].cstr).toString()
        4 -> NSString.create(f, locale = null, args[0].cstr, args[1].cstr, args[2].cstr, args[3].cstr).toString()
        else -> NSString.create(f, locale = null, args[0].cstr, args[1].cstr, args[2].cstr, args[3].cstr, args[4].cstr).toString()
    }
}


actual fun getSystemLanguageCode(): String {
    return NSLocale.currentLocale.languageCode
}