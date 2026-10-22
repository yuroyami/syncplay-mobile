package app.player

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Where the viewer left one file, so that playback can resume there later.
 *
 * The store is small on purpose: a bounded list keyed by file name, kept as JSON in one
 * preference. A database for a few dozen entries is not worth the extra code.
 *
 * The policy for which points to offer lives here too, and it needs care. Offering to resume a
 * file that the viewer barely started, or one that they finished, is worse than no offer at all.
 */
@Serializable
data class ResumePoint(
    val fileName: String,
    val positionMs: Long,
    val durationMs: Long,
    /** When the point was recorded, so that the oldest entries drop out first. */
    val recordedAtMs: Long,
)

/** How many files the store keeps. Past this count, the least recently recorded file drops out. */
const val MAX_RESUME_POINTS = 50

/** Below this position, the viewer has barely started, so there is nothing to resume. */
const val RESUME_MIN_POSITION_MS = 60_000L

/** Within this distance of the end, the file counts as finished. */
const val RESUME_END_MARGIN_MS = 90_000L

private val resumeJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** Reads the store. A store that will not parse is an empty store, never an exception. */
fun decodeResumePoints(raw: String): List<ResumePoint> =
    if (raw.isBlank()) emptyList()
    else runCatching { resumeJson.decodeFromString<List<ResumePoint>>(raw) }.getOrDefault(emptyList())

fun encodeResumePoints(points: List<ResumePoint>): String = resumeJson.encodeToString(points)

/**
 * Records where a file was left. The new point replaces any earlier point for the same file, and
 * the least recently recorded point drops out once the store is full.
 *
 * A position that would never be offered back is not stored, and the earlier point for that file
 * goes too. There is no reason to remember that someone watched less than a minute of a file, or
 * that they finished it.
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
