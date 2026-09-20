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

    @Test
    fun a_slider_value_outside_its_range_is_skipped() {
        val raw = """{"version":1,"app":"Synkplay","values":{"pref_inroom_sync_rewind_threshold":"-2147483648"}}"""
        val (values, outcome) = readSettingsBackup(raw)
        assertTrue(values.isEmpty(), "a number outside its slider is not a setting the row could ever produce")
        assertEquals(1, outcome.skipped)
    }

    @Test
    fun an_unknown_choice_is_skipped() {
        // Writing this would leave the unpause gate matching none of its branches.
        val raw = """{"version":1,"app":"Synkplay","values":{"pref_unpause_action":"typo"}}"""
        val (values, outcome) = readSettingsBackup(raw)
        assertTrue(values.isEmpty())
        assertEquals(1, outcome.skipped)
    }

    @Test
    fun a_real_choice_still_comes_back() {
        val raw = """{"version":1,"app":"Synkplay","values":{"pref_unpause_action":"Always"}}"""
        val (values, outcome) = readSettingsBackup(raw)
        assertEquals(1, outcome.applied)
        assertEquals("Always", values.values.single())
    }

    @Test
    fun an_old_theme_marker_for_a_chat_colour_is_skipped() {
        // Earlier versions saved 0 on a chat colour reset. Written now, it would draw invisible text.
        val raw = """{"version":1,"app":"Synkplay","values":{"pref_inroom_color_selftag":"0","pref_inroom_color_friendtag":"-1"}}"""
        val (values, outcome) = readSettingsBackup(raw)
        assertEquals(1, outcome.skipped)
        assertEquals(listOf("pref_inroom_color_friendtag"), values.keys.map { it.key })
    }

    @Test
    fun engine_rows_and_nested_colours_are_exportable_but_actions_are_not() {
        val keys = exportableSettings().map { it.key }.toSet()
        assertTrue(Preferences.KITE_COMPOSE_RENDERER.key in keys, "an engine's own row is still a setting")
        assertTrue(Preferences.COLOR_TIMESTAMP.key in keys, "a colour behind a nested editor is still a setting")
        assertTrue(Preferences.EXPORT_SETTINGS.key !in keys, "a button is not a setting")
        assertTrue("misc_user_id" !in keys)
    }
}
