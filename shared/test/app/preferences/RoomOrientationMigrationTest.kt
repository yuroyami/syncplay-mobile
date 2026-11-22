package app.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.room.RoomOrientation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The migration removes the old "Follow device rotation" switch and writes nothing else (#249). */
class RoomOrientationMigrationTest {
    private val oldSwitch = booleanPreferencesKey(RoomOrientationMigration.OLD_KEY)
    private val orientation = stringPreferencesKey(Preferences.ROOM_ORIENTATION.key)
    private val unrelated = stringPreferencesKey("pref_unpause_action")

    @Test
    fun theOldSwitchIsRemovedAndNoViewIsWritten() = runTest {
        for (on in listOf(true, false)) {
            val stored = preferencesOf(oldSwitch to on, unrelated to "Always")
            assertTrue(RoomOrientationMigration.shouldMigrate(stored))

            val migrated = RoomOrientationMigration.migrate(stored)
            assertNull(migrated[oldSwitch], "The old key is removed (switch on: $on)")
            assertNull(migrated[orientation], "Landscape is the default, so nothing is stored (switch on: $on)")
            assertEquals("Always", migrated[unrelated])
            assertFalse(RoomOrientationMigration.shouldMigrate(migrated))
        }
    }

    @Test
    fun aStoredViewStays() = runTest {
        for (view in listOf(RoomOrientation.Portrait.name, "Auto")) {
            val stored = preferencesOf(oldSwitch to true, orientation to view)
            assertEquals(view, RoomOrientationMigration.migrate(stored)[orientation], "The stored value $view stays as it is")
        }
    }

    @Test
    fun aStoreWithoutTheOldSwitchIsLeftAlone() = runTest {
        assertFalse(RoomOrientationMigration.shouldMigrate(preferencesOf(unrelated to "Always")))
    }
}
