package app.utils

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import app.preferences.Preferences.SYNKPLAY_PREFS
import app.preferences.createDataStore
import app.preferences.datastore
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/**
 * The preference store lives in Application Support, which the Files app never shows and iCloud
 * does not sync by document. It used to sit in Documents, which this app exposes to the Files app
 * on purpose for media, so the saved room password was one tap away; an existing store is moved
 * across once.
 */
@OptIn(ExperimentalForeignApi::class)
fun dataStore(fileName: String): DataStore<Preferences> = createDataStore(
    producePath = {
        val fm = NSFileManager.defaultManager
        val support: NSURL? = fm.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
        val documents: NSURL? = fm.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null,
        )
        val target = requireNotNull(support).path + "/$fileName"
        val legacy = documents?.path?.let { "$it/$fileName" }
        if (legacy != null && !fm.fileExistsAtPath(target) && fm.fileExistsAtPath(legacy)) {
            fm.moveItemAtPath(legacy, target, null)
        }
        target
    }
)

fun initializeDS() {
    runCatching {
        datastore = dataStore(SYNKPLAY_PREFS)
    }
}