package app.room

/**
 * Slash commands in the chat box.
 *
 * The desktop client has had these since forever and mobile has not, which means several things
 * the app can already do are reachable only by finding the right panel: setting readiness,
 * changing room, identifying as an operator, jumping to a timecode.
 *
 * The parser is deliberately dumb and total: anything it does not recognise is [Unknown], and
 * anything that does not start with a slash is [NotACommand] and gets sent as chat. It never
 * throws, because the caller is a text field.
 */
sealed interface SlashCommand {
    /** Ordinary text. Send it. */
    data object NotACommand : SlashCommand

    /** A slash followed by something we do not know. */
    data class Unknown(val name: String) : SlashCommand

    /** `/ready` and `/notready`. */
    data class SetReady(val ready: Boolean) : SlashCommand

    /** `/room <name>`. */
    data class JoinRoom(val name: String) : SlashCommand

    /** `/seek <timecode>` and `/seek +30` / `/seek -30`, in milliseconds. */
    data class Seek(val millis: Long, val relative: Boolean) : SlashCommand

    /** `/pause` and `/play`. */
    data class SetPaused(val paused: Boolean) : SlashCommand

    /** `/op <XX-000-000>`: identify as an operator in the current room. */
    data class Identify(val password: String) : SlashCommand

    /** `/users`: print the roster into chat, for a room whose panel is closed. */
    data object ListUsers : SlashCommand

    /** `/help`: the list of these. */
    data object Help : SlashCommand

    /** The command word was right but what followed it was not. */
    data class BadArgument(val name: String, val expected: String) : SlashCommand
}

/** Every command word, for the help text and for anyone adding one. */
val SLASH_COMMANDS: List<String> =
    listOf("ready", "notready", "room", "seek", "pause", "play", "op", "users", "help")

/**
 * Reads a chat draft.
 *
 * A leading `//` escapes: `//so` is the text `/so`, so a message that genuinely starts with a
 * slash is still sendable.
 */
fun parseSlashCommand(input: String): SlashCommand {
    val text = input.trim()
    if (!text.startsWith("/") || text.startsWith("//")) return SlashCommand.NotACommand

    val body = text.removePrefix("/")
    val name = body.substringBefore(' ').lowercase()
    val argument = body.substringAfter(' ', missingDelimiterValue = "").trim()

    return when (name) {
        "ready" -> SlashCommand.SetReady(true)
        "notready", "unready" -> SlashCommand.SetReady(false)
        "pause" -> SlashCommand.SetPaused(true)
        "play", "unpause" -> SlashCommand.SetPaused(false)
        "users", "list" -> SlashCommand.ListUsers
        "help", "commands" -> SlashCommand.Help
        "room" ->
            if (argument.isEmpty()) SlashCommand.BadArgument("room", "a room name")
            else SlashCommand.JoinRoom(argument)
        "op", "operator" ->
            if (OPERATOR_PASSWORD.matches(argument.uppercase())) SlashCommand.Identify(argument.uppercase())
            else SlashCommand.BadArgument("op", "a password shaped like AB-123-456")
        "seek" -> parseSeek(argument)
        else -> SlashCommand.Unknown(name)
    }
}

private val OPERATOR_PASSWORD = Regex("""[A-Z]{2}-\d{3}-\d{3}""")

/**
 * `mm:ss`, `hh:mm:ss` or plain seconds, optionally prefixed with `+` or `-` to move by that
 * much instead of jumping to it.
 */
private fun parseSeek(argument: String): SlashCommand {
    if (argument.isEmpty()) return SlashCommand.BadArgument("seek", "a time like 1:23:45, or +30")
    val relative = argument.startsWith("+") || argument.startsWith("-")
    val negative = argument.startsWith("-")
    val digits = argument.removePrefix("+").removePrefix("-")

    val parts = digits.split(":")
    if (parts.size > 3 || parts.any { it.isEmpty() || !it.all(Char::isDigit) }) {
        return SlashCommand.BadArgument("seek", "a time like 1:23:45, or +30")
    }
    val seconds = parts.fold(0L) { acc, part -> acc * 60 + part.toLong() }
    val millis = seconds * 1000
    return SlashCommand.Seek(if (negative) -millis else millis, relative)
}
