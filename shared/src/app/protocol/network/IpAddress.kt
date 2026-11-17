package app.protocol.network

/**
 * Formats a raw IP address for the "Successfully reached" line: dotted for IPv4, and for IPv6
 * the short form, where the longest run of zero groups becomes "::". Null for any other length.
 */
fun formatIpAddress(bytes: ByteArray): String? = when (bytes.size) {
    4 -> bytes.joinToString(".") { (it.toInt() and 0xFF).toString() }
    16 -> formatIpv6(bytes)
    else -> null
}

private fun formatIpv6(bytes: ByteArray): String {
    val groups = List(8) { ((bytes[it * 2].toInt() and 0xFF) shl 8) or (bytes[it * 2 + 1].toInt() and 0xFF) }
    // An IPv4 address carried in IPv6 (::ffff:a.b.c.d) prints as plain IPv4.
    if (groups.take(5).all { it == 0 } && groups[5] == 0xFFFF) return formatIpAddress(bytes.copyOfRange(12, 16))!!

    var bestStart = -1
    var bestLength = 0
    var i = 0
    while (i < groups.size) {
        if (groups[i] != 0) { i++; continue }
        val start = i
        while (i < groups.size && groups[i] == 0) i++
        if (i - start > bestLength) { bestStart = start; bestLength = i - start }
    }
    fun hex(part: List<Int>) = part.joinToString(":") { it.toString(16) }
    // A single zero group stays as "0" (RFC 5952, section 4.2.2).
    if (bestLength < 2) return hex(groups)
    return hex(groups.take(bestStart)) + "::" + hex(groups.drop(bestStart + bestLength))
}
