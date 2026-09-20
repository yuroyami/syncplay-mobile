package app.preferences

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.Preferences as StoredPreferences

/**
 * Earlier versions saved 0 when someone reset a chat colour, and 0 meant "use the theme's colour".
 * Chat colours no longer come from the theme, so a saved 0 would now draw invisible text. This
 * removes it before the first read of the store, and the chat default applies again.
 */
internal object ChatColorCleanup : DataMigration<StoredPreferences> {
    /** The old marker. It is fully transparent black, and nobody picks invisible chat text. */
    const val OLD_THEME_MARKER = 0

    /** The keys of the six chat colour settings. */
    val keyNames: Set<String>
        get() = with(Preferences) {
            listOf(COLOR_TIMESTAMP, COLOR_SELFTAG, COLOR_FRIENDTAG, COLOR_SYSTEMMSG, COLOR_USERMSG, COLOR_ERRORMSG)
        }.mapTo(mutableSetOf()) { it.key }

    override suspend fun shouldMigrate(currentData: StoredPreferences): Boolean =
        keyNames.any { currentData[intPreferencesKey(it)] == OLD_THEME_MARKER }

    override suspend fun migrate(currentData: StoredPreferences): StoredPreferences =
        currentData.toMutablePreferences().apply {
            keyNames.map(::intPreferencesKey).forEach { key -> if (this[key] == OLD_THEME_MARKER) remove(key) }
        }.toPreferences()

    override suspend fun cleanUp() = Unit
}
