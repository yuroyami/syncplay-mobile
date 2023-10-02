package com.yuroyami.syncplay.utils

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

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
