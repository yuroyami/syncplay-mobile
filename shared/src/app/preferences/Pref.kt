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
import app.i18n.AppStrings

class Pref<T>(
    val key: String,
    val default: T,
    settingConfigLambda: (SettingConfig.() -> Unit)? = null
) {
    init { PrefRegistry.register(this) }

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
 * Every [Pref] ever constructed.
 *
 * The settings export reads this registry, not the settings screens. Some preferences have rows
 * only in an engine's own category, or in a colour editor nested inside a row. A walk over the
 * screens would miss them, and they would silently stay out of the file.
 */
object PrefRegistry {
    private val all = mutableListOf<Pref<*>>()

    fun register(pref: Pref<*>) { all += pref }

    fun snapshot(): List<Pref<*>> = all.toList()
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


/** One line of text, taken from the strings of the current app language. */
typealias Localized = (AppStrings) -> String

data class SettingConfig(
    var title: Localized = { it.okay },
    var summary: Localized? = null,
    var icon: ImageVector = Icons.Filled.Done,

    var dependencyEnable: () -> Boolean = { true },

    var extraConfig: PrefExtraConfig? = null,

    /** The one line "what is this set to" for the value column. Null derives it from the value. */
    var stateSummary: (@Composable (Any?) -> String)? = null,

    /** Caveats and defaults, shown only in the editor, under the summary. */
    var detail: Localized? = null,
)
/**
 * Returns the cached [Preferences.Key] for this pref, creating it on first access.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> Pref<T>.prefKey(): Preferences.Key<T> {
    return (cachedKey as? Preferences.Key<T>) ?: prefKeyMapper<T>(key).also { cachedKey = it }
}

/**
 * The type-erased key, for rows that hold a `Pref<*>`. It is built from the runtime class of the
 * default, the same rule that [Pref.Render] picks the row kind by. A pref whose default is null
 * has no config and is never rendered, so it never reaches this.
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

/** Type-erased snapshot read. It falls back to the default. */
fun Pref<*>.valueAny(): Any? = datastoreStateFlow.value[anyKey] ?: default

/** Type-erased reactive read from the root snapshot, like [watchPref]. */
@Composable
fun Pref<*>.watchAny(): State<Any?> {
    val prefsState = LocalPrefsState.current
    val k = anyKey
    return remember(k) { derivedStateOf { prefsState.value[k] ?: default } }
}

/** Type-erased write. The caller passes the declared type, and nothing here checks it. */
suspend fun Pref<*>.setAny(value: Any) {
    datastore.edit { preferences -> preferences[anyKey] = value }
}

/** The current value, or the default when nothing is stored. */
inline fun <reified T> Pref<T>.value(): T {
    return datastoreStateFlow.value[prefKey()] ?: default
}

/**
 * A flow of the value, with the default when nothing is stored. The flow is cached, so repeated
 * calls do not rebuild the map and distinctUntilChanged chain.
 */
inline fun <reified T> Pref<T>.flow(): Flow<T> {
    return cachedFlow ?: datastoreStateFlow
        .map { preferences -> preferences[prefKey()] ?: default }
        .distinctUntilChanged()
        .also { cachedFlow = it }
}

/**
 * The value as Compose State. It reads the one root [LocalPrefsState] snapshot through
 * [derivedStateOf], so no composable collects a flow of its own and no stale default shows first.
 */
@Composable
inline fun <reified T> Pref<T>.watchPref(): State<T> {
    val prefsState = LocalPrefsState.current
    val k = prefKey()
    return remember(k) {
        derivedStateOf { prefsState.value[k] ?: default }
    }
}

/** Writes a new value to this preference. */
suspend inline fun <reified T> Pref<T>.set(value: T) {
    datastore.edit { preferences ->
        preferences[prefKey()] = value
    }
}
