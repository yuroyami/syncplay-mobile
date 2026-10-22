package app.utils

import kotlinx.coroutines.CoroutineDispatcher

/**
 * The dispatcher for blocking work: file reads, preference writes, socket IO and hashing.
 *
 * Common code cannot name `Dispatchers.IO`. The coroutines library declares it only for the JVM
 * and Kotlin/Native, and the web target also compiles this source set. So shared code uses
 * [ioDispatcher] instead.
 *
 * Android, iOS and desktop map it to `Dispatchers.IO`. The web maps it to `Dispatchers.Default`,
 * because a page has one thread and no thread pool.
 */
expect val ioDispatcher: CoroutineDispatcher
