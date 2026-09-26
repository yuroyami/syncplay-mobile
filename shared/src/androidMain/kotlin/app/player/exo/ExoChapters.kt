package app.player.exo

import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import app.player.models.Chapter
import androidx.media3.extractor.metadata.Chapter as MediaChapter

/**
 * The chapters that Media3 read from the file. The Matroska and MP4 extractors put them into the
 * formats of the tracks, and several tracks can carry the same list. A hidden chapter stays out,
 * and a chapter without a title gets a number, as on the other engines.
 */
@OptIn(UnstableApi::class)
internal fun chaptersOf(formats: List<Format>): List<Chapter> =
    formats.asSequence()
        .mapNotNull { it.metadata }
        .flatMap { metadata -> (0 until metadata.length()).asSequence().map(metadata::get) }
        .filterIsInstance<MediaChapter>()
        .filterNot { it.isHidden }
        .distinctBy { it.startTimeMs to it.title?.value }
        .sortedBy { it.startTimeMs }
        .mapIndexed { index, chapter ->
            Chapter(
                index = index,
                name = chapter.title?.value?.takeIf { it.isNotBlank() } ?: "Chapter ${index + 1}",
                timeOffsetMillis = chapter.startTimeMs,
            )
        }
        .toList()
