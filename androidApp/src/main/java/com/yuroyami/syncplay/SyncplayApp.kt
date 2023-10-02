package com.yuroyami.syncplay

import android.app.Application
import com.yuroyami.syncplay.datastore.DataStoreKeys
import com.yuroyami.syncplay.datastore.datastoreFiles
import com.yuroyami.syncplay.utils.dataStore

class SyncplayApp: Application() {

    override fun onCreate() {
        super.onCreate()

        //Initializing datastore
        val datastorekeys = listOf(
            DataStoreKeys.DATASTORE_GLOBAL_SETTINGS,
            DataStoreKeys.DATASTORE_INROOM_PREFERENCES,
            DataStoreKeys.DATASTORE_MISC_PREFS
        )

        /** The keys we created in the above list are gonna be used to create datastore filenames */
        for (key in datastorekeys) {
            /** Now, creating the datastore for each group of preferences
             * Note that we could use one globalized datastore file, there is no difference. */
            datastoreFiles[key] = dataStore(applicationContext, key)
        }

    }
}