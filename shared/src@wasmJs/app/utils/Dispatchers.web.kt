package app.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * A page has one thread, so there is no pool for blocking work. This is the same dispatcher that
 * everything else runs on; the name only keeps common code compiling.
 */
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
