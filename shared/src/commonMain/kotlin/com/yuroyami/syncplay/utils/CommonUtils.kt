package com.yuroyami.syncplay.utils

import com.yuroyami.syncplay.watchroom.p
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

    private fun Int.fixDigits() = this.toString().padStart(2, '0')

    var pingUpdateJob: Job? = null
    suspend fun beginPingUpdate() {
        while (true) {
            p.ping.value = if (p.connection?.socket?.isActive == true && p.connection != null) {
                pingIcmp(p.session.serverHost, 32)
            } else null
            delay(1000)
        }
    }
}