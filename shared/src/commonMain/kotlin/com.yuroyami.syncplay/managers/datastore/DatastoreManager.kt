package com.yuroyami.syncplay.managers.datastore

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
import com.yuroyami.syncplay.AbstractManager
import com.yuroyami.syncplay.viewmodels.SyncplayViewmodel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class DatastoreManager(val viewmodel: SyncplayViewmodel) : AbstractManager(viewmodel) {
    companion object {

        /**
         * Maps preference key names to type-safe [Preferences.Key] instances.
         *
         * This generic function provides compile-time type safety for preference keys
         * by leveraging Kotlin's reified generics. It supports all DataStore preference types.
         *
         * Supported types:
         * - [Int] -> [intPreferencesKey]
         * - [Double] -> [doublePreferencesKey]
         * - [String] -> [stringPreferencesKey]
         * - [Boolean] -> [booleanPreferencesKey]
         * - [Float] -> [floatPreferencesKey]
         * - [Long] -> [longPreferencesKey]
         * - [Set] -> [stringSetPreferencesKey]
         * - [ByteArray] -> [byteArrayPreferencesKey]
         *
         * @param name The preference key name
         * @return Type-safe [Preferences.Key] for the specified type
         * @throws IllegalArgumentException if the type is not supported by DataStore
         *
         * Example:
         * ```
         * val key: Preferences.Key<String> = prefKeyMapper("username")
         * ```
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
        /** ==================== OPTIMIZED FLOW OBTAINER ==================== */

        /**
         * Creates a Flow derived from the single shared StateFlow.
         * Only emits when THIS specific key's value changes, not on every preference change.
         *
         * Uses distinctUntilChanged implicitly through map operator behavior.
         */
        inline fun <reified T> valueFlow(key: String, default: T): Flow<T> {
            val preferencesKey: Preferences.Key<T> = prefKeyMapper(key)
            return datastoreStateFlow.map { preferences ->
                preferences[preferencesKey] ?: default
            }.distinctUntilChanged() //Only emit NEW values, avoids useless recompositions
        }

        /** ==================== WRITE Operation ==================== */

        suspend inline fun <reified T> writeValue(key: String, value: T) {
            datastore.edit { preferences ->
                val preferencesKey: Preferences.Key<T> = prefKeyMapper(key)
                preferences[preferencesKey] = value
            }
        }

        /** ==================== READ Operation ==================== */

        inline fun <reified T> value(key: String, default: T): T {
            val preferencesKey: Preferences.Key<T> = prefKeyMapper(key)
            return datastoreStateFlow.value[preferencesKey] ?: default
        }

        /** ==================== COMPOSE INTEGRATION ==================== */
        @Composable
        inline fun <reified T> String.watch(default: T): State<T> {
            return valueFlow(this, default).collectAsState(initial = default)
        }
    }
}
