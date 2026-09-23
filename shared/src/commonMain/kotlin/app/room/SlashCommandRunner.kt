package app.room

import app.i18n.Localization
import app.player.Playback
import app.protocol.WireMessage
import app.utils.loggy

/**
 * Runs a [SlashCommand]. Returns false when the input is ordinary chat text. Each command is a
 * shortcut to something that the app can already do from a panel.
 *
 * Every reply goes into chat as a local line and never to the server, so the room (the group of
 * people watching together) never sees a mistyped command.
 */
suspend fun RoomViewmodel.runSlashCommand(command: SlashCommand): Boolean {
    fun reply(isError: Boolean = false, text: suspend () -> String) =
        dispatcher.broadcastMessage(isChat = false, isError = isError, message = text)

    when (command) {
        SlashCommand.NotACommand -> return false

        is SlashCommand.Unknown -> reply(isError = true) {
            Localization.strings.roomCommandUnknown(command.name)
        }

        is SlashCommand.BadArgument -> reply(isError = true) {
            Localization.strings.roomCommandBadArgument(command.name, command.expected)
        }

        SlashCommand.Help -> reply {
            Localization.strings.roomCommandHelp(SLASH_COMMANDS.joinToString(", ") { "/$it" })
        }

        SlashCommand.ListUsers -> {
            val names = session.userList.value.joinToString(", ") { user ->
                user.name + if (user.readiness) " ✓" else ""
            }
            reply { Localization.strings.roomCommandUsers(names.ifEmpty { "-" }) }
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
            switchRoom(command.name)
        }

        is SlashCommand.Identify -> {
            // Kept so that a success can save the password for the re-identification on reconnect.
            session.lastControlPasswordAttempt = command.password
            networkManager.sendAsync(
                WireMessage.controllerAuth(room = session.currentRoom, password = command.password)
            )
        }

        is SlashCommand.Seek -> {
            if (media == null) {
                reply(isError = true) { Localization.strings.roomCommandNeedsMedia }
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
