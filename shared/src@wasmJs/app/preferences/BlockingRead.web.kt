package app.preferences

/** Always null: blocking the browser's only thread freezes the page, so the caller reads async. */
internal actual fun <T> readBlockingOrNull(block: suspend () -> T): T? = null
