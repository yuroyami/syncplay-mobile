package app.protocol

import app.utils.LogRedactor
import app.utils.LogRedactor.Kind

/**
 * Registers the user names, room names and file names that [this] message carries with
 * [LogRedactor], before any handler can log them. Both the client and the hosted server call it
 * on each line they decode.
 */
fun WireMessage.registerLogIdentifiers() {
    fun user(value: String?) = LogRedactor.register(Kind.User, value)
    fun room(value: String?) = LogRedactor.register(Kind.Room, value)
    fun file(value: String?) = LogRedactor.register(Kind.File, value)
    when (this) {
        is WireMessage.Hello -> {
            user(data.username)
            room(data.room?.name)
        }
        is WireMessage.Set -> {
            data.user?.forEach { (name, set) ->
                user(name)
                room(set.room?.name)
                file(set.file?.name)
            }
            room(data.room?.name)
            file(data.file?.name)
            data.ready?.let { user(it.username); user(it.setBy) }
            data.controllerAuth?.let { user(it.user); room(it.room) }
            data.newControlledRoom?.let { room(it.roomName) }
            data.playlistIndex?.let { user(it.user) }
            data.playlistChange?.let { change ->
                user(change.user)
                change.files?.forEach(::file)
            }
        }
        is WireMessage.ListResponse -> rooms.forEach { (name, users) ->
            room(name)
            users.forEach { (userName, entry) ->
                user(userName)
                file(entry.file?.name)
            }
        }
        is WireMessage.State -> user(data.playstate?.setBy)
        is WireMessage.ChatBroadcast -> user(data.username)
        else -> Unit
    }
}
