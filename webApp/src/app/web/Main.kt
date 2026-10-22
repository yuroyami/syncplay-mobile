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

/** The app viewmodel, held here as the Android Activity and the desktop window hold theirs. */
var globalViewmodel: SyncplayViewmodel? = null

/**
 * The browser entry point.
 *
 * The call order matters: nothing composes until the stored preferences have been read. Android
 * holds its splash screen and desktop blocks the calling thread, but a page can do neither, so it
 * suspends in awaitPreferences instead. The wait is one localStorage read, so it ends before the
 * browser paints anything.
 *
 * This uses `ComposeViewport`, not the older canvas entry point, because only `ComposeViewport`
 * can put real HTML elements inside the Compose layout. The web video engine needs that for its
 * `<video>` element.
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
