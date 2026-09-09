package app.preferences

/**
 * Runs [block] to completion on the calling thread, or returns null where the platform forbids it.
 *
 * Only the preference store's very first read uses this. Android, iOS and desktop all hold a
 * splash while that read happens, so blocking there is both allowed and the simplest correct
 * thing. A browser has one thread and blocking it freezes the page, so the web returns null and
 * the caller reads the store asynchronously instead.
 */
internal expect fun <T> readBlockingOrNull(block: suspend () -> T): T?
