package app.preferences

import kotlin.math.abs

/**
 * Formats a value in tenths for display (15 becomes "1.5"). [zeroPoint] handles sliders whose
 * stored zero is shifted into a positive range.
 */
internal fun formatTenths(value: Int, zeroPoint: Int = 0, signed: Boolean = false): String {
    val tenths = value.toLong() - zeroPoint
    val sign = when {
        tenths < 0 -> "-"
        tenths > 0 && signed -> "+"
        else -> ""
    }
    val magnitude = abs(tenths)
    return "$sign${magnitude / 10}.${magnitude % 10}"
}
