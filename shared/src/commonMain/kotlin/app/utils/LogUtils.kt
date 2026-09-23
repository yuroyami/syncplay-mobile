package app.utils

import SyncplayMobile.shared.KiteBuildConfig

import co.touchlab.kermit.Logger
import io.ktor.client.plugins.logging.Logger as KtorLogger
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
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

/** How many days a log file stays before [cleanupOldLogs] deletes it. */
private const val LOG_RETENTION_DAYS = 7

/** The date and the timestamp of one instant, worked out together. */
private class Stamped(val date: String, val timestamp: String)

/**
 * Formats one instant into its "yyyy-MM-dd" file name and its "yyyy-MM-dd HH:mm:ss" line prefix.
 *
 * Both come from one conversion. Two separate conversions would build two Instants and two
 * LocalDateTimes per log line and read the system time zone twice, for a date that is only the
 * first ten characters of the timestamp.
 */
private fun stamp(millis: Long): Stamped {
    val ldt = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
    val date = "${ldt.year}-${ldt.month.number.pad()}-${ldt.day.pad()}"
    return Stamped(date, "$date ${ldt.hour.pad()}:${ldt.minute.pad()}:${ldt.second.pad()}")
}

/** Formats epoch milliseconds as the "yyyy-MM-dd" date that names a log file. */
private fun formatDate(millis: Long): String = stamp(millis).date

private fun Int.pad() = toString().padStart(2, '0')

/**
 * An entry for the log writer ([logPump]): a line to write, or a request to be told when
 * everything queued before it is on disk.
 */
private sealed interface LogEntry {
    data class Line(val timestamp: String, val date: String, val text: String) : LogEntry
    data class Flush(val done: CompletableDeferred<Unit>) : LogEntry
}

private val logScope = CoroutineScope(SupervisorJob() + ioDispatcher)
private val logQueue = Channel<LogEntry>(Channel.UNLIMITED)

/**
 * The one log writer. It drains the queue on the IO dispatcher and joins the lines that are
 * already waiting into one append per file. It starts on the first use from [loggy] or
 * [flushLogs] and never restarts.
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
                // Losing a log line must never crash the app.
            }
        }
    }
    batch.filterIsInstance<LogEntry.Flush>().forEach { it.done.complete(Unit) }
}

/**
 * Records a message. It is cheap and never blocks: the console print is immediate, and the file
 * write goes to the log writer on the IO dispatcher. Callers include the main thread and the
 * serial protocol consumer, so it must never write the file on the calling thread.
 */
fun loggy(s: Any?) {
    val raw = if (s is Exception) {
        s.stackTraceToString()
    } else {
        s.toString()
    }
    val string = redactSecrets(raw, knownSecrets)

    /* Always print to the console (the Xcode console on iOS, logcat on Android), in release builds
     * too, so runtime errors stay visible. The queued file write keeps the log for the export
     * from settings. */
    Logger.e(string)

    logPump // starts the log writer on first use
    val stamped = stamp(generateTimestampMillis())
    for (line in string.lineSequence()) {
        logQueue.trySend(LogEntry.Line(stamped.timestamp, stamped.date, line))
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
    return withContext(ioDispatcher) { logFile }
}

/**
 * Every log file, concatenated, as bytes. It does not flush the queue first, so prefer
 * [readLogsForExport].
 */
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

/**
 * The epoch-day count of a "yyyy-MM-dd" string. Throws on a string that is not a date, and the
 * caller catches that and skips the file.
 */
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
 * Passes the output of Ktor's [io.ktor.client.plugins.logging.Logger] interface to [loggy].
 *
 * The Logging plugin sends multi-line messages (the request line, headers and body, then the
 * response line, headers and body). Each message gets a `[ktor]` prefix, so a search of the log
 * file can find it. [loggy] splits a message into lines, so only the first line has the prefix.
 */
object KtorLoggyLogger : KtorLogger {
    override fun log(message: String) {
        loggy("[ktor] $message")
    }
}

/**
 * Replaces every occurrence of [secrets] in [text] with `***`.
 *
 * The user can export the log from settings, and a service key reaches the log by many paths.
 * For example, Klipy's key is part of the URL, so it is in every request line that Ktor prints.
 * Masking at the one place that every line passes through keeps working as the code changes.
 *
 * A secret shorter than [MIN_MASKABLE_SECRET] characters is skipped, because masking a very short
 * value would destroy the log.
 */
fun redactSecrets(text: String, secrets: List<String>): String =
    secrets.filter { it.length >= MIN_MASKABLE_SECRET }.fold(text) { acc, secret -> acc.replace(secret, "***") }

/** A secret shorter than this is too common to mask without destroying the log around it. */
private const val MIN_MASKABLE_SECRET = 8

private val knownSecrets: List<String> by lazy {
    listOf(KiteBuildConfig.KLIPY_API_KEY, KiteBuildConfig.OPENSUBTITLES_API_KEY)
}
