package app.preferences

import kotlinx.coroutines.runBlocking

/** JVM and Native both have a thread to spare, and a splash covering the wait. */
internal actual fun <T> readBlockingOrNull(block: suspend () -> T): T? = runBlocking { block() }
