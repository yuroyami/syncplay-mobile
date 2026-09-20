package app.preferences

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A chat colour saved as the old "use the theme's colour" marker falls back to chat's own default. */
class ChatColorCleanupTest {
    private val selfTag = intPreferencesKey(Preferences.COLOR_SELFTAG.key)
    private val friendTag = intPreferencesKey(Preferences.COLOR_FRIENDTAG.key)
    private val videoBackground = intPreferencesKey(Preferences.VIDEO_BACKGROUND_COLOR.key)
    private val unrelated = stringPreferencesKey("pref_unpause_action")

    @Test
    fun theOldMarkerIsRemovedAndEverythingElseStays() = runTest {
        val picked = 0xFF123456.toInt()
        val stored = preferencesOf(selfTag to 0, friendTag to picked, videoBackground to 0, unrelated to "Always")
        assertTrue(ChatColorCleanup.shouldMigrate(stored))

        val cleaned = ChatColorCleanup.migrate(stored)
        assertNull(cleaned[selfTag], "the old marker must give way to the chat default")
        assertEquals(picked, cleaned[friendTag], "a colour someone picked stays")
        assertEquals(0, cleaned[videoBackground], "only the six chat colours ever saved the marker")
        assertEquals("Always", cleaned[unrelated])
        assertFalse(ChatColorCleanup.shouldMigrate(cleaned))
    }

    @Test
    fun aStoreWithoutTheMarkerIsLeftAlone() = runTest {
        assertFalse(ChatColorCleanup.shouldMigrate(preferencesOf(friendTag to 0xFF123456.toInt())))
    }
}
