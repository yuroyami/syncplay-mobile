package app.player

import androidx.compose.ui.graphics.ImageBitmap
import app.player.models.Chapter
import app.player.models.MediaFile

/**
 * Still frames for the chapter list and the seek bar bubble: one frame a little after each chapter
 * start, read from the file itself. So the chapters of every engine can show one. Null where the
 * app cannot decode video outside the engine: the web, and the Android build without KitePlayer.
 */
interface ChapterStills {
    /**
     * A frame of [chapter] in [media], or null when none can be taken. [chapterEndMs] is where the
     * chapter ends, when that is known. The frames of the current file stay cached.
     */
    suspend fun still(media: MediaFile, chapter: Chapter, chapterEndMs: Long?): ImageBitmap?
}

/** The platform's chapter stills, or null where there are none. */
expect val chapterStills: ChapterStills?

/**
 * Where a chapter's still comes from: three seconds in, past the fade that often opens a chapter,
 * but never past the middle of a short chapter.
 */
internal fun stillPositionMs(startMs: Long, endMs: Long?): Long {
    val room = endMs?.let { (it - startMs) / 2 } ?: STILL_OFFSET_MS
    return startMs + room.coerceIn(0L, STILL_OFFSET_MS)
}

private const val STILL_OFFSET_MS = 3_000L
