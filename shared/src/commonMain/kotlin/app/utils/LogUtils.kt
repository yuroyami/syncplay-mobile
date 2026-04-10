package app.utils

import SyncplayMobile.shared.BuildConfig
import co.touchlab.kermit.Logger
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val logLock = SynchronizedObject()

/** Max number of days to keep log files before auto-cleanup. */
private const val LOG_RETENTION_DAYS = 7

/** Formats epoch millis into "yyyy-MM-dd HH:mm:ss" style timestamp string */
private fun formatTimestamp(millis: Long): String {
    val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(millis)
    val ldt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${ldt.year}-${ldt.monthNumber.pad()}-${ldt.dayOfMonth.pad()} " +
            "${ldt.hour.pad()}:${ldt.minute.pad()}:${ldt.second.pad()}"
}

/** Formats epoch millis into "yyyy-MM-dd" date string for log file naming */
private fun formatDate(millis: Long): String {
    val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(millis)
    val ldt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${ldt.year}-${ldt.monthNumber.pad()}-${ldt.dayOfMonth.pad()}"
}

private fun Int.pad() = toString().padStart(2, '0')

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
        try {
            val logDir = getLogDirectoryPath() ?: return@synchronized
            val millis = generateTimestampMillis()
            val timestamp = formatTimestamp(millis)
            val dateString = formatDate(millis)
            val logFilePath = "$logDir/$dateString.log"

            string.lines().forEach { line ->
                appendToFile(logFilePath, "$timestamp | $line\n")
            }
        } catch (_: Exception) {
            // Don't crash if logging fails
        }
    }
}

/** Reads and returns all log file contents as a ByteArray (for export). */
val logFile: ByteArray
    get() = synchronized(logLock) {
        try {
            val logDir = getLogDirectoryPath() ?: return@synchronized ""
            val files = listFiles(logDir).sorted()
            files.joinToString("\n") { fileName ->
                "=== $fileName ===\n${readFile("$logDir/$fileName")}"
            }
        } catch (_: Exception) {
            ""
        }
    }.encodeToByteArray()

/** Removes log files older than [LOG_RETENTION_DAYS]. */
fun cleanupOldLogs() {
    try {
        val logDir = getLogDirectoryPath() ?: return
        val todayDate = formatDate(generateTimestampMillis())
        val todayEpochDays = todayDate.toEpochDays()

        listFiles(logDir).forEach { fileName ->
            try {
                val datePart = fileName.removeSuffix(".log")
                val fileDays = datePart.toEpochDays()
                if (todayEpochDays - fileDays > LOG_RETENTION_DAYS) {
                    deleteFile("$logDir/$fileName")
                }
            } catch (_: Exception) {
                // Skip files that don't match date format
            }
        }
    } catch (_: Exception) { }
}

/** Simple epoch day calculation from "yyyy-MM-dd" string */
private fun String.toEpochDays(): Int {
    val parts = split("-")
    if (parts.size != 3) return 0
    val y = parts[0].toIntOrNull() ?: return 0
    val m = parts[1].toIntOrNull() ?: return 0
    val d = parts[2].toIntOrNull() ?: return 0
    // Approximate epoch days (good enough for relative comparisons within 7 days)
    return y * 365 + y / 4 - y / 100 + y / 400 + (m * 30) + d
}

fun clearLogs() {
    try {
        val logDir = getLogDirectoryPath() ?: return
        listFiles(logDir).forEach { fileName ->
            deleteFile("$logDir/$fileName")
        }
    } catch (_: Exception) { }
}
