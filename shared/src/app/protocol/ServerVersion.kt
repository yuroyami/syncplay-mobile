package app.protocol

/** The lowest Syncplay server version with shared playlists (Syncplay PC's `SHARED_PLAYLIST_MIN_VERSION`). */
const val SHARED_PLAYLISTS_MIN_VERSION = "1.4.0"

/**
 * Whether [version] is [minimum] or newer, compared part by part as numbers, like Syncplay PC's
 * `meetsMinVersion`. A missing part counts as 0. Null when either version starts with no number.
 */
fun versionAtLeast(version: String, minimum: String): Boolean? {
    val have = versionParts(version) ?: return null
    val need = versionParts(minimum) ?: return null
    for (i in 0 until maxOf(have.size, need.size)) {
        val a = have.getOrElse(i) { 0 }
        val b = need.getOrElse(i) { 0 }
        if (a != b) return a > b
    }
    return true
}

/** The leading number of each dot-separated part, such as 1, 7 and 5 for "1.7.5-beta". */
private fun versionParts(version: String): List<Int>? {
    val parts = version.trim().split('.').map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() }
    if (parts.first() == null) return null
    return parts.map { it ?: 0 }
}
