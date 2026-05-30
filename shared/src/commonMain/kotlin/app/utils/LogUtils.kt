package app.utils

import co.touchlab.kermit.Logger
import io.ktor.client.plugins.logging.Logger as KtorLogger
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

private val logLock = SynchronizedObject()

/** Max number of days to keep log files before auto-cleanup. */
private const val LOG_RETENTION_DAYS = 7

/** Formats epoch millis into "yyyy-MM-dd HH:mm:ss" style timestamp string */
private fun formatTimestamp(millis: Long): String {
    val instant = Instant.fromEpochMilliseconds(millis)
    val ldt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${ldt.year}-${ldt.month.number.pad()}-${ldt.day.pad()} " +
            "${ldt.hour.pad()}:${ldt.minute.pad()}:${ldt.second.pad()}"
}

/** Formats epoch millis into "yyyy-MM-dd" date string for log file naming */
private fun formatDate(millis: Long): String {
    val instant = Instant.fromEpochMilliseconds(millis)
    val ldt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${ldt.year}-${ldt.month.number.pad()}-${ldt.day.pad()}"
}

private fun Int.pad() = toString().padStart(2, '0')

fun loggy(s: Any?) {
    val string = if (s is Exception) {
        s.stackTraceToString()
    } else {
        s.toString()
    }

    /* Always print to console — gating on BuildConfig.DEBUG meant release builds
     * had zero visibility into runtime errors (Klipy/SubtitleSearch failures had
     * no stacktrace anywhere because of this). On iOS this surfaces in Xcode's
     * console; on Android in logcat. The log file write below preserves logs for
     * after-the-fact export from settings. */
    Logger.e(string)

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

/** Exact epoch-day count for a "yyyy-MM-dd" string, using real calendar arithmetic.
 *  Throws if the string isn't a valid date; the caller catches and skips such files.
 *  (The old version approximated every month as 30 days, drifting by several days
 *   near month/year boundaries and occasionally deleting logs early or late.) */
private fun String.toEpochDays(): Long {
    val parts = split("-")
    if (parts.size != 3) throw IllegalArgumentException("Not a date: $this")
    val y = parts[0].toInt()
    val m = parts[1].toInt()
    val d = parts[2].toInt()
    // .toLong() keeps this compiling whether LocalDate.toEpochDays() returns Int or Long
    // across kotlinx-datetime versions.
    return LocalDate(y, m, d).toEpochDays().toLong()
}

fun clearLogs() {
    try {
        val logDir = getLogDirectoryPath() ?: return
        listFiles(logDir).forEach { fileName ->
            deleteFile("$logDir/$fileName")
        }
    } catch (_: Exception) { }
}

/**
 * Bridges Ktor's [io.ktor.client.plugins.logging.Logger] interface into [loggy].
 *
 * The Logging plugin emits multi-line transcripts (REQUEST line, headers, body,
 * RESPONSE line, more headers, body). We prefix each line with `[ktor]` so it
 * stays grep-able in the log file alongside our app logs.
 */
object KtorLoggyLogger : KtorLogger {
    override fun log(message: String) {
        loggy("[ktor] $message")
    }
}
