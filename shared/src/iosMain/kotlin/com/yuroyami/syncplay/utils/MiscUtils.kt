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