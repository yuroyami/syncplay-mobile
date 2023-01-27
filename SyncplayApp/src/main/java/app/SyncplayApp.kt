package app

import android.app.Application
import app.datastore.DataStoreKeys
import app.datastore.DataStoreUtils
import app.datastore.DataStoreUtils.createDataStore

/** Having an extended custom 'Application' class will allow you to contain some properties that can be
 * accessed through the whole application by any given context, and also, the application context lives
 * through the whole application's lifecycle. So, when you need a long-living persistent memory-leak-free context,
 * such as in the case of Jetpack DataStore API, then this method works best (this, or dependency injection using Hilt). */
class SyncplayApp : Application() {

    override fun onCreate() {
        super.onCreate()

        /** Creating a list containing the different preference groups.
         * We can use one single datastore for everything, but it's best to separate concerns */
        val datastorekeys = listOf(
            DataStoreKeys.DATASTORE_GLOBAL_SETTINGS,
            DataStoreKeys.DATASTORE_INROOM_PREFERENCES,
            DataStoreKeys.DATASTORE_MISC_PREFS
        )

        /** The keys we created in the past list are gonna be used to create datastore filenames */
        for (key in datastorekeys) {
            /** Now, creating the datastore for each group of preferences. Prefereably,
             * every setting screen needs its own group of datastore, so they can be accessed simulataneously */
            DataStoreUtils.datastores[key] = applicationContext.createDataStore(key)
        }
    }
}