package com.yuroyami.syncplay.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath

fun createDataStore(
    producePath: () -> String,
): DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(
    corruptionHandler = null,
    migrations = emptyList(),
    produceFile = { producePath().toPath() },
)

lateinit var datastore: DataStore<Preferences>

/** Flow obtainers, these are the ones that are gonna return flows to read from */
fun stringFlow(key: String, default: String): Flow<String> {
    return datastore.data.map { preferences ->
        preferences[stringPreferencesKey(key)] ?: default
    }
}

fun booleanFlow(key: String, default: Boolean): Flow<Boolean> {
    return datastore.data.map { preferences ->
        preferences[booleanPreferencesKey(key)] ?: default
    }
}

fun intFlow(key: String, default: Int): Flow<Int> {
    return datastore.data.map { preferences ->
        preferences[intPreferencesKey(key)] ?: default
    }
}

fun stringSetFlow(key: String, default: Set<String>): Flow<Collection<String>> {
    return datastore.data.map { preferences ->
        preferences[stringSetPreferencesKey(key)] ?: default
    }
}


/** Methods that write to flows (low level) */
suspend fun writeString(key: String, value: String) {
    datastore.edit { preferences ->
        preferences[stringPreferencesKey(key)] = value
    }
}

suspend fun writeBoolean(key: String, value: Boolean) {
    datastore.edit { preferences ->
        preferences[booleanPreferencesKey(key)] = value
    }
}

suspend fun writeInt(key: String, value: Int) {
    datastore.edit { preferences ->
        preferences[intPreferencesKey(key)] = value
    }
}

suspend fun writeStringSet(key: String, value: Set<String>) {
    datastore.edit { preferences ->
        preferences[stringSetPreferencesKey(key)] = value
    }
}


/** The rest are convenience methods which we will be using when fetching or writing data outside settings */
suspend fun obtainString(key: String, default: String): String {
    return stringFlow(key, default).first()
}

suspend fun obtainBoolean(key: String, default: Boolean): Boolean {
    return booleanFlow(key, default).first()
}

suspend fun obtainInt(key: String, default: Int): Int {
    return intFlow(key, default).first()
}

suspend fun obtainStringSet(key: String, default: Set<String>): Set<String> {
    return stringSetFlow(key, default).first().toSet()
}