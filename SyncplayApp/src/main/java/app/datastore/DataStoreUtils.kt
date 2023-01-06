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
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

object DataStoreUtils {
    val datastores: MutableMap<String, DataStore<Preferences>> = mutableMapOf()

    fun Context.createDataStore(filekey: String): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler(produceNewData = { emptyPreferences() }),
            migrations = listOf(),
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { applicationContext.preferencesDataStoreFile(filekey) }
        )
    }

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

    /** Now onto the writing functions will store data */
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


    /** The rest are convenience methods */
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
}