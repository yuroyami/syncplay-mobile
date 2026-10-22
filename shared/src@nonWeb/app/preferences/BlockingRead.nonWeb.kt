package app.preferences

import kotlinx.coroutines.runBlocking

/** Android, iOS and desktop can block a thread here, and a splash screen covers the wait. */
internal actual fun <T> readBlockingOrNull(block: suspend () -> T): T? = runBlocking { block() }
