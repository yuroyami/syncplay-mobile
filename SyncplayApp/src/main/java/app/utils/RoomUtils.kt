package app.utils

import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import app.R
import app.protocol.JsonSender
import app.protocol.SyncplayProtocol
import app.ui.activities.RoomActivity
import app.ui.activities.WatchActivity
import app.utils.MiscUtils.string
import app.wrappers.Message
import com.google.android.exoplayer2.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Methods exclusive to Room functionality (messages, sending data to server, etc) */
object RoomUtils {

    /** This broadcasts a message to show it in the message section **/
    fun WatchActivity.broadcastMessage(message: String, isChat: Boolean, chatter: String = "") {
        /** Messages are just a wrapper class for everything we need about a message
        So first, we initialize it, customize it, then add it to our long list of messages */
        val msg = Message()
        if (isChat) msg.sender = chatter
        msg.isMainUser = chatter == p.session.currentUsername
        msg.content = message

        /** Adding the message instance to our message sequence **/
        p.session.messageSequence.add(msg)
    }

    /** Sends a play/pause playback to the server **/
    fun WatchActivity.sendPlayback(play: Boolean) {
        if (isSoloMode()) return;
        p.sendPacket(
            JsonSender.sendState(
                servertime = null, clienttime = System.currentTimeMillis() / 1000.0,
                doSeek = null, seekPosition = 0, iChangeState = 1, play = play, protocol = p
            )
        )
    }

    fun WatchActivity.sendSeek(newpos: Long) {
        if (isSoloMode()) return;

        p.sendPacket(
            JsonSender.sendState(
                null, (System.currentTimeMillis() / 1000.0), true,
                newpos, 1,
                play = myExoPlayer.playWhenReady && myExoPlayer.playbackState == Player.STATE_READY,
                p
            )
        )
    }

    /** Sends a chat message to the server **/
    fun WatchActivity.sendMessage(message: String) {
        p.sendPacket(JsonSender.sendChat(message))
    }


    /** Periodic task method to execute ping commands every 1 sec
     * to update the ping which is used in syncplay's protocol and to show ping to the user UI */
    fun RoomActivity.pingUpdate() {
        lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                if (p.channel?.isActive == true) {
                    p.ping = MiscUtils.pingIcmp("151.80.32.178", 32) * 1000.0
                    runOnUiThread {
                        //binding.syncplayConnectionInfo.text = string(R.string.room_ping_connected, "${p.ping.roundToInt()}")
                        when (p.ping) {
                            in (0.0..100.0) -> {} //binding.syncplaySignalIcon.setImageResource(R.drawable.ping_3)
                            in (100.0..200.0) -> {} //binding.syncplaySignalIcon.setImageResource(R.drawable.ping_2)
                            else -> {} //binding.syncplaySignalIcon.setImageResource(R.drawable.ping_1)
                        }
                    }
                } else {
                    runOnUiThread {
                        //binding.syncplayConnectionInfo.text = string(R.string.room_ping_disconnected)
                        //binding.syncplaySignalIcon.setImageDrawable(AppCompatResources.getDrawable(this@pingUpdate, R.drawable.ic_unconnected))
                    }
                }
                delay(1000)
            }
        }
    }

    /** Method to verify mismatches of files with different users in the room.
     * Mismatches are: Name, Size, Duration. If 3 mismatches are detected, no error is thrown
     * since that would mean that the two files are completely and obviously different.*/
    fun WatchActivity.checkFileMismatches(p: SyncplayProtocol) {
        /** First, we check if user wanna be notified about file mismatchings */
        if (!PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("warn_file_mismatch", true)
        ) return

        for (user in p.session.userList) {
            val theirFile = user.file ?: continue /* If they have no file, iterate unto next */
            val nameMismatch =
                (media?.fileName != theirFile.fileName) && (media?.fileNameHashed != theirFile.fileName)
            val durationMismatch = media?.fileDuration != theirFile.fileDuration
            val sizeMismatch =
                media?.fileSize != theirFile.fileSize && media?.fileSizeHashed != theirFile.fileSize

            if (nameMismatch && durationMismatch && sizeMismatch) continue /* 2 mismatches or less */
            var warning = string(R.string.room_file_mismatch_warning_core, user.name)
            if (nameMismatch) warning =
                warning.plus(string(R.string.room_file_mismatch_warning_name))
            if (durationMismatch) warning =
                warning.plus(string(R.string.room_file_mismatch_warning_duration))
            if (sizeMismatch) warning =
                warning.plus(string(R.string.room_file_mismatch_warning_size))

            //broadcastMessage(warning, false)
        }
    }

}