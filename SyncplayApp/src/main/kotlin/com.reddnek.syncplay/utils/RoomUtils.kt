package com.reddnek.syncplay.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.reddnek.syncplay.R
import com.reddnek.syncplay.controllers.activity.RoomActivity
import com.reddnek.syncplay.protocol.JsonSender
import com.reddnek.syncplay.protocol.SyncplayProtocol
import com.reddnek.syncplay.utils.UIUtils.broadcastMessage
import com.reddnek.syncplay.utils.UIUtils.hideKb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Wrapping functions that need no further coding here, to reduce code space in RoomActivity
 * for things that cannot be separated (like lifecycle methods, broadcaster overridden methods...etc).
 * This mostly concerns UI/Room Functionality. */

object RoomUtils {

    /** Updates the protocol with the current position of the video playback **/
    fun RoomActivity.vidPosUpdater() {
        Handler(Looper.getMainLooper()).postDelayed({
            if (myExoPlayer?.isCurrentMediaItemSeekable == true) {
                /* Informing my ViewModel about current vid position so it is retrieved for networking after */
                runOnUiThread {
                    val progress = (binding.vidplayer.player?.currentPosition?.div(1000.0))
                    if (progress != null) {
                        p.currentVideoPosition = progress
                    }
                }
            }
            vidPosUpdater()
        }, 100)
    }


    /** Periodic task method to execute ping commands every 1 sec
     * to update the ping which is used in syncplay's protocol and to show ping to the user UI */
    fun RoomActivity.pingUpdate() {
        lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                if (p.socket.isConnected && !p.socket.isClosed && !p.socket.isInputShutdown) {
                    p.ping = SyncplayUtils.pingIcmp("151.80.32.178", 32) * 1000.0
                    runOnUiThread {
                        binding.syncplayConnectionInfo.text =
                            string(R.string.room_ping_connected, "${p.ping.roundToInt()}")
                        when (p.ping) {
                            in (0.0..100.0) -> binding.syncplaySignalIcon.setImageResource(R.drawable.ping_3)
                            in (100.0..200.0) -> binding.syncplaySignalIcon.setImageResource(R.drawable.ping_2)
                            else -> binding.syncplaySignalIcon.setImageResource(R.drawable.ping_1)
                        }
                    }
                } else {
                    runOnUiThread {
                        binding.syncplayConnectionInfo.text =
                            string(R.string.room_ping_disconnected)
                        binding.syncplaySignalIcon.setImageDrawable(
                            AppCompatResources.getDrawable(
                                this@pingUpdate,
                                R.drawable.ic_unconnected
                            )
                        )
                    }
                    if (p.connected) {
                        p.syncplayBroadcaster?.onDisconnected()
                    }
                }
                delay(1000)
            }
        }
    }

    /** Sends a chat message to the server **/
    fun RoomActivity.sendMessage(message: String) {
        hideKb()
        if (binding.syncplayMESSAGERY.visibility != View.VISIBLE) {
            binding.syncplayVisiblitydelegate.visibility = View.GONE
        }
        p.sendPacket(JsonSender.sendChat(message))
        binding.syncplayINPUTBox.setText("")
    }

    /** Sends a play/pause playback to the server **/
    fun RoomActivity.sendPlayback(play: Boolean) {
        val clienttime = System.currentTimeMillis() / 1000.0
        p.sendPacket(
            JsonSender.sendState(null, clienttime, null, 0, 1, play, p)
        )
    }

    /** Method to verify mismatches of files with different users in the room.
     * Mismatches are: Name, Size, Duration. If 3 mismatches are detected, no error is thrown
     * since that would mean that the two files are completely and obviously different.*/
    fun RoomActivity.checkFileMismatches(p: SyncplayProtocol) {
        /** First, we check if user wanna be notified about file mismatchings */
        if (!PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("warn_file_mismatch", true)
        ) return /* No need to continue if option is off */
        val myFile = p.file
        for (user in p.session.userList) {
            val theirFile = user.file ?: continue /* If they have no file, iterate unto next */
            val nameMismatch =
                (myFile?.fileName != theirFile.fileName) && (myFile?.fileNameHashed != theirFile.fileName)
            val durationMismatch = myFile?.fileDuration != theirFile.fileDuration
            val sizeMismatch =
                myFile?.fileSize != theirFile.fileSize && myFile?.fileSizeHashed != theirFile.fileSize

            if (nameMismatch && durationMismatch && sizeMismatch) continue /* 2 mismatches or less */
            var warning = string(R.string.room_file_mismatch_warning_core, user.name)
            if (nameMismatch) warning =
                warning.plus(string(R.string.room_file_mismatch_warning_name))
            if (durationMismatch) warning =
                warning.plus(string(R.string.room_file_mismatch_warning_duration))
            if (sizeMismatch) warning =
                warning.plus(string(R.string.room_file_mismatch_warning_size))

            broadcastMessage(warning, false)
        }
    }

    /** Functions to grab a localized string from resources, format it according to arguments **/
    fun Context.string(id: Int, vararg stuff: String): String {
        return String.format(resources.getString(id), *stuff)
    }

}