package com.yuroyami.syncplay.utils

import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object CommonUtils {


    /** Generates the current clock timestamp */
    fun generateClockstamp(): String {
        val c = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time
        return "${c.hour.fixDigits()}:${c.minute.fixDigits()}:${c.second.fixDigits()}"
    }

    private fun Int.fixDigits(): String {
        return if (this < 10) "0${this}" else "${this}"
    }

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