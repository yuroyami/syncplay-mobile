package com.yuroyami.syncplay.managers.preferences

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.yuroyami.syncplay.managers.settings.ExtraConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.StringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.okay

class Pref<T>(
    val key: String,
    val default: T,
    var configLambda: (SettingConfig.() -> Unit)? = null
) {
    val config: SettingConfig? by lazy {
        configLambda?.let {
            SettingConfig().apply(it)
        }
    }
}

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


data class SettingConfig(
    var title: StringResource = Res.string.okay,
    var summary: StringResource = Res.string.okay,
    var icon: ImageVector = Icons.Filled.Done,

    var extraConfig: ExtraConfig? = null
)
/**
 * Get the current value using the static default.
 */
inline fun <reified T> Pref<T>.get(): T {
    val preferencesKey = prefKeyMapper<T>(key)
    return datastoreStateFlow.value[preferencesKey] ?: default
}

/**
 * Get a reactive Flow using the static default.
 */
inline fun <reified T> Pref<T>.flow(): Flow<T> {
    val preferencesKey = prefKeyMapper<T>(key)
    return datastoreStateFlow
        .map { preferences -> preferences[preferencesKey] ?: default }
        .distinctUntilChanged()
}

/**
 * Observe as Compose State using the static default.
 */
@Composable
inline fun <reified T> Pref<T>.watchPref(): State<T> {
    return flow().collectAsState(initial = default)
}

/**
 * Write a new value to this preference.
 */
suspend inline fun <reified T> Pref<T>.set(value: T) {
    datastore.edit { preferences ->
        val preferencesKey = prefKeyMapper<T>(key)
        preferences[preferencesKey] = value
    }
}