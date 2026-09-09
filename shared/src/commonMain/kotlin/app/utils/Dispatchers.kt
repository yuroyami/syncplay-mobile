package app.utils

import kotlinx.coroutines.CoroutineDispatcher

/**
 * The dispatcher for work that blocks: file reads, preference writes, socket IO, hashing.
 *
 * Common code cannot simply name the coroutines library's own IO dispatcher. That one is declared
 * for the JVM and for Kotlin/Native, and for nothing else, so the moment a browser target joins
 * the same source set every call site stops compiling. This is the name the shared code uses.
 *
 * Android, iOS and desktop map it straight through. The web maps it to `Dispatchers.Default`,
 * because a page has one thread and there is no pool to hand work to.
 */
expect val ioDispatcher: CoroutineDispatcher
