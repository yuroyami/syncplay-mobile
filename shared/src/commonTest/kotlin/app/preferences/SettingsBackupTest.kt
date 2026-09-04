package app.preferences

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Settings in and out of a file. A settings file is a text file people mail to each other, so
 * two things matter: nothing private travels, and a hand-edited file cannot break the app.
 */
class SettingsBackupTest {

    @Test
    fun nothing_private_is_ever_exported() {
        val exported = exportableSettings().map { it.anyKey.name }.toSet()
        for (secret in listOf("misc_user_id", "misc_join_config", "misc_server_salt", "pref_server_password")) {
            assertTrue(secret !in exported, "$secret must not travel in a settings file")
        }
    }

    @Test
    fun a_file_from_a_newer_app_is_refused_rather_than_half_applied() {
        val raw = """{"version": 99, "app": "Synkplay", "values": {"pref_inroom_sync_rewind": "false"}}"""
        val (values, outcome) = readSettingsBackup(raw)
        assertTrue(values.isEmpty())
        assertNotNull(outcome.error)
    }

    @Test
    fun something_that_is_not_a_settings_file_is_refused_not_thrown() {
        for (junk in listOf("", "hello", "{", """{"values": 3}""")) {
            val (values, outcome) = readSettingsBackup(junk)
            assertTrue(values.isEmpty(), "junk: $junk")
            assertEquals("not a settings file", outcome.error)
        }
    }

    @Test
    fun a_key_the_app_no_longer_has_is_counted_and_skipped() {
        val raw = """{"version": 1, "app": "Synkplay", "values": {"a_setting_that_never_existed": "1"}}"""
        val (values, outcome) = readSettingsBackup(raw)
        assertTrue(values.isEmpty())
        assertEquals(1, outcome.skipped)
        assertNull(outcome.error)
    }

    @Test
    fun a_value_that_does_not_fit_its_setting_is_skipped_rather_than_guessed_at() {
        val raw = """{"version": 1, "app": "Synkplay", "values": {"pref_inroom_sync_rewind": "maybe"}}"""
        val (values, outcome) = readSettingsBackup(raw)
        assertTrue(values.isEmpty(), "a boolean setting must not accept 'maybe'")
        assertEquals(1, outcome.skipped)
    }

    @Test
    fun a_real_value_of_each_type_comes_back() {
        val raw = """
            {"version": 1, "app": "Synkplay", "values": {
              "pref_inroom_sync_rewind": "false",
              "pref_inroom_sync_rewind_threshold": "55",
              "pref_unpause_action": "Always"
            }}
        """.trimIndent()
        val (values, outcome) = readSettingsBackup(raw)
        assertEquals(3, outcome.applied)
        assertEquals(0, outcome.skipped)
        val byName = values.mapKeys { it.key.anyKey.name }
        assertEquals(false, byName["pref_inroom_sync_rewind"])
        assertEquals(55, byName["pref_inroom_sync_rewind_threshold"])
        assertEquals("Always", byName["pref_unpause_action"])
    }
}
