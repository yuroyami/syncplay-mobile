package app.player

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Where each file was left, so it can be picked up again.
 *
 * Nothing persisted a position, which is the one thing every competing player does. The store is
 * deliberately small and dumb: a bounded list keyed by file name, kept as JSON in a single
 * preference, because a database for a few dozen entries would be more machinery than the
 * feature is worth.
 *
 * The policy lives here too, and it is the part worth being careful about. Offering to resume a
 * file the viewer barely started, or one they finished, is worse than not offering at all.
 */
@Serializable
data class ResumePoint(
    val fileName: String,
    val positionMs: Long,
    val durationMs: Long,
    /** When it was recorded, so the oldest entries fall out first. */
    val recordedAtMs: Long,
)

/** How many files are remembered. Past this the least recently watched falls out. */
const val MAX_RESUME_POINTS = 50

/** Below this, the viewer has effectively not started, so there is nothing to resume. */
const val RESUME_MIN_POSITION_MS = 60_000L

/** Within this of the end, the viewer has effectively finished. */
const val RESUME_END_MARGIN_MS = 90_000L

private val resumeJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** Reads the store. A store that will not parse is an empty store, never an exception. */
fun decodeResumePoints(raw: String): List<ResumePoint> =
    if (raw.isBlank()) emptyList()
    else runCatching { resumeJson.decodeFromString<List<ResumePoint>>(raw) }.getOrDefault(emptyList())

fun encodeResumePoints(points: List<ResumePoint>): String = resumeJson.encodeToString(points)

/**
 * Records where a file was left, replacing any earlier point for the same file and dropping the
 * least recently recorded once the store is full.
 *
 * A position that would never be offered back is not stored at all: there is no reason to
 * remember that someone watched ninety seconds of something, or that they finished it.
 */
fun withResumePoint(existing: List<ResumePoint>, point: ResumePoint): List<ResumePoint> {
    val others = existing.filterNot { it.fileName == point.fileName }
    if (!worthRemembering(point)) return others
    return (others + point)
        .sortedByDescending { it.recordedAtMs }
        .take(MAX_RESUME_POINTS)
}

/** Whether a point is far enough in, and far enough from the end, to ever be offered. */
fun worthRemembering(point: ResumePoint): Boolean {
    if (point.positionMs < RESUME_MIN_POSITION_MS) return false
    if (point.durationMs <= 0L) return true // unknown length: trust the position
    return point.positionMs < point.durationMs - RESUME_END_MARGIN_MS
}

/** The point for this file, if there is one worth offering. */
fun resumePointFor(existing: List<ResumePoint>, fileName: String): ResumePoint? =
    existing.firstOrNull { it.fileName == fileName }?.takeIf { worthRemembering(it) }
