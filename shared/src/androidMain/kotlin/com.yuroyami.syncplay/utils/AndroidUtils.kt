package com.yuroyami.syncplay.utils

import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import java.util.Locale

/**
 * This is currently used to get file names for shared playlists without leaking the context globally.
 */
lateinit var contextObtainer: () -> Context

@Suppress("DEPRECATION")
fun Context.changeLanguage(lang: String): Context {
    val locale = Locale(lang)
    Locale.setDefault(locale)
    val config = Configuration()
    config.setLocale(locale)
    return createConfigurationContext(config)
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

