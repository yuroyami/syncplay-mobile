package com.yuroyami.syncplay.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import cocoapods.SPLPing.SPLPing
import cocoapods.SPLPing.SPLPingConfiguration
import com.yuroyami.syncplay.player.BasePlayer
import kotlinx.cinterop.cstr
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import platform.Foundation.NSDate
import platform.Foundation.NSLocale
import platform.Foundation.NSString
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.Foundation.stringWithFormat
import platform.Foundation.timeIntervalSince1970
import kotlin.math.roundToInt
import kotlin.math.roundToLong

actual fun getPlatform(): PLATFORM = PLATFORM.IOS

actual fun loggy(s: String?, checkpoint: Int) = println(s.toString())

actual fun getDefaultEngine(): String = BasePlayer.ENGINE.IOS_VLC.name

//actual fun changeLanguage(lang: String) {
//    val languageBundle = AtomicReference<NSBundle?>(null)
//
//    NSBundle.mainBundle.pathForResource(lang, ofType = "lproj")?.let { path ->
//        languageBundle.value = NSBundle.bundleWithPath(path)
//    }
//}

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

actual fun getFileName(uri: String, context: Any?): String? {
    return "" //TODO
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

actual fun String.format(vararg keys: String) =
    // This ugly work around is because varargs can't be passed to Objective-C...
    when (keys.size) {
        0 -> this
        1 -> NSString.stringWithFormat(this, keys[0].cstr)
        2 -> NSString.stringWithFormat(this, keys[0].cstr, keys[1].cstr)
        3 -> NSString.stringWithFormat(this, keys[0].cstr, keys[1].cstr, keys[2].cstr)
        4 -> NSString.stringWithFormat(this, keys[0].cstr, keys[1].cstr, keys[2].cstr, keys[3].cstr)
        5 -> NSString.stringWithFormat(this, keys[0].cstr, keys[1].cstr, keys[2].cstr, keys[3].cstr, keys[4].cstr)
        6 -> NSString.stringWithFormat(this, keys[0].cstr, keys[1].cstr, keys[2].cstr, keys[3].cstr, keys[4].cstr, keys[5].cstr)
        else -> throw IllegalStateException("more than 6 args")
    }

actual fun getSystemLanguageCode(): String {
    return NSLocale.currentLocale.languageCode
}