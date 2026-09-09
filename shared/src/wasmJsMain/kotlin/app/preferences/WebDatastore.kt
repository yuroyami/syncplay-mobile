package app.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.utils.loggy
import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add

/** Where the whole preference store lives in the browser, as one key. */
private const val STORAGE_KEY = "synkplay.preferences"

/**
 * The preference store, kept in the browser's localStorage.
 *
 * DataStore's own factory wants a file path, and a page has no filesystem, so this implements the
 * two-method `DataStore` contract directly instead. localStorage rather than sessionStorage on
 * purpose: settings should survive closing the tab, which is the whole point of settings.
 *
 * Everything is held in memory and the whole store is rewritten on each change. The store is a
 * hundred small values read constantly and written rarely, so that costs nothing and removes any
 * question of partial writes.
 *
 * Two limits worth knowing. localStorage is per-origin and around 5 MB, which this will never
 * approach, and it is unavailable in a page with cookies blocked or in some private windows; that
 * case degrades to memory-only, so settings work for the session and do not survive it.
 */
class LocalStorageDataStore : DataStore<Preferences> {

    private val flow = MutableStateFlow(readFromStorage())
    private val writeLock = Mutex()

    override val data: Flow<Preferences> = flow.asStateFlow()

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        writeLock.withLock {
            val updated = transform(flow.value)
            flow.value = updated
            writeToStorage(updated)
            updated
        }

    private fun readFromStorage(): Preferences {
        val raw = runCatching { localStorage.getItem(STORAGE_KEY) }.getOrNull()
        if (raw.isNullOrBlank()) return mutablePreferencesOf()
        return runCatching { decode(raw) }.getOrElse { failure ->
            // Same policy as the file store's corruption handler: defaults beat a broken launch.
            preferencesLoadFailure = failure
            loggy("Stored preferences could not be read and were replaced with defaults: $failure")
            mutablePreferencesOf()
        }
    }

    private fun writeToStorage(preferences: Preferences) {
        runCatching { localStorage.setItem(STORAGE_KEY, encode(preferences)) }
            .onFailure { loggy("Preferences could not be saved to this browser: $it") }
    }
}

/**
 * One JSON object, each entry tagged with its type.
 *
 * The type has to be recorded because DataStore keys carry it and there is no way to recover it
 * from a bare JSON value: 1 could be an Int, a Long, a Float or a Double, and reading it back as
 * the wrong one throws on the first access.
 */
private fun encode(preferences: Preferences): String {
    val obj = buildJsonObject {
        preferences.asMap().forEach { (key, value) ->
            when (value) {
                is Boolean -> putJsonArray(key.name) { add("bool"); add(value) }
                is Int -> putJsonArray(key.name) { add("int"); add(value) }
                is Long -> putJsonArray(key.name) { add("long"); add(value.toString()) }
                is Float -> putJsonArray(key.name) { add("float"); add(value) }
                is Double -> putJsonArray(key.name) { add("double"); add(value) }
                is String -> putJsonArray(key.name) { add("string"); add(value) }
                is Set<*> -> putJsonArray(key.name) {
                    add("stringSet")
                    value.forEach { element -> add(element.toString()) }
                }
                else -> loggy("Preference ${key.name} has an unsupported type and was not saved.")
            }
        }
    }
    return obj.toString()
}

private fun decode(raw: String): Preferences {
    val out = mutablePreferencesOf()
    Json.parseToJsonElement(raw).jsonObject.forEach { (name, element) ->
        val parts = element.jsonArray
        val type = parts[0].jsonPrimitive.content
        when (type) {
            "bool" -> out[booleanPreferencesKey(name)] = parts[1].jsonPrimitive.content.toBoolean()
            "int" -> out[intPreferencesKey(name)] = parts[1].jsonPrimitive.content.toInt()
            "long" -> out[longPreferencesKey(name)] = parts[1].jsonPrimitive.content.toLong()
            "float" -> out[floatPreferencesKey(name)] = parts[1].jsonPrimitive.content.toFloat()
            "double" -> out[doublePreferencesKey(name)] = parts[1].jsonPrimitive.content.toDouble()
            "string" -> out[stringPreferencesKey(name)] = parts[1].jsonPrimitive.content
            "stringSet" -> out[stringSetPreferencesKey(name)] =
                parts.drop(1).map { it.jsonPrimitive.content }.toSet()
            else -> loggy("Stored preference $name has an unknown type tag and was skipped.")
        }
    }
    return out
}

/** Installs the browser store as the app's one datastore. Called before anything composes. */
fun initializeWebDatastore() {
    datastore = LocalStorageDataStore()
}
