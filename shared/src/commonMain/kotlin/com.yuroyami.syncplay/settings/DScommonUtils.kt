package com.yuroyami.syncplay.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.runBlocking
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
@Suppress("UNCHECKED_CAST")
inline fun <reified T> prefKeyMapper(name: String): Preferences.Key<T> {
    return when (T::class) {
        Int::class -> intPreferencesKey(name)
        Double::class -> doublePreferencesKey(name)
        String::class -> stringPreferencesKey(name)
        Boolean::class -> booleanPreferencesKey(name)
        Float::class -> floatPreferencesKey(name)
        Long::class -> longPreferencesKey(name)
        Set::class -> stringSetPreferencesKey(name)
        ByteArray::class -> byteArrayPreferencesKey(name)
        else -> throw IllegalArgumentException("Unsupported type: ${T::class}")
    } as Preferences.Key<T>
}

inline fun <reified T> valueFlow(key: String, default: T): Flow<T> {
    return datastore.data.mapNotNull { preferences ->
        val preferencesKey: Preferences.Key<T> = prefKeyMapper(key)
        preferences[preferencesKey] ?: default
    }
}

/** Methods that write to flows (low level) */
suspend inline fun <reified T> writeValue(key: String, value: T) {
    datastore.edit { preferences ->
        val preferencesKey: Preferences.Key<T> = prefKeyMapper(key)
        preferences[preferencesKey] = value
    }
}

/** ==== Convenience methods to obtain values ====== */
/** Gets the current value of the flow suspendingly, which means it may suspend until a value is present */
suspend inline fun <reified T> valueSuspendingly(key: String, default: T): T {
    return valueFlow(key, default).first()
}

suspend inline fun <reified T> valueSusQuick(key: String, default: T): T? {
    return valueFlow(key, default).firstOrNull()
}


/** Gets the current value of the flow blockingly (for non-coroutine context),
 * To avoid blocking the thread, we return null if no value is present anyway
 */
inline fun <reified T> valueBlockingly(key: String, default: T): T {
    return runBlocking { valueFlow(key, default).firstOrNull() ?: default }
}
