package app.utils

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import app.preferences.Preferences.SYNKPLAY_PREFS
import app.preferences.createDataStore
import app.preferences.datastore
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
fun dataStore(fileName: String): DataStore<Preferences> = createDataStore(
    producePath = {
        val documentDirectory: NSURL? = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null,
        )
        requireNotNull(documentDirectory).path + "/$fileName"
    }
)

fun initializeDS() {
    runCatching {
        datastore = dataStore(SYNKPLAY_PREFS)
    }
}