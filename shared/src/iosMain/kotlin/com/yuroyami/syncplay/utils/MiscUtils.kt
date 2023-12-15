package com.yuroyami.syncplay.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import platform.Foundation.NSBundle
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitSecond
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponentsFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSString
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.Foundation.stringWithFormat
import platform.Foundation.timeIntervalSince1970
import kotlin.concurrent.AtomicReference
import kotlin.math.roundToLong

actual fun getPlatform(): String = "iOS"

actual fun loggy(s: String?) = println(s.toString())

actual fun getDefaultEngine(): String = "avplayer"

actual fun changeLanguage(lang: String, context: Any?) {
    val languageBundle = AtomicReference<NSBundle?>(null)

    NSBundle.mainBundle.pathForResource(lang, ofType = "lproj")?.let { path ->
        languageBundle.value = NSBundle.bundleWithPath(path)
    }
}

actual fun generateTimestampMillis(): Long {
    return (NSDate().timeIntervalSince1970 * 1000).roundToLong()
}

actual fun timeStamper(seconds: Long): String {
    val formatter = NSDateComponentsFormatter()
    formatter.allowedUnits = NSCalendarUnitHour and NSCalendarUnitMinute and NSCalendarUnitSecond
    return formatter.stringFromTimeInterval(seconds.toDouble()) ?: "??:??"
}

actual fun getFileName(uri: String, context: Any?): String? {
    return "" //TODO
}

actual fun pingIcmp(host: String, packet: Int): Int? {
    return pingIcmpIOS(host, packet)
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
    NSString.stringWithFormat(this, *arrayOf(keys as NSString))
    /* when (keys.size) {
        0 -> this //NSString.stringWithFormat(this)
        1 -> NSString.stringWithFormat(this, keys[0])
        2 -> NSString.stringWithFormat(this, keys[0], keys[1])
        3 -> NSString.stringWithFormat(this, keys[0], keys[1], keys[2])
        4 -> NSString.stringWithFormat(this, keys[0], keys[1], keys[2], keys[3])
        5 -> NSString.stringWithFormat(this, keys[0], keys[1], keys[2], keys[3], keys[4])
        6 -> NSString.stringWithFormat(this, keys[0], keys[1], keys[2], keys[3], keys[4], keys[5])
        else -> throw IllegalStateException("more than 6 args")
    } */

actual fun getSystemLanguageCode(): String {
    return NSLocale.currentLocale.languageCode
}

//actual fun instantiateProtocol(): SyncplayProtocol = SyncplayProtocolIOS()