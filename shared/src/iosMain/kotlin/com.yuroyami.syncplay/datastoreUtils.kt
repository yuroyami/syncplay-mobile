package com.yuroyami.syncplay

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.yuroyami.syncplay.managers.managers.datastore.DataStoreKeys
import com.yuroyami.syncplay.managers.managers.datastore.createDataStore
import com.yuroyami.syncplay.managers.managers.datastore.datastore
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
    /** The keys we created in the above list are gonna be used to create datastore filenames */
    try {
        datastore = dataStore(DataStoreKeys.SYNCPLAY_PREFS)
    } catch (e: Exception) {
        e.printStackTrace()
    }

}