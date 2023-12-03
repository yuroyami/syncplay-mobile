package com.yuroyami.syncplay.utils

import androidx.compose.runtime.Composable
import com.yuroyami.syncplay.models.Message
import com.yuroyami.syncplay.protocol.JsonSender
import com.yuroyami.syncplay.protocol.SyncplayProtocol
import com.yuroyami.syncplay.watchroom.isSoloMode
import com.yuroyami.syncplay.watchroom.p
import com.yuroyami.syncplay.watchroom.player
import kotlinx.coroutines.launch

/** Methods exclusive to Room functionality (messages, sending data to server, etc) */
object RoomUtils {

    /** Sends a play/pause playback to the server **/
    fun sendPlayback(play: Boolean) {
        if (isSoloMode) return

        p.sendPacket(
            JsonSender.sendState(
                servertime = null, clienttime = generateTimestampMillis() / 1000.0,
                doSeek = null, seekPosition = 0, iChangeState = 1, play = play
            )
        )
    }

    fun sendSeek(newpos: Long) {
        if (isSoloMode) return

        player?.playerScopeMain?.launch {
            p.sendPacket(
                JsonSender.sendState(
                    null, (generateTimestampMillis() / 1000.0), true,
                    newpos, 1,
                    play = player?.isPlaying() == true,
                )
            )
        }
    }

    /** Sends a chat message to the server **/
    fun sendMessage(message: String) {
        if (isSoloMode) return

        p.sendPacket(JsonSender.sendChat(message))
    }

    /** This broadcasts a message to show it in the chat section **/
    fun broadcastMessage(message: String = "", messageComposite: (@Composable () -> String)? = null, isChat: Boolean, chatter: String = "") {
        if (isSoloMode) return

        /** Messages are just a wrapper class for everything we need about a message
        So first, we initialize it, customize it, then add it to our long list of messages */
        val msg = Message(
            sender = if (isChat) chatter else null,
            isMainUser = chatter == p.session.currentUsername,
            content = message,
            contentComposite = messageComposite
        )

        /** Adding the message instance to our message sequence **/
        p.session.messageSequence.add(msg)
    }

    /** TODO: Method to verify mismatches of files with different users in the room.
     * Mismatches are: Name, Size, Duration. If 3 mismatches are detected, no error is thrown
     * since that would mean that the two files are completely and obviously different.*/
    fun checkFileMismatches(p: SyncplayProtocol) {
        if (isSoloMode) return
        //TODO

        /** First, we check if user wanna be notified about file mismatchings */
        //FIXME: if (!PreferenceManager.getDefaultSharedPreferences(this)
        //        .getBoolean("warn_file_mismatch", true)
        //) return

       // for (user in p.session.userList) {
//            val theirFile = user.file ?: continue /* If they have no file, iterate unto next */
//            val nameMismatch =
//                (media?.fileName != theirFile.fileName) && (media?.fileNameHashed != theirFile.fileName)
//            val durationMismatch = media?.fileDuration != theirFile.fileDuration
//            val sizeMismatch =
//                media?.fileSize != theirFile.fileSize && media?.fileSizeHashed != theirFile.fileSize
//
//            if (nameMismatch && durationMismatch && sizeMismatch) continue /* 2 mismatches or less */
//            var warning = string(R.string.room_file_mismatch_warning_core, user.name)
//            if (nameMismatch) warning =
//                warning.plus(string(R.string.room_file_mismatch_warning_name))
//            if (durationMismatch) warning =
//                warning.plus(string(R.string.room_file_mismatch_warning_duration))
//            if (sizeMismatch) warning =
//                warning.plus(string(R.string.room_file_mismatch_warning_size))

            //broadcastMessage(warning, false)
       // }
    }

}