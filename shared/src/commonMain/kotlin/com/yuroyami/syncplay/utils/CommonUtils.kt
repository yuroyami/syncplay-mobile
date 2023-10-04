package com.yuroyami.syncplay.utils

import com.yuroyami.syncplay.watchroom.p
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
        return if (this < 10) "0${this}" else "$this"
    }

    var pingUpdateJob: Job? = null
    suspend fun beginPingUpdate() {
        while (true) {
            p.ping.value = if (p.connection?.socket?.isActive == true) pingIcmp(p.session.serverHost, 32) else null
            delay(1000)
        }
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