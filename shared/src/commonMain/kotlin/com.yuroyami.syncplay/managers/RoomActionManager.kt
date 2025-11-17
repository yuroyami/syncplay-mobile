package com.yuroyami.syncplay.managers

import androidx.lifecycle.viewModelScope
import com.yuroyami.syncplay.AbstractManager
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys
import com.yuroyami.syncplay.managers.datastore.DatastoreManager.Companion.value
import com.yuroyami.syncplay.managers.protocol.creator.PacketOut
import com.yuroyami.syncplay.models.Message
import com.yuroyami.syncplay.utils.platformCallback
import com.yuroyami.syncplay.viewmodels.RoomViewmodel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong

/**
 * Manages user-initiated actions in the Syncplay room and sends them to the server.
 *
 * Handles outbound protocol messages for playback control, seeking, chat messages,
 * and local playback operations. This is the "sending actions" counterpart to
 * [OnRoomEventManager] which handles "receiving actions" from the server.
 *
 * All send operations are no-ops in solo mode.
 *
 * @property viewmodel The parent RoomViewModel that owns this manager
 */
class RoomActionManager(val viewmodel: RoomViewmodel) : AbstractManager(viewmodel) {

    /**
     * Quick access to the Network manager used to send protocol packets to the server.
     */
    val sender = viewmodel.networkManager

    /**
     * Sends a playback state change (play/pause) to the server.
     *
     * Includes current playback position in the packet. No-op in solo mode.
     *
     * @param play True to notify server of play state, false for pause state
     */
    fun sendPlayback(play: Boolean) {
        if (viewmodel.isSoloMode) return

        sender.sendAsync<PacketOut.State> {
            serverTime = null
            doSeek = null
            position = withContext(Dispatchers.Main.immediate) { viewmodel.player.currentPositionMs().div(1000.0).roundToLong() }
            changeState = 1
            this.play = play
        }
    }

    /**
     * Sends a seek operation to the server with the new playback position.
     *
     * Notifies other users in the room of the position change. No-op in solo mode.
     *
     * @param newPosMs The new playback position in milliseconds
     */
    fun sendSeek(newPosMs: Long) {
        if (viewmodel.isSoloMode) return

        sender.sendAsync<PacketOut.State> {
            serverTime = null
            doSeek = true
            position = newPosMs / 1000L
            changeState = 1
            this.play = viewmodel.player.isPlaying() == true
        }
    }

    /**
     * Sends a chat message to all users in the room.
     *
     * No-op in solo mode.
     *
     * @param msg The chat message text to send
     */
    fun sendMessage(msg: String) {
        if (viewmodel.isSoloMode) return

        sender.sendAsync<PacketOut.Chat> {
            message = msg
        }
    }

    /**
     * Pauses local playback and notifies the platform.
     *
     * Executes on the main thread as required by media player APIs.
     * No-op if the app is in the background.
     */
    fun pausePlayback() {
        if (viewmodel.lifecycleManager.isInBackground) return
        onMainThread {
            viewmodel.player.pause()
        }
        platformCallback.onPlayback(true)
    }

    /**
     * Resumes local playback and notifies the platform.
     *
     * Executes on the main thread and triggers system UI updates (notifications, etc.).
     * No-op if the app is in the background.
     */
    fun playPlayback() {
        if (viewmodel.lifecycleManager.isInBackground) return
        onMainThread {
            viewmodel.player.play()
        }
        platformCallback.onPlayback(false)
    }

    /**
     * Seeks backward by a user-configured amount.
     *
     * Retrieves the backward jump duration from preferences (default 10 seconds),
     * calculates the new position, sends it to the server, and performs the local seek.
     * In solo mode, records the seek operation in [RoomViewmodel.seeks].
     */
    fun seekBckwd() {
        viewmodel.player.playerScopeIO.launch {
            val dec = value(DataStoreKeys.PREF_INROOM_PLAYER_SEEK_BACKWARD_JUMP, 10)

            val currentMs = withContext(Dispatchers.Main) { viewmodel.player.currentPositionMs() }
            var newPos = ((currentMs) - (dec * 1000L)).coerceIn(0, viewmodel.playerManager.media.value?.fileDuration?.toLong()?.times(1000L) ?: 0)

            if (newPos < 0) newPos = 0

            sendSeek(newPos)
            viewmodel.player.seekTo(newPos)

            if (viewmodel.isSoloMode) {
                viewmodel.seeks.add(Pair(currentMs, newPos * 1000))
            }
        }
    }

    /**
     * Seeks forward by a user-configured amount.
     *
     * Retrieves the forward jump duration from preferences (default 10 seconds),
     * calculates the new position, sends it to the server, and performs the local seek.
     * In solo mode, records the seek operation in [RoomViewmodel.seeks].
     *
     * TODO: Start with main dispatcher then switch
     */
    fun seekFrwrd() {
        viewmodel.player.playerScopeIO.launch {
            val inc = value(DataStoreKeys.PREF_INROOM_PLAYER_SEEK_FORWARD_JUMP, 10)

            val currentMs = withContext(Dispatchers.Main) { viewmodel.player.currentPositionMs() }
            val newPos = ((currentMs) + (inc * 1000L)).coerceIn(0, viewmodel.playerManager.media.value?.fileDuration?.toLong()?.times(1000L) ?: 0)
            sendSeek(newPos)
            viewmodel.player.seekTo(newPos)

            if (viewmodel.isSoloMode) {
                viewmodel.seeks.add(Pair((currentMs), newPos * 1000))
            }
        }
    }

    /**
     * Displays a message in the chat section of the UI.
     *
     * Creates a [Message] object and adds it to the session's message sequence.
     * Messages can be chat messages from users or system notifications/errors.
     * No-op in solo mode.
     *
     * @param isChat True if this is a user chat message, false for system messages
     * @param chatter The username of the message sender (for chat messages)
     * @param isError True if this is an error message (displayed with error styling)
     * @param message Suspend function that returns the message content
     */
    fun broadcastMessage(isChat: Boolean, chatter: String = "", isError: Boolean = false, message: suspend () -> String) {
        if (viewmodel.isSoloMode) return

        viewmodel.viewModelScope.launch {
            /** Messages are just a wrapper class for everything we need about a message
            So first, we initialize it, customize it, then add it to our long list of messages */
            val msg = Message(
                sender = if (isChat) chatter else null,
                isMainUser = chatter == viewmodel.sessionManager.session.currentUsername,
                content = message.invoke(),
                isError = isError
            )

            /** Adding the message instance to our message sequence **/
            withContext(Dispatchers.Main) {
                viewmodel.sessionManager.session.messageSequence.add(msg)
            }
        }
    }
}