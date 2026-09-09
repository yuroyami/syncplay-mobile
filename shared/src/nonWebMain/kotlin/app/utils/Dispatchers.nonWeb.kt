package app.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/** Every platform with real threads: the pool coroutines already provides. */
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
