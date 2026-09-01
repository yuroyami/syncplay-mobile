package app.preferences

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
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
import app.preferences.settings.Render
import app.preferences.settings.SettingEntry
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

    /** Cached typed key, built lazily on first read/write. */
    @PublishedApi
    internal var cachedKey: Preferences.Key<*>? = null

    /** Cached flow so the map/distinctUntilChanged chain is built only once. */
    @PublishedApi
    internal var cachedFlow: Flow<T>? = null

    /** Renders this pref as a settings row with its declared control. */
    @Composable
    fun Render() {
        SettingEntry(this).Render()
    }
}

/**
 * Builds the typed [Preferences.Key] for [name] from the reified element type. Supports Boolean,
 * Int, Long, Float, Double, String, Set<String> and ByteArray; throws on any other type.
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

    var extraConfig: PrefExtraConfig? = null,

    /** The one line "what is this set to" for the value column. Null derives it from the value. */
    var stateSummary: (@Composable (Any?) -> String)? = null,

    /** Caveats and defaults, shown only in the editor, under the summary. */
    var detail: StringResource? = null,
)
/**
 * Returns the cached [Preferences.Key] for this pref, creating it on first access.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> Pref<T>.prefKey(): Preferences.Key<T> {
    return (cachedKey as? Preferences.Key<T>) ?: prefKeyMapper<T>(key).also { cachedKey = it }
}

/**
 * The type-erased key, for rows that hold a `Pref<*>`. Built from the default's runtime class,
 * the same rule [Pref.Render] dispatches on. A pref whose default is null has no config and is
 * never rendered, so it never reaches this.
 */
@Suppress("UNCHECKED_CAST")
val Pref<*>.anyKey: Preferences.Key<Any>
    get() = (cachedKey ?: when (default) {
        is Boolean -> booleanPreferencesKey(key)
        is Int -> intPreferencesKey(key)
        is Long -> longPreferencesKey(key)
        is Float -> floatPreferencesKey(key)
        is Double -> doublePreferencesKey(key)
        is String -> stringPreferencesKey(key)
        is Set<*> -> stringSetPreferencesKey(key)
        else -> throw IllegalArgumentException("Unsupported pref type for $key")
    }.also { cachedKey = it }) as Preferences.Key<Any>

/** Type-erased snapshot read; falls back to the default. */
fun Pref<*>.valueAny(): Any? = datastoreStateFlow.value[anyKey] ?: default

/** Type-erased reactive read from the root snapshot, like [watchPref]. */
@Composable
fun Pref<*>.watchAny(): State<Any?> {
    val prefsState = LocalPrefsState.current
    val k = anyKey
    return remember(k) { derivedStateOf { prefsState.value[k] ?: default } }
}

/** Type-erased write. The caller passes the declared type; nothing here checks it. */
suspend fun Pref<*>.setAny(value: Any) {
    datastore.edit { preferences -> preferences[anyKey] = value }
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
 * Observe as Compose State. Reads from the single root-level [LocalPrefsState] snapshot
 * via [derivedStateOf], so there is no per-composable flow collection and no stale initial default.
 */
@Composable
inline fun <reified T> Pref<T>.watchPref(): State<T> {
    val prefsState = LocalPrefsState.current
    val k = prefKey()
    return remember(k) {
        derivedStateOf { prefsState.value[k] ?: default }
    }
}

/**
 * Write a new value to this preference.
 */
suspend inline fun <reified T> Pref<T>.set(value: T) {
    datastore.edit { preferences ->
        preferences[prefKey()] = value
    }
}
