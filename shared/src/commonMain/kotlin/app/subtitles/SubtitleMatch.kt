package app.subtitles

/** A season and an episode, from a file name such as `Show.S02E11.mkv`. */
data class Episode(val season: Int, val episode: Int)

/** "s02e11" or "season2episode11", in a lower-case name. The room's status line uses it too. */
internal val EPISODE_MARKER = Regex("(?:s|season)(\\d{1,2})(?:e|episode)(\\d{1,2})")

/** The season and episode in [filename], or null when it names none. */
fun episodeOf(filename: String): Episode? =
    EPISODE_MARKER.find(filename.lowercase())?.let { Episode(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }

/**
 * What a search for [filename] sends: the show's title and its episode when the name has one,
 * because the service matches an episode by its numbers, and the cleaned name otherwise.
 */
data class SearchTerms(val query: String, val episode: Episode?)

fun searchTermsFor(filename: String): SearchTerms {
    val cleaned = SubtitleSearch.cleanMediaName(filename)
    val episode = episodeOf(filename) ?: return SearchTerms(cleaned, null)
    val title = EPISODE_MARKER.find(cleaned.lowercase())?.let { cleaned.substring(0, it.range.first).trim() }
    return SearchTerms(title?.ifBlank { null } ?: cleaned, episode)
}

/** The number of bytes that OpenSubtitles hashes at each end of a file. */
const val HASH_CHUNK_BYTES = 65_536

/** The first and the last [HASH_CHUNK_BYTES] of a file, with its size. */
class FileEnds(val size: Long, val head: ByteArray, val tail: ByteArray)

/**
 * OpenSubtitles' file hash: the file size plus every 64-bit little-endian word of the first and the
 * last 64 KB, with overflow, as 16 lower-case hex digits. A match on it is exact where a text match
 * is a guess.
 */
fun openSubtitlesHash(ends: FileEnds): String {
    var hash = ends.size
    for (part in listOf(ends.head, ends.tail)) {
        for (word in 0 until part.size / 8) {
            var value = 0L
            for (byte in 7 downTo 0) value = (value shl 8) or (part[word * 8 + byte].toLong() and 0xFF)
            hash += value
        }
    }
    return hash.toULong().toString(16).padStart(16, '0')
}
