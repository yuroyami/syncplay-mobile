package app.preferences

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.room.RoomOrientation
import androidx.datastore.preferences.core.Preferences as StoredPreferences

/**
 * A store migration from the old "Follow device rotation" switch to the "Room orientation"
 * setting (#249). A switch that was on becomes Auto. A switch that was off leaves the default,
 * Landscape. The old key is removed in both cases.
 */
internal object RoomOrientationMigration : DataMigration<StoredPreferences> {
    /** The key of the old switch. */
    const val OLD_KEY = "pref_room_allow_portrait"

    private val oldSwitch = booleanPreferencesKey(OLD_KEY)

    override suspend fun shouldMigrate(currentData: StoredPreferences): Boolean = oldSwitch in currentData

    override suspend fun migrate(currentData: StoredPreferences): StoredPreferences =
        currentData.toMutablePreferences().apply {
            val orientation = stringPreferencesKey(Preferences.ROOM_ORIENTATION.key)
            // A choice that is already stored is newer than the switch, so it wins.
            if (this[oldSwitch] == true && orientation !in this) this[orientation] = RoomOrientation.Auto.name
            remove(oldSwitch)
        }.toPreferences()

    override suspend fun cleanUp() = Unit
}
