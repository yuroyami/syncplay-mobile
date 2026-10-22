package app.preferences

/**
 * Runs [block] to completion on the calling thread, or returns null where the platform forbids it.
 *
 * Only the first read of the preference store uses this. On Android, iOS and desktop, that read
 * happens before the first frame (Android also holds its splash screen), so blocking there is
 * allowed and is the simplest correct choice. A browser has one thread, and blocking it freezes
 * the page. So the web returns null, and the caller reads the store asynchronously.
 */
internal expect fun <T> readBlockingOrNull(block: suspend () -> T): T?
