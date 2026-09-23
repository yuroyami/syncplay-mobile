package app.room

/**
 * A command typed in the chat box, such as `/ready` or `/seek 1:30`. The desktop Syncplay client
 * accepts similar commands. They reach actions that otherwise need the right panel: readiness,
 * changing room, identifying as an operator, seeking to a time. A room is the group of people
 * watching together.
 *
 * The parser is simple and total. Input that it does not recognize is [Unknown], and input that
 * does not start with a slash is [NotACommand] and goes out as chat. The parser never throws,
 * because its caller is a text field.
 */
sealed interface SlashCommand {
    /** Ordinary text. Send it. */
    data object NotACommand : SlashCommand

    /** A slash followed by a command word that the parser does not know. */
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

    /**
     * `/users`: print the roster (the list of users in the room) into chat, when its panel is
     * closed.
     */
    data object ListUsers : SlashCommand

    /** `/help`: print the list of commands. */
    data object Help : SlashCommand

    /** The command word was right but what followed it was not. */
    data class BadArgument(val name: String, val expected: String) : SlashCommand
}

/** Every command word, for the help text. A new command must be listed here too. */
val SLASH_COMMANDS: List<String> =
    listOf("ready", "notready", "room", "seek", "pause", "play", "op", "users", "help")

/**
 * Parses a chat draft into a [SlashCommand].
 *
 * A draft that starts with `//` is [SlashCommand.NotACommand], so it goes out as chat. The chat
 * box sends it as typed, with both slashes.
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
 * Parses a seek argument: `mm:ss`, `hh:mm:ss` or plain seconds. A leading `+` or `-` moves by
 * that amount instead of jumping to it.
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
