package app.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

object DataStoreUtils {

    /** The static datastore list that holds all datastore instances throughout the app's lifetime */
    val datastores: MutableMap<String, DataStore<Preferences>> = mutableMapOf()

    /** Creates an instance of the datastore which are saved in a file with the given string
     * @param filekey The file name that'll be created, which holds datastore data */
    fun Context.createDataStore(filekey: String): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler(produceNewData = { emptyPreferences() }),
            migrations = listOf(),
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { applicationContext.preferencesDataStoreFile(filekey) }
        )
    }


    /** Flow obtainers, these are the ones that are gonna return flows to read from */
    fun DataStore<Preferences>.stringFlow(key: String, default: String): Flow<String> {
        return this.data.map { preferences ->
            preferences[stringPreferencesKey(key)] ?: default
        }
    }

    fun DataStore<Preferences>.booleanFlow(key: String, default: Boolean): Flow<Boolean> {
        return this.data.map { preferences ->
            preferences[booleanPreferencesKey(key)] ?: default
        }
    }

    fun DataStore<Preferences>.intFlow(key: String, default: Int): Flow<Int> {
        return this.data.map { preferences ->
            preferences[intPreferencesKey(key)] ?: default
        }
    }

    fun DataStore<Preferences>.stringSetFlow(key: String, default: Set<String>): Flow<Collection<String>> {
        return this.data.map { preferences ->
            preferences[stringSetPreferencesKey(key)] ?: default
        }
    }


    /** Methods that write to flows (low level) */
    suspend fun DataStore<Preferences>.writeString(key: String, value: String) {
        this.edit { preferences ->
            preferences[stringPreferencesKey(key)] = value
        }
    }

    suspend fun DataStore<Preferences>.writeBoolean(key: String, value: Boolean) {
        this.edit { preferences ->
            preferences[booleanPreferencesKey(key)] = value
        }
    }

    suspend fun DataStore<Preferences>.writeInt(key: String, value: Int) {
        this.edit { preferences ->
            preferences[intPreferencesKey(key)] = value
        }
    }

    suspend fun DataStore<Preferences>.writeStringSet(key: String, value: Set<String>) {
        this.edit { preferences ->
            preferences[stringSetPreferencesKey(key)] = value
        }
    }


    /** The rest are convenience methods which we will be using when fetching or writing data outside settings */

    /** Returns the datastore instance corresponding to the given key */
    fun String.ds(): DataStore<Preferences> {
        return datastores[this]!!
    }

    suspend fun String.obtainString(key: String, default: String): String {
        return this.ds().stringFlow(key, default).first()
    }

    suspend fun String.obtainBoolean(key: String, default: Boolean): Boolean {
        return this.ds().booleanFlow(key, default).first()
    }

    suspend fun String.obtainInt(key: String, default: Int): Int {
        return this.ds().intFlow(key, default).first()
    }

    suspend fun String.obtainStringSet(key: String, default: Set<String>): Set<String> {
        return this.ds().stringSetFlow(key, default).first().toSet()
    }

    suspend fun String.writeString(key: String, value: String) {
        this.ds().edit { preferences ->
            preferences[stringPreferencesKey(key)] = value
        }
    }

    suspend fun String.writeBoolean(key: String, value: Boolean) {
        this.ds().edit { preferences ->
            preferences[booleanPreferencesKey(key)] = value
        }
    }

    suspend fun String.writeInt(key: String, value: Int) {
        this.ds().edit { preferences ->
            preferences[intPreferencesKey(key)] = value
        }
    }

    suspend fun String.writeStringSet(key: String, value: Set<String>) {
        this.ds().edit { preferences ->
            preferences[stringSetPreferencesKey(key)] = value
        }
    }
}