package com.yuroyami.syncplay.utils

import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier

object CommonUtils {


    /** prints the string to the platform-specific logcat output */

    var nap = false

    fun loggy(string: String) {
        if (!nap) {
            Napier.base(DebugAntilog())
            nap = true
        }
        Napier.e { string }
    }
}