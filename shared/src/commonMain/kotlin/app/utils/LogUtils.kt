package app.utils

import co.touchlab.kermit.Logger
import io.ktor.client.plugins.logging.Logger as KtorLogger
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

/**
 * What the pump accepts: a line to write, or a request to be told when everything queued
 * before it has landed on disk.
 */
private sealed interface LogEntry {
    data class Line(val timestamp: String, val date: String, val text: String) : LogEntry
    data class Flush(val done: CompletableDeferred<Unit>) : LogEntry
}

private val logScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private val logQueue = Channel<LogEntry>(Channel.UNLIMITED)

/**
 * One writer drains the queue on the IO dispatcher, batching whatever is already waiting into a
 * single append per file. Started on the first [loggy] call and never restarted.
 */
private val logPump: Job by lazy {
    logScope.launch {
        val batch = mutableListOf<LogEntry>()
        for (first in logQueue) {
            batch.clear()
            batch += first
            while (true) batch += logQueue.tryReceive().getOrNull() ?: break
            writeBatch(batch)
        }
    }
}

private fun writeBatch(batch: List<LogEntry>) {
    val lines = batch.filterIsInstance<LogEntry.Line>()
    if (lines.isNotEmpty()) {
        synchronized(logLock) {
            try {
                val logDir = getLogDirectoryPath() ?: return@synchronized
                for ((date, group) in lines.groupBy { it.date }) {
                    val text = group.joinToString("") { "${it.timestamp} | ${it.text}\n" }
                    appendToFile("$logDir/$date.log", text)
                }
            } catch (_: Exception) {
                // Losing a log line must never take the app with it.
            }
        }
    }
    batch.filterIsInstance<LogEntry.Flush>().forEach { it.done.complete(Unit) }
}

/**
 * Records a line. Cheap and non-blocking: the console print is immediate, the file write is
 * handed to a writer on the IO dispatcher. It used to append to a file, line by line, under a
 * lock, on whatever thread called it, which included the main thread and the serial protocol
 * consumer.
 */
fun loggy(s: Any?) {
    val string = if (s is Exception) {
        s.stackTraceToString()
    } else {
        s.toString()
    }

    /* Always print to console (iOS: Xcode console, Android: logcat), including in release builds
     * so runtime errors stay visible. The queued file write preserves logs for export from settings. */
    Logger.e(string)

    logPump // starts the writer on first use
    val millis = generateTimestampMillis()
    val timestamp = formatTimestamp(millis)
    val date = formatDate(millis)
    for (line in string.lines()) {
        logQueue.trySend(LogEntry.Line(timestamp, date, line))
    }
}

/** Suspends until every line queued so far is on disk. Export calls this before reading. */
suspend fun flushLogs() {
    logPump
    val done = CompletableDeferred<Unit>()
    if (logQueue.trySend(LogEntry.Flush(done)).isSuccess) done.await()
}

/** Every log file, concatenated, with anything still queued written out first. */
suspend fun readLogsForExport(): ByteArray {
    flushLogs()
    return withContext(Dispatchers.IO) { logFile }
}

/** Reads and returns all log file contents as a ByteArray. Prefer [readLogsForExport]. */
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
                // Today counts as day one, so a file from LOG_RETENTION_DAYS ago is the first to go.
                if (todayEpochDays - fileDays >= LOG_RETENTION_DAYS) {
                    deleteFile("$logDir/$fileName")
                }
            } catch (_: Exception) {
                // Skip files that don't match date format
            }
        }
    } catch (_: Exception) { }
}

/** Exact epoch-day count for a "yyyy-MM-dd" string. Throws on non-date strings; callers catch
 *  and skip those files. */
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
