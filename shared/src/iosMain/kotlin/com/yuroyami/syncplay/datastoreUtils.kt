package com.yuroyami.syncplay

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.yuroyami.syncplay.settings.DataStoreKeys
import com.yuroyami.syncplay.settings.createDataStore
import com.yuroyami.syncplay.datastore.datastoreFiles
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


fun initializeDatastoreIOS() {
    val datastorekeys = listOf(
        DataStoreKeys.DATASTORE_GLOBAL_SETTINGS,
        DataStoreKeys.DATASTORE_INROOM_PREFERENCES,
        DataStoreKeys.DATASTORE_MISC_PREFS
    )

    /** The keys we created in the above list are gonna be used to create datastore filenames */
    try {
        for (key in datastorekeys) {
            /** Now, creating the datastore for each group of preferences
             * Note that we could use one globalized datastore file, there is no difference. */
            datastoreFiles[key] = dataStore(key)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

}