package com.yuroyami.syncplay.managers.preferences

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Maps preference key names to type-safe [Preferences.Key] instances.
 *
 * This generic function provides compile-time type safety for preference keys
 * by leveraging Kotlin's reified generics. It supports all DataStore preference types.
 */
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

/**
 * Base sealed class for all preference definitions.
 */
sealed class PreferenceDef<T>(val key: String)

/**
 * Preference with a fixed default value.
 * Use this for most preferences where the default is known at compile time.
 *
 * @param key The DataStore key name
 * @param default The default value when preference is not set
 */
data class StaticPref<T>(
    private val k: String,
    val default: T
) : PreferenceDef<T>(k)

/**
 * Preference with a runtime-computed default value.
 * Use this when the default depends on runtime state (e.g., system settings, user context).
 *
 * @param key The DataStore key name
 *
 * Example:
 * ```
 * val BUFFER_SIZE = DynamicPref<Int>("buffer_size")
 *
 * // Usage with runtime default
 * val bufferSize = BUFFER_SIZE.get(defaultTo = calculateOptimalBuffer())
 * ```
 */
data class DynamicPref<T>(
    private val k: String
) : PreferenceDef<T>(k)

/** ==================== STATIC PREFERENCE EXTENSIONS ==================== */

/**
 * Get the current value using the static default.
 */
inline fun <reified T> StaticPref<T>.get(): T {
    val preferencesKey = prefKeyMapper<T>(key)
    return datastoreStateFlow.value[preferencesKey] ?: default
}

/**
 * Get the current value or null if not set (ignores default).
 */
inline fun <reified T> StaticPref<T>.getOrNull(): T? {
    val preferencesKey = prefKeyMapper<T>(key)
    return datastoreStateFlow.value[preferencesKey]
}

/**
 * Get a reactive Flow using the static default.
 */
inline fun <reified T> StaticPref<T>.flow(): Flow<T> {
    val preferencesKey = prefKeyMapper<T>(key)
    return datastoreStateFlow
        .map { preferences -> preferences[preferencesKey] ?: default }
        .distinctUntilChanged()
}

/**
 * Observe as Compose State using the static default.
 */
@Composable
inline fun <reified T> StaticPref<T>.watchPref(): State<T> {
    return flow().collectAsState(initial = default)
}

/** ==================== DYNAMIC PREFERENCE EXTENSIONS ==================== */

/**
 * Get the current value, providing a runtime default.
 * The default is REQUIRED since DynamicPref has no compile-time default.
 */
inline fun <reified T> DynamicPref<T>.get(defaultTo: T): T {
    val preferencesKey = prefKeyMapper<T>(key)
    return datastoreStateFlow.value[preferencesKey] ?: defaultTo
}

/**
 * Get the current value or null if not set.
 */
inline fun <reified T> DynamicPref<T>.getOrNull(): T? {
    val preferencesKey = prefKeyMapper<T>(key)
    return datastoreStateFlow.value[preferencesKey]
}

/**
 * Get a reactive Flow, providing a runtime default.
 */
inline fun <reified T> DynamicPref<T>.flow(defaultTo: T): Flow<T> {
    val preferencesKey = prefKeyMapper<T>(key)
    return datastoreStateFlow
        .map { preferences -> preferences[preferencesKey] ?: defaultTo }
        .distinctUntilChanged()
}

/**
 * Observe as Compose State, providing a runtime default.
 */
@Composable
inline fun <reified T> DynamicPref<T>.watchPref(defaultTo: T): State<T> {
    return flow(defaultTo).collectAsState(initial = defaultTo)
}

/** ==================== SHARED EXTENSIONS (Both types) ==================== */

/**
 * Write a new value to this preference.
 */
suspend inline fun <reified T> PreferenceDef<T>.set(value: T) {
    datastore.edit { preferences ->
        val preferencesKey = prefKeyMapper<T>(key)
        preferences[preferencesKey] = value
    }
}

/**
 * Delete this preference, removing it from storage.
 */
suspend inline fun <reified T> PreferenceDef<T>.delete() {
    datastore.edit { preferences ->
        val preferencesKey = prefKeyMapper<T>(key)
        preferences.remove(preferencesKey)
    }
}

/**
 * Check if this preference has been explicitly set.
 */
inline fun <reified T> PreferenceDef<T>.isSet(): Boolean {
    val preferencesKey = prefKeyMapper<T>(key)
    return datastoreStateFlow.value.contains(preferencesKey)
}