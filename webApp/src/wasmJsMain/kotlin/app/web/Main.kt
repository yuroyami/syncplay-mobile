package app.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import app.AdamScreen
import app.SyncplayViewmodel
import app.WebPlatformCallback
import app.preferences.awaitPreferences
import app.preferences.initializeWebDatastore
import app.preferences.warmPreferences
import app.utils.platformCallback
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Global viewmodel handle, mirroring the Android Activity and the desktop window. */
var globalViewmodel: SyncplayViewmodel? = null

/**
 * The browser entry point.
 *
 * The order matters and differs from every other platform in one way: nothing composes until the
 * stored preferences have been read. Android holds its splash and desktop blocks the calling
 * thread, but a page can do neither, so it waits properly instead. The wait is a localStorage
 * read, so it is over before the browser has finished painting anything.
 *
 * `ComposeViewport` rather than the older canvas entry point on purpose: it is the one that
 * supports putting real HTML elements inside the Compose layout, which is how the video engine
 * will eventually get its `<video>`.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    initializeWebDatastore()
    platformCallback = WebPlatformCallback
    warmPreferences()

    CoroutineScope(Dispatchers.Main).launch {
        awaitPreferences()
        ComposeViewport(document.body!!) {
            AdamScreen(onGlobalViewmodel = { globalViewmodel = it })
        }
    }
}
