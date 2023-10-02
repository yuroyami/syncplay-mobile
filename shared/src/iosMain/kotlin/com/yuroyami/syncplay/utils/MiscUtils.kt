package com.yuroyami.syncplay.utils

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