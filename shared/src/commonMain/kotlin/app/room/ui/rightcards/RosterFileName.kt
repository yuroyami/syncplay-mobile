package app.room.ui.rightcards

import app.utils.mediaExs

/**
 * The title used by the compact roster. This changes presentation only; the actual filename
 * remains available for file comparison and the detailed row. Width-based shortening belongs
 * to the text layout, so a wider panel can show more of the title.
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

    // Release groups may be adjacent, repeated or nested. Leave unmatched brackets and
    // bracket-only titles intact rather than removing the only useful name we have.
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
 * Keeps the title's beginning and ending inside a measured text width. [fits] should measure
 * the complete candidate with the font used to draw it; a character limit is not a width.
 * The extra character in an odd split belongs to the ending, where episode numbers live.
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
    var low = 2 // Both the title and its ending must retain at least one code point.
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
