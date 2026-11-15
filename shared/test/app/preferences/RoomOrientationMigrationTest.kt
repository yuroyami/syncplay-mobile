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

/** The old "Follow device rotation" switch becomes a choice of the "Room orientation" setting (#249). */
class RoomOrientationMigrationTest {
    private val oldSwitch = booleanPreferencesKey(RoomOrientationMigration.OLD_KEY)
    private val orientation = stringPreferencesKey(Preferences.ROOM_ORIENTATION.key)
    private val unrelated = stringPreferencesKey("pref_unpause_action")

    @Test
    fun aSwitchThatWasOnBecomesAuto() = runTest {
        val stored = preferencesOf(oldSwitch to true, unrelated to "Always")
        assertTrue(RoomOrientationMigration.shouldMigrate(stored))

        val migrated = RoomOrientationMigration.migrate(stored)
        assertEquals(RoomOrientation.Auto.name, migrated[orientation])
        assertNull(migrated[oldSwitch], "the old key is removed")
        assertEquals("Always", migrated[unrelated])
        assertFalse(RoomOrientationMigration.shouldMigrate(migrated))
    }

    @Test
    fun aSwitchThatWasOffLeavesTheDefault() = runTest {
        val migrated = RoomOrientationMigration.migrate(preferencesOf(oldSwitch to false))
        assertNull(migrated[orientation], "landscape is the default, so nothing is stored")
        assertNull(migrated[oldSwitch])
    }

    @Test
    fun aChoiceThatIsAlreadyStoredWins() = runTest {
        val stored = preferencesOf(oldSwitch to true, orientation to RoomOrientation.Portrait.name)
        assertEquals(RoomOrientation.Portrait.name, RoomOrientationMigration.migrate(stored)[orientation])
    }

    @Test
    fun aStoreWithoutTheOldSwitchIsLeftAlone() = runTest {
        assertFalse(RoomOrientationMigration.shouldMigrate(preferencesOf(unrelated to "Always")))
    }
}
