package com.chromaticnoob.syncplay.room

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.preference.PreferenceManager
import com.chromaticnoob.syncplay.R
import com.chromaticnoob.syncplay.room.UIUtils.broadcastMessage
import com.chromaticnoob.syncplay.room.UIUtils.hideKb
import com.chromaticnoob.syncplayprotocol.JsonSender
import com.chromaticnoob.syncplayprotocol.SyncplayProtocol
import com.chromaticnoob.syncplayutils.SyncplayUtils

/** Wrapping functions that need no further coding here, to reduce code space in RoomActivity
 * for things that cannot be separated (like lifecycle methods, broadcaster overridden methods...etc).
 * This mostly concerns UI/Room Functionality. */

object RoomUtils {

    /** Updates the protocol with the current position of the video playback **/
    @JvmStatic
    fun RoomActivity.vidPosUpdater() {
        Handler(Looper.getMainLooper()).postDelayed({
            if (myExoPlayer?.isCurrentMediaItemSeekable == true) {
                /* Informing my ViewModel about current vid position so it is retrieved for networking after */
                runOnUiThread {
                    val progress = (roomBinding.vidplayer.player?.currentPosition?.div(1000.0))
                    if (progress != null) {
                        p.currentVideoPosition = progress
                    }
                }
            }
            vidPosUpdater()
        }, 100)
    }

    /** Launches a HandlerThread ato execute ping updating periodic task
     *
     * @see [pingUpdaterCore]**/
    @JvmStatic
    fun RoomActivity.pingUpdater() {
        try {
            if (!pingingThread.isAlive) {
                pingingThread.start()
            }
            pingUpdaterCore()
        } catch (e: IllegalThreadStateException) {
            pingUpdaterCore()
        }
    }

    /** Periodic task method with the help of a HandlerThread to execute ping commands every 1 sec
     * to update the ping which is used in syncplay's protocol and to show ping to the user UI */
    @JvmStatic
    fun RoomActivity.pingUpdaterCore() {
        Handler(pingingThread.looper).postDelayed({
            if (p.socket.isConnected && !p.socket.isClosed && !p.socket.isInputShutdown) {
                p.ping = SyncplayUtils.pingIcmp("151.80.32.178", 32) * 1000.0
                runOnUiThread {
                    roomBinding.syncplayConnectionInfo.text =
                        string(R.string.room_ping_connected, "${p.ping}")
                    when (p.ping) {
                        in (0.0..100.0) -> roomBinding.syncplaySignalIcon.setImageResource(R.drawable.ping_3)
                        in (100.0..200.0) -> roomBinding.syncplaySignalIcon.setImageResource(R.drawable.ping_2)
                        else -> roomBinding.syncplaySignalIcon.setImageResource(R.drawable.ping_1)
                    }
                }
            } else {
                runOnUiThread {
                    roomBinding.syncplayConnectionInfo.text =
                        string(R.string.room_ping_disconnected)
                    roomBinding.syncplaySignalIcon.setImageDrawable(
                        AppCompatResources.getDrawable(
                            this@pingUpdaterCore,
                            R.drawable.ic_unconnected
                        )
                    )
                }
                p.syncplayBroadcaster?.onDisconnected()
            }
            pingUpdater()
        }, 1000)
    }

    /** Sends a chat message to the server **/
    @JvmStatic
    fun RoomActivity.sendMessage(message: String) {
        hideKb()
        if (roomBinding.syncplayMESSAGERY.visibility != View.VISIBLE) {
            roomBinding.syncplayVisiblitydelegate.visibility = View.GONE
        }
        p.sendPacket(JsonSender.sendChat(message))
        roomBinding.syncplayINPUTBox.setText("")
    }

    /** Sends a play/pause playback to the server **/
    @JvmStatic
    fun RoomActivity.sendPlayback(play: Boolean) {
        val clienttime = System.currentTimeMillis() / 1000.0
        p.sendPacket(
            JsonSender.sendState(null, clienttime, null, 0, 1, play, p)
        )
    }

    /** Method to verify mismatches of files with different users in the room.
     * Mismatches are: Name, Size, Duration. If 3 mismatches are detected, no error is thrown
     * since that would mean that the two files are completely and obviously different.*/
    @JvmStatic
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
    @JvmStatic
    fun Context.string(id: Int, vararg stuff: String): String {
        return String.format(resources.getString(id), *stuff)
    }

}