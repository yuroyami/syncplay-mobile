package com.yuroyami.syncplay.managers.datastore

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
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


//TODO Refactor Datastore to only access datastore.data flow collection only ONCE
/**
 * DataStore management utilities for Syncplay-mobile preference storage.
 *
 * This module provides a type-safe, thread-safe coroutine-friendly abstraction over Android's DataStore
 * for managing application preferences. It supports multiple data types and provides both
 * synchronous and asynchronous access patterns with Compose integration.
 *
 * Usage example:
 * ```
 * // Read as Flow
 * val usernameFlow = valueFlow<String>("username", "default")
 *
 * // Read in Composable
 * val username by "username".valueAsState("default")
 *
 * // Write value
 * writeValue("username", "newUsername")
 * ```
 *
 * @see createDataStore Factory function for DataStore initialization
 * @see valueFlow Get a Flow for reactive preference observation
 * @see valueAsState Compose integration for preference values
 */
typealias DS = DataStore<Preferences>
/**
 * Creates a [DataStore] instance with the specified file path.
 *
 * This factory function initializes the Preference DataStore with the given path.
 * The DataStore is created without corruption handling or migrations by default.
 *
 * @param producePath Lambda that provides the file path for storing preferences
 * @return Configured [DataStore] instance ready for use
 */
fun createDataStore(
    producePath: () -> String,
): DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(
    corruptionHandler = null,
    migrations = emptyList(),
    produceFile = { producePath().toPath() },
)

/**
 * Global DataStore instance for application preferences.
 *
 * This lateinit variable must be initialized using [createDataStore] before any
 * preference operations are performed. Accessing this before initialization
 * will throw [UninitializedPropertyAccessException].
 */
lateinit var datastore: DataStore<Preferences>

/**
 * Cached flow of DataStore preferences for efficient access.
 *
 * This lazy-initialized flow ensures that [datastore.data] is collected only once
 * throughout the application lifecycle, preventing multiple active collectors
 * and improving performance.
 */
val datastoreDataFlow: Flow<Preferences> by lazy { datastore.data }

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

/** ==================== FLOW OBTAINERS ==================== */

/**
 * Creates a [Flow] that emits the current value of a preference and any subsequent changes.
 *
 * This is the primary method for reactive preference observation. The flow will emit:
 * - The current preference value if it exists
 * - The default value if the preference is not set
 * - New values whenever the preference is updated via [writeValue]
 *
 * @param key The preference key name
 * @param default The default value to use if the preference is not set
 * @return [Flow] that emits preference values reactively
 *
 * Example:
 * ```
 * val themeFlow = valueFlow<String>("app_theme", "dark")
 * themeFlow.collect { theme -> updateAppTheme(theme) }
 * ```
 */
inline fun <reified T> valueFlow(key: String, default: T): Flow<T> {
    return datastoreDataFlow.mapNotNull { preferences ->
        val preferencesKey: Preferences.Key<T> = prefKeyMapper(key)
        preferences[preferencesKey] ?: default
    }
}

/** ==================== WRITE OPERATIONS ==================== */

/**
 * Writes a value to preferences asynchronously.
 *
 * This suspending function updates the preference value in a thread-safe manner.
 * The write operation is performed on the DataStore's background thread.
 *
 * @param key The preference key name
 * @param value The value to store (must match the expected type)
 *
 * Example:
 * ```
 * // In a coroutine
 * writeValue("volume_level", 0.75)
 * writeValue("notifications_enabled", true)
 * ```
 */
suspend inline fun <reified T> writeValue(key: String, value: T) {
    datastore.edit { preferences ->
        val preferencesKey: Preferences.Key<T> = prefKeyMapper(key)
        preferences[preferencesKey] = value
    }
}

/** ==================== CONVENIENCE READERS ==================== */

/**
 * Reads the current preference value suspendingly.
 *
 * This function suspends until the current preference value is available.
 * Useful for one-time reads in coroutine contexts.
 *
 * @param key The preference key name
 * @param default The default value if preference is not set
 * @return The current preference value or default
 *
 * Example:
 * ```
 * // In a suspending function
 * val username = valueSuspendingly<String>("username", "guest")
 * ```
 */
suspend inline fun <reified T> valueSuspendingly(key: String, default: T): T {
    return valueFlow(key, default).first()
}

/**
 * Reads the current preference value suspendingly with quick null return.
 *
 * Similar to [valueSuspendingly] but returns null if no value is immediately available.
 * Useful for non-critical preference reads where a default can be handled elsewhere.
 *
 * @param key The preference key name
 * @param default The default value for the flow (returned as null if not available)
 * @return The current preference value or null if not immediately available
 */
// TODO: Use nullable valueFlow without defaults
suspend inline fun <reified T> valueSusQuick(key: String, default: T): T? {
    return valueFlow(key, default).firstOrNull()
}

/**
 * Reads the current preference value in a blocking manner.
 *
 * **Use with caution:** This function blocks the current thread until the value is available.
 * Only use in non-coroutine contexts where suspending is not possible.
 *
 * @param key The preference key name
 * @param default The default value if preference is not set
 * @return The current preference value or default
 *
 * Example:
 * ```
 * // In non-coroutine context
 * val userId = valueBlockingly<Int>("user_id", -1)
 * ```
 */
inline fun <reified T> valueBlockingly(key: String, default: T): T {
    return runBlocking { valueFlow(key, default).firstOrNull() ?: default }
}

/** ==================== COMPOSE INTEGRATION ==================== */

/**
 * Extension function to observe a preference as Compose [State].
 *
 * This function provides seamless integration with Jetpack Compose, allowing
 * preference values to be observed reactively in Composable functions.
 *
 * @param default The default value if preference is not set
 * @return [State] that can be used directly in Composable functions
 *
 * Example:
 * ```
 * @Composable
 * fun SettingsScreen() {
 *     val username by "username".valueAsState("guest")
 *     Text("Hello, $username")
 * }
 * ```
 */
@Composable
inline fun <reified T> String.valueAsState(default: T): State<T> {
    return valueFlow(this, default).collectAsState(initial = default)
}