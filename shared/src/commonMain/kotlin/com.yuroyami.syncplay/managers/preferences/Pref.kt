package com.yuroyami.syncplay.managers.preferences

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.StringResource

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

enum class SettingType {
    SingleAction, TextField, MultiChoice, ShowPopup, ColorPick
}

data class SettingConfig(
    var title: StringResource,
    var summary: StringResource,
    var icon: ImageVector? = null,

    var rationale: StringResource? = null,

    var settingType: SettingType,

    var extraConfig: ExtraConfig
)


sealed interface ExtraConfig {
    data class ActionSettingConfig(
        val onClick: () -> Unit
    ) : ExtraConfig

    data class SliderSettingConfig(
        val maxValue: Int = 100,
        val minValue: Int = 0,
        val onValueChanged: ((newValue: Int) -> Unit)? = null
    ) : ExtraConfig

    data class MultiChoiceSettingConfig(
        val entries: @Composable () -> Map<String, String>,
        val onItemChosen: ((value: String) -> Unit)? = null
    ) : ExtraConfig

    data class ShowComposableSettingConfig(
        val composable: @Composable () -> Unit
    ) : ExtraConfig
}

/**
 * Base sealed class for all preference definitions.
 */
data class Pref<T>(
    val key: String,
    val default: T,
    var config: (SettingConfig.() -> Unit)? = null
)


/** ==================== STATIC PREFERENCE EXTENSIONS ==================== */

/**
 * Get the current value using the static default.
 */
inline fun <reified T> Pref<T>.get(): T {
    val preferencesKey = prefKeyMapper<T>(key)
    return datastoreStateFlow.value[preferencesKey] ?: default
}

/**
 * Get the current value or null if not set (ignores default).
 */
inline fun <reified T> Pref<T>.getOrNull(): T? {
    val preferencesKey = prefKeyMapper<T>(key)
    return datastoreStateFlow.value[preferencesKey]
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

/**
 * Delete this preference, removing it from storage.
 */
suspend inline fun <reified T> Pref<T>.delete() {
    datastore.edit { preferences ->
        val preferencesKey = prefKeyMapper<T>(key)
        preferences.remove(preferencesKey)
    }
}

/**
 * Check if this preference has been explicitly set.
 */
inline fun <reified T> Pref<T>.isSet(): Boolean {
    val preferencesKey = prefKeyMapper<T>(key)
    return datastoreStateFlow.value.contains(preferencesKey)
}