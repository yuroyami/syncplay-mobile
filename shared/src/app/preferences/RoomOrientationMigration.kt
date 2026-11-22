package app.preferences

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.Preferences as StoredPreferences

/**
 * A store migration that removes the old "Follow device rotation" switch (#249). The room now opens
 * in the view that its full-screen key stored last, so the switch has no meaning. The migration
 * writes nothing else.
 */
internal object RoomOrientationMigration : DataMigration<StoredPreferences> {
    /** The key of the old switch. */
    const val OLD_KEY = "pref_room_allow_portrait"

    private val oldSwitch = booleanPreferencesKey(OLD_KEY)

    override suspend fun shouldMigrate(currentData: StoredPreferences): Boolean = oldSwitch in currentData

    override suspend fun migrate(currentData: StoredPreferences): StoredPreferences =
        currentData.toMutablePreferences().apply { remove(oldSwitch) }.toPreferences()

    override suspend fun cleanUp() = Unit
}
