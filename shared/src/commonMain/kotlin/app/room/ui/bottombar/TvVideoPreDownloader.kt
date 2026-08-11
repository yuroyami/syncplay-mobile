package app.room.ui.bottombar

import androidx.compose.runtime.Composable

@Composable
internal expect fun rememberTvVideoPreDownloader(
    onProgress: (Float?) -> Unit,
    onComplete: (String) -> Unit,
    onError: (String) -> Unit,
): ((String) -> Unit)?

internal fun splitDownloadRanges(
    totalBytes: Long,
    requestedParts: Int,
): List<LongRange> {
    require(totalBytes > 0L)
    require(requestedParts > 0)

    val partCount = minOf(totalBytes, requestedParts.toLong()).toInt()
    val baseSize = totalBytes / partCount
    val remainder = totalBytes % partCount
    var start = 0L

    return List(partCount) { index ->
        val size = baseSize + if (index < remainder) 1L else 0L
        val range = start..(start + size - 1L)
        start = range.last + 1L
        range
    }
}

internal fun contentRangeMatches(
    header: String?,
    expectedRange: LongRange,
    expectedTotalBytes: Long,
): Boolean {
    val match = Regex(
        pattern = """bytes\s+(\d+)-(\d+)/(\d+)""",
        option = RegexOption.IGNORE_CASE,
    ).matchEntire(header?.trim().orEmpty()) ?: return false

    return match.groupValues[1].toLongOrNull() == expectedRange.first &&
        match.groupValues[2].toLongOrNull() == expectedRange.last &&
        match.groupValues[3].toLongOrNull() == expectedTotalBytes
}
