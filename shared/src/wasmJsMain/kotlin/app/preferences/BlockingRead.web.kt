package app.preferences

/** Null always: blocking the browser's one thread freezes the page. The caller reads async. */
internal actual fun <T> readBlockingOrNull(block: suspend () -> T): T? = null
