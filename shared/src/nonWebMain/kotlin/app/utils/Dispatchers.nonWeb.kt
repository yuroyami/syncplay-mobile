package app.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/** Android, iOS and desktop have real threads, so this is the coroutines library's IO pool. */
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
