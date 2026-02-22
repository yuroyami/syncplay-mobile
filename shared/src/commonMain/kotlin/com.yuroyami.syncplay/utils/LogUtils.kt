package com.yuroyami.syncplay.utils

import SyncplayMobile.shared.BuildConfig
import co.touchlab.kermit.Logger
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

//TODO COMPLETELY REWORK BY WRITING TO A FILE PROGRESSIVELY

private val logBuffer = ArrayDeque<String>(10_000)
private val logLock = SynchronizedObject()
private const val MAX_LOG_SIZE = 10_000

@OptIn(FormatStringsInDatetimeFormats::class)
private val formatter = LocalDateTime.Format {
    byUnicodePattern("yyyy-MM-dd HH:mm:ss.SSS")
}

fun loggy(s: Any?) {
    val string = if (s is Exception) {
        s.stackTraceToString()
    } else {
        s.toString()
    }

    if (BuildConfig.DEBUG) {
        Logger.e(string)
    }

    synchronized(logLock) {
        string.lines().forEach { line ->
            if (logBuffer.size >= MAX_LOG_SIZE) {
                logBuffer.removeFirst()
            }
            val now = Clock.System.now()
            val localDateTime = now.toLocalDateTime(TimeZone.currentSystemDefault())
            val timestamp = localDateTime.format(formatter)
            logBuffer.add("$timestamp | $line")
        }
    }
}

val logFile: ByteArray
    get() = synchronized(logLock) {
        logBuffer.joinToString("\n")
    }.encodeToByteArray()


fun clearLogs() {
    synchronized(logLock) {
        logBuffer.clear()
    }
}