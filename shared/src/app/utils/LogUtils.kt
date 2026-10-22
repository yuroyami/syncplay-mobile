package app.utils

import SyncplayMobile.shared.KiteBuildConfig

import co.touchlab.kermit.Logger
import io.ktor.client.plugins.logging.Logger as KtorLogger
import kotlinx.atomicfu.atomic
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

/** Lines waiting for the writer. Past this, or past [MAX_PENDING_BYTES], a new line is dropped. */
private const val MAX_PENDING_LINES = 5_000

/** Characters waiting for the writer, across all queued lines. */
private const val MAX_PENDING_BYTES = 1_000_000

/** Entries the writer takes from the queue for one append. */
private const val MAX_BATCH = 500

/** A day's log starts a new part file past this size. */
private const val MAX_FILE_BYTES = 2_000_000L

/** All log files together. Past this, the oldest part files are deleted first. */
private const val MAX_TOTAL_BYTES = 10_000_000L

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

/**
 * Bounded in lines and in characters. A flood of log lines drops the newest ones, and the writer
 * records how many, so a long or noisy session never competes with playback for memory.
 */
private val logQueue = Channel<LogEntry>(MAX_PENDING_LINES)
private val pendingChars = atomic(0)
private val droppedLines = atomic(0)

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
            while (batch.size < MAX_BATCH) batch += logQueue.tryReceive().getOrNull() ?: break
            writeBatch(batch)
        }
    }
}

private fun writeBatch(batch: List<LogEntry>) {
    val lines = batch.filterIsInstance<LogEntry.Line>()
    pendingChars.addAndGet(-lines.sumOf { it.text.length })
    val dropped = droppedLines.getAndSet(0)
    if (lines.isNotEmpty() || dropped > 0) {
        synchronized(logLock) {
            try {
                val logDir = getLogDirectoryPath() ?: return@synchronized
                val now = stamp(generateTimestampMillis())
                val marker = if (dropped > 0) listOf(LogEntry.Line(now.timestamp, now.date, "$dropped log lines dropped: the log queue was full")) else emptyList()
                for ((date, group) in (marker + lines).groupBy { it.date }) {
                    val text = group.joinToString("") { "${it.timestamp} | ${it.text}\n" }
                    appendToFile(logFileFor(logDir, date, text.length), text)
                }
            } catch (_: Exception) {
                // Losing a log line must never crash the app.
            }
        }
    }
    batch.filterIsInstance<LogEntry.Flush>().forEach { it.done.complete(Unit) }
}

/** One log file: a date, and a part number that grows as the day's log passes [MAX_FILE_BYTES]. */
internal data class LogPart(val name: String, val date: String, val part: Int)

/** "2026-09-26.log" is part 1 of that day, "2026-09-26.2.log" part 2. Null for any other name. */
internal fun logPartOf(fileName: String): LogPart? {
    if (!fileName.endsWith(".log")) return null
    val stem = fileName.removeSuffix(".log")
    val date = stem.substringBefore('.')
    val part = if ('.' in stem) stem.substringAfter('.').toIntOrNull() ?: return null else 1
    if (date.length != 10) return null
    return LogPart(fileName, date, part)
}

/** The day's current part file, and a new one when [adding] characters would pass the size cap. */
internal fun logFileFor(
    logDir: String,
    date: String,
    adding: Int,
    maxFileBytes: Long = MAX_FILE_BYTES,
    maxTotalBytes: Long = MAX_TOTAL_BYTES,
): String {
    val latest = listFiles(logDir).mapNotNull(::logPartOf).filter { it.date == date }.maxByOrNull { it.part }
    val current = latest ?: return "$logDir/$date.log"
    if (fileLength("$logDir/${current.name}") + adding <= maxFileBytes) return "$logDir/${current.name}"
    enforceTotalLogSize(logDir, maxTotalBytes)
    return "$logDir/$date.${current.part + 1}.log"
}

/** Deletes the oldest part files until all of them together fit in [MAX_TOTAL_BYTES]. */
internal fun enforceTotalLogSize(logDir: String, maxTotalBytes: Long = MAX_TOTAL_BYTES) {
    val parts = listFiles(logDir).mapNotNull(::logPartOf).sortedWith(compareBy({ it.date }, { it.part }))
    var total = parts.sumOf { fileLength("$logDir/${it.name}") }
    for (part in parts.dropLast(1)) {
        if (total <= maxTotalBytes) break
        total -= fileLength("$logDir/${part.name}")
        deleteFile("$logDir/${part.name}")
    }
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
    // Both at the one place that every line passes through: service keys, then who was there.
    val string = LogRedactor.redact(redactSecrets(raw, knownSecrets))

    /* Always print to the console (the Xcode console on iOS, logcat on Android), in release builds
     * too, so runtime errors stay visible. The queued file write keeps the log for the export
     * from settings. */
    Logger.e(string)

    logPump // starts the log writer on first use
    val stamped = stamp(generateTimestampMillis())
    for (line in string.lineSequence()) {
        val fits = pendingChars.value + line.length <= MAX_PENDING_BYTES &&
            logQueue.trySend(LogEntry.Line(stamped.timestamp, stamped.date, line)).isSuccess
        if (fits) pendingChars.addAndGet(line.length) else droppedLines.incrementAndGet()
    }
}

/** Suspends until every line queued so far is on disk. Export calls this before reading. */
suspend fun flushLogs() {
    logPump
    val done = CompletableDeferred<Unit>()
    if (logQueue.trySend(LogEntry.Flush(done)).isSuccess) done.await()
}

/**
 * The paths of every log file, oldest first, with anything still queued written out first. The
 * export copies them one at a time, so it never holds the whole log in memory.
 */
suspend fun logFilesForExport(): List<String> {
    flushLogs()
    return withContext(ioDispatcher) {
        synchronized(logLock) {
            val logDir = getLogDirectoryPath() ?: return@synchronized emptyList()
            listFiles(logDir).mapNotNull(::logPartOf)
                .sortedWith(compareBy({ it.date }, { it.part }))
                .map { "$logDir/${it.name}" }
        }
    }
}

/** Removes log files older than [LOG_RETENTION_DAYS], then the oldest past [MAX_TOTAL_BYTES]. */
fun cleanupOldLogs() {
    try {
        val logDir = getLogDirectoryPath() ?: return
        val todayEpochDays = formatDate(generateTimestampMillis()).toEpochDays()

        listFiles(logDir).mapNotNull(::logPartOf).forEach { part ->
            try {
                // Today counts as day one, so a file from LOG_RETENTION_DAYS ago is the first to go.
                if (todayEpochDays - part.date.toEpochDays() >= LOG_RETENTION_DAYS) {
                    deleteFile("$logDir/${part.name}")
                }
            } catch (_: Exception) {
                // Skip files that don't match date format
            }
        }
        synchronized(logLock) { enforceTotalLogSize(logDir) }
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
