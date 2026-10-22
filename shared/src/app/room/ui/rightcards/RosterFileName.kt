package app.room.ui.rightcards

import app.utils.mediaExs

/**
 * Returns the title that the compact roster (the list of users in the room) shows for a file
 * name. A room is the group of people watching together. The function drops a media extension
 * and the release group tags in brackets at the start. Only the display changes: file comparison
 * and the detailed row still use the real file name. Shortening to a width belongs to the text
 * layout, so a wider panel can show more of the title.
 */
internal fun compactRosterFileName(filename: String): String {
    var title = filename.trim()
    val extensionAt = title.lastIndexOf('.')
    if (extensionAt > 0) {
        val extension = title.substring(extensionAt + 1).lowercase()
        if (extension in mediaExs) {
            title = title.substring(0, extensionAt).trimEnd()
        }
    }

    // Release group tags can be adjacent, repeated or nested. Keep unmatched brackets, and keep a
    // title made only of bracket tags, so the only useful name is not removed.
    while (title.startsWith('[')) {
        var depth = 0
        var closingAt = -1
        for (index in title.indices) {
            when (title[index]) {
                '[' -> depth++
                ']' -> depth--
            }
            if (depth == 0) {
                closingAt = index
                break
            }
        }
        if (closingAt < 0) break
        val remainder = title.substring(closingAt + 1).trimStart()
        if (remainder.isBlank()) break
        title = remainder
    }
    return title
}

/**
 * Shortens [title] to its beginning and its ending, joined by an ellipsis, so that it fits a
 * measured text width. [fits] must measure the whole candidate with the font that draws it,
 * because a character count is not a width. In an odd split, the extra character goes to the
 * ending, where episode numbers are.
 */
internal fun abbreviateRosterFileName(title: String, fits: (String) -> Boolean): String {
    if (fits(title)) return title
    val ellipsis = "…"
    if (!fits(ellipsis)) return ""

    // UTF-16 offsets at code-point boundaries: a title may start or end with an emoji.
    val boundaries = mutableListOf(0)
    var offset = 0
    while (offset < title.length) {
        val pair = title[offset] in '\uD800'..'\uDBFF' && offset + 1 < title.length &&
            title[offset + 1] in '\uDC00'..'\uDFFF'
        offset += if (pair) 2 else 1
        boundaries += offset
    }
    val codePoints = boundaries.size - 1
    var low = 2 // The beginning and the ending each keep at least one code point.
    var high = codePoints - 1
    var result = ellipsis
    while (low <= high) {
        val retained = low + (high - low) / 2
        val prefix = retained / 2
        val suffix = retained - prefix
        val candidate = title.substring(0, boundaries[prefix]) + ellipsis +
            title.substring(boundaries[codePoints - suffix])
        if (fits(candidate)) {
            result = candidate
            low = retained + 1
        } else {
            high = retained - 1
        }
    }
    return result
}
