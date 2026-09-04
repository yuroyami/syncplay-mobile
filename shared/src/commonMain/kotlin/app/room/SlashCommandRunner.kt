package app.room

import app.player.Playback
import app.protocol.WireMessage
import app.utils.loggy
import org.jetbrains.compose.resources.getString
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_command_bad_argument
import syncplaymobile.shared.generated.resources.room_command_help
import syncplaymobile.shared.generated.resources.room_command_needs_media
import syncplaymobile.shared.generated.resources.room_command_unknown
import syncplaymobile.shared.generated.resources.room_command_users

/**
 * Carries out a [SlashCommand]. Everything here already existed somewhere in the app; the
 * commands are a second way in for people who are already typing.
 *
 * Every reply goes into chat as a local line, never onto the wire, so a mistyped command is
 * invisible to the room.
 */
suspend fun RoomViewmodel.runSlashCommand(command: SlashCommand): Boolean {
    fun reply(isError: Boolean = false, text: suspend () -> String) =
        dispatcher.broadcastMessage(isChat = false, isError = isError, message = text)

    when (command) {
        SlashCommand.NotACommand -> return false

        is SlashCommand.Unknown -> reply(isError = true) {
            getString(Res.string.room_command_unknown, command.name)
        }

        is SlashCommand.BadArgument -> reply(isError = true) {
            getString(Res.string.room_command_bad_argument, command.name, command.expected)
        }

        SlashCommand.Help -> reply {
            getString(Res.string.room_command_help, SLASH_COMMANDS.joinToString(", ") { "/$it" })
        }

        SlashCommand.ListUsers -> {
            val names = session.userList.value.joinToString(", ") { user ->
                user.name + if (user.readiness) " ✓" else ""
            }
            reply { getString(Res.string.room_command_users, names.ifEmpty { "-" }) }
        }

        is SlashCommand.SetReady -> {
            session.ready.value = command.ready
            readiness.evaluate()
            networkManager.sendAsync(
                WireMessage.readiness(isReady = command.ready, manuallyInitiated = true)
            )
        }

        is SlashCommand.SetPaused ->
            dispatcher.controlPlayback(
                if (command.paused) Playback.PAUSE else Playback.PLAY,
                tellServer = true,
            )

        is SlashCommand.JoinRoom -> {
            loggy("Slash command: joining room ${command.name}")
            networkManager.sendAsync(WireMessage.roomChange(command.name))
            session.currentRoom = command.name
        }

        is SlashCommand.Identify ->
            networkManager.sendAsync(
                WireMessage.controllerAuth(room = session.currentRoom, password = command.password)
            )

        is SlashCommand.Seek -> {
            if (media == null) {
                reply(isError = true) { getString(Res.string.room_command_needs_media) }
            } else {
                val target =
                    if (command.relative) playerManager.estimatedPositionMs() + command.millis
                    else command.millis
                dispatcher.seek(target.coerceAtLeast(0L), fromMs = playerManager.estimatedPositionMs())
            }
        }
    }
    return true
}
