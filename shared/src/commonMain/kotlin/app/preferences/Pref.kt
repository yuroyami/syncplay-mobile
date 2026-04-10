package app.preferences

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.StringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.okay

class Pref<T>(
    val key: String,
    val default: T,
    settingConfigLambda: (SettingConfig.() -> Unit)? = null
) {
    val config: SettingConfig? by lazy {
        settingConfigLambda?.let {
            SettingConfig().apply(it)
        }
    }

    /** Cached preference key — avoids recreating on every read/write */
    @PublishedApi
    internal var cachedKey: Preferences.Key<*>? = null

    /** Cached flow — avoids rebuilding the map/distinctUntilChanged chain every time */
    @PublishedApi
    internal var cachedFlow: Flow<T>? = null

    //Type erasure is annoying, W-we have to cast Pref<*> to its corresponding type
    //or else we can't call pref.SettingComposable() since it uses a reified generic parameter
    @Composable
    fun Render() {
        @Suppress("UNCHECKED_CAST")
        when (default) {
            is Boolean -> (this as Pref<Boolean>).SettingComposable()
            is Int -> (this as Pref<Int>).SettingComposable()
            is String -> (this as Pref<String>).SettingComposable()
            is Long -> (this as Pref<Long>).SettingComposable()
            is Float -> (this as Pref<Float>).SettingComposable()
            is Double -> (this as Pref<Double>).SettingComposable()
            is Set<*> -> (this as Pref<Set<*>>).SettingComposable()
            else -> throw IllegalArgumentException("Unsupported type for Pref Composable!!")
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
        Set::class -> stringSetPreferencesKey(name)
        Int::class -> intPreferencesKey(name)
        Double::class -> doublePreferencesKey(name)
        String::class -> stringPreferencesKey(name)
        Boolean::class -> booleanPreferencesKey(name)
        Float::class -> floatPreferencesKey(name)
        Long::class -> longPreferencesKey(name)
        ByteArray::class -> byteArrayPreferencesKey(name)
        else -> throw IllegalArgumentException("Unsupported type:prefKeyMapper!")
    } as Preferences.Key<T>
}


data class SettingConfig(
    var title: StringResource = Res.string.okay,
    var summary: StringResource = Res.string.okay,
    var summaryFormatArgs: Array<Any> = emptyArray(),
    var icon: ImageVector = Icons.Filled.Done,

    var dependencyEnable: () -> Boolean = { true },

    var extraConfig: PrefExtraConfig? = null
)
/**
 * Returns the cached [Preferences.Key] for this pref, creating it on first access.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> Pref<T>.prefKey(): Preferences.Key<T> {
    return (cachedKey as? Preferences.Key<T>) ?: prefKeyMapper<T>(key).also { cachedKey = it }
}

/**
 * Get the current value using the static default.
 */
inline fun <reified T> Pref<T>.value(): T {
    return datastoreStateFlow.value[prefKey()] ?: default
}

/**
 * Get a reactive Flow using the static default. The flow is cached so repeated calls
 * don't rebuild the map/distinctUntilChanged chain.
 */
inline fun <reified T> Pref<T>.flow(): Flow<T> {
    return cachedFlow ?: datastoreStateFlow
        .map { preferences -> preferences[prefKey()] ?: default }
        .distinctUntilChanged()
        .also { cachedFlow = it }
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
        preferences[prefKey()] = value
    }
}