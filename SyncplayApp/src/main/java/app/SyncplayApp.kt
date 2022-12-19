package app

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Having an extended custom 'Application' class will allow you to contain some properties that can be
 * accessed through the whole application by any given context, and also, the application context lives
 * through the whole application's lifecycle. So, when you need a long-living persistent memory-leak-free context,
 * such as in the case of Jetpack DataStore API, then this method works best (this, or dependency injection using Hilt). */
class SyncplayApp : Application() {

    companion object {
        /** The parent key of all DataStore preferences */
        val SYNCPLAY_SETTINGS = "syncplaysettings"

        /** Our singleton property which will be accessed throughout the app */
        var dataStore: DataStore<Preferences>? = null

        /** Our readable preference flows all in one map just to be easily accessible only using keys */
        val booleanPrefs = mutableMapOf<String, Flow<Boolean>>()
        val stringPrefs = mutableMapOf<String, Flow<String>>()
        val intPrefs = mutableMapOf<String, Flow<Int>>()

        fun readString(key: String, default: String): Flow<String>? {
            return dataStore?.data?.map { preferences ->
                preferences[stringPreferencesKey(key)] ?: default
            }
        }

        fun readBoolean(key: String, default: Boolean): Flow<Boolean>? {
            return dataStore?.data?.map { preferences ->
                preferences[booleanPreferencesKey(key)] ?: default
            }
        }

        fun readInt(key: String, default: Int): Flow<Int>? {
            return dataStore?.data?.map { preferences ->
                preferences[intPreferencesKey(key)] ?: default
            }
        }

        /** Now onto the writing functions will store data */
        suspend fun writeString(key: String, value: String) {
            dataStore?.edit { preferences ->
                preferences[stringPreferencesKey(key)] = value
            }
        }

        suspend fun writeBoolean(key: String, value: Boolean) {
            dataStore?.edit { preferences ->
                preferences[booleanPreferencesKey(key)] = value
            }
        }

        suspend fun writeInt(key: String, value: Int) {
            dataStore?.edit { preferences ->
                preferences[intPreferencesKey(key)] = value
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        /** Initializing datastore using app context */
        dataStore = PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler(produceNewData = { emptyPreferences() }),
            migrations = listOf(),
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { applicationContext.preferencesDataStoreFile(SYNCPLAY_SETTINGS) }
        )

    }


}