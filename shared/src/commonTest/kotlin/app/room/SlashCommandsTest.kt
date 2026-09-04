package app.room

import kotlin.test.Test
import kotlin.test.assertEquals

/** The chat box parser. It never throws, because what it reads is a text field. */
class SlashCommandsTest {

    @Test
    fun ordinary_text_is_left_alone() {
        assertEquals(SlashCommand.NotACommand, parseSlashCommand("hello everyone"))
        assertEquals(SlashCommand.NotACommand, parseSlashCommand(""))
        assertEquals(SlashCommand.NotACommand, parseSlashCommand("   "))
        assertEquals(SlashCommand.NotACommand, parseSlashCommand("and/or"))
    }

    @Test
    fun a_double_slash_escapes_so_a_message_can_start_with_one() {
        assertEquals(SlashCommand.NotACommand, parseSlashCommand("//not a command"))
    }

    @Test
    fun readiness_reads_both_ways_and_both_spellings() {
        assertEquals(SlashCommand.SetReady(true), parseSlashCommand("/ready"))
        assertEquals(SlashCommand.SetReady(false), parseSlashCommand("/notready"))
        assertEquals(SlashCommand.SetReady(false), parseSlashCommand("/unready"))
    }

    @Test
    fun playback_reads_both_ways() {
        assertEquals(SlashCommand.SetPaused(true), parseSlashCommand("/pause"))
        assertEquals(SlashCommand.SetPaused(false), parseSlashCommand("/play"))
        assertEquals(SlashCommand.SetPaused(false), parseSlashCommand("/unpause"))
    }

    @Test
    fun the_command_word_is_case_insensitive_but_a_room_name_is_not() {
        assertEquals(SlashCommand.SetReady(true), parseSlashCommand("/READY"))
        assertEquals(SlashCommand.JoinRoom("MovieNight"), parseSlashCommand("/room MovieNight"))
    }

    @Test
    fun a_room_name_keeps_its_spaces() {
        assertEquals(SlashCommand.JoinRoom("friday night"), parseSlashCommand("/room   friday night  "))
    }

    @Test
    fun a_room_with_no_name_says_what_it_wanted() {
        assertEquals(SlashCommand.BadArgument("room", "a room name"), parseSlashCommand("/room"))
    }

    @Test
    fun seek_reads_seconds_minutes_and_hours() {
        assertEquals(SlashCommand.Seek(90_000, relative = false), parseSlashCommand("/seek 90"))
        assertEquals(SlashCommand.Seek(90_000, relative = false), parseSlashCommand("/seek 1:30"))
        assertEquals(SlashCommand.Seek(5_025_000, relative = false), parseSlashCommand("/seek 1:23:45"))
    }

    @Test
    fun a_signed_seek_moves_by_that_much_instead_of_jumping_to_it() {
        assertEquals(SlashCommand.Seek(30_000, relative = true), parseSlashCommand("/seek +30"))
        assertEquals(SlashCommand.Seek(-30_000, relative = true), parseSlashCommand("/seek -30"))
        assertEquals(SlashCommand.Seek(-90_000, relative = true), parseSlashCommand("/seek -1:30"))
    }

    @Test
    fun a_seek_that_is_not_a_time_says_so_rather_than_throwing() {
        val expected = SlashCommand.BadArgument("seek", "a time like 1:23:45, or +30")
        assertEquals(expected, parseSlashCommand("/seek"))
        assertEquals(expected, parseSlashCommand("/seek soon"))
        assertEquals(expected, parseSlashCommand("/seek 1:2:3:4"))
        assertEquals(expected, parseSlashCommand("/seek 1::30"))
        assertEquals(expected, parseSlashCommand("/seek -"))
    }

    @Test
    fun an_operator_password_must_have_the_shape_the_server_issues() {
        assertEquals(SlashCommand.Identify("AB-123-456"), parseSlashCommand("/op ab-123-456"))
        assertEquals(
            SlashCommand.BadArgument("op", "a password shaped like AB-123-456"),
            parseSlashCommand("/op hunter2"),
        )
    }

    @Test
    fun an_unknown_command_is_named_so_the_reply_can_be_useful() {
        assertEquals(SlashCommand.Unknown("dance"), parseSlashCommand("/dance"))
    }

    @Test
    fun every_documented_command_parses_to_something_other_than_unknown() {
        for (name in SLASH_COMMANDS) {
            val parsed = parseSlashCommand("/$name x")
            assertEquals(false, parsed is SlashCommand.Unknown, "/$name is listed in help but not handled")
        }
    }
}
