package com.yuroyami.syncplay.utils

import android.annotation.TargetApi
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

object UIUtils {
    /** A function to control cutout mode (expanding window content beyond notch (front camera) **/
    @TargetApi(Build.VERSION_CODES.P)
    fun ComponentActivity.cutoutMode(enable: Boolean) {
        if (enable) {
            window.attributes?.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        } else {
            window.attributes?.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
        }
    }


    /** Completely revised and working versions for "System UI" manipulators. **/
    @Suppress("DEPRECATION")
    fun ComponentActivity.hideSystemUI(useDeprecated: Boolean) {
        runOnUiThread {
            if (!useDeprecated) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                WindowInsetsControllerCompat(window, window.decorView).let { controller ->
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                val decorView: View = window.decorView
                val uiOptions = decorView.systemUiVisibility
                var newUiOptions = uiOptions
                newUiOptions = newUiOptions or View.SYSTEM_UI_FLAG_LOW_PROFILE
                newUiOptions = newUiOptions or View.SYSTEM_UI_FLAG_FULLSCREEN
                newUiOptions = newUiOptions or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                newUiOptions = newUiOptions or View.SYSTEM_UI_FLAG_IMMERSIVE
                newUiOptions = newUiOptions or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                decorView.systemUiVisibility = newUiOptions
                View.OnSystemUiVisibilityChangeListener { newmode ->
                    if (newmode != newUiOptions) {
                        hideSystemUI(false)
                    }
                }
                window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)

            }
        }
    }
}