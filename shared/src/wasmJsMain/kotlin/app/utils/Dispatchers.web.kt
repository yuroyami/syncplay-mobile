package app.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * A page has one thread. There is no pool to move blocking work to, so this is the same
 * dispatcher everything else already runs on, and the name only keeps common code compiling.
 */
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
