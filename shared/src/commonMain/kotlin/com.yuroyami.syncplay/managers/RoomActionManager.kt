package com.yuroyami.syncplay.managers

import androidx.lifecycle.viewModelScope
import com.yuroyami.syncplay.AbstractManager
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys
import com.yuroyami.syncplay.managers.datastore.valueSuspendingly
import com.yuroyami.syncplay.managers.protocol.creator.PacketCreator
import com.yuroyami.syncplay.models.Message
import com.yuroyami.syncplay.utils.platformCallback
import com.yuroyami.syncplay.viewmodels.RoomViewmodel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong

class RoomActionManager(val viewmodel: RoomViewmodel) : AbstractManager(viewmodel) {

    val sender = viewmodel.networkManager

    fun sendPlayback(play: Boolean) {
        if (viewmodel.isSoloMode) return

        sender.sendAsync<PacketCreator.State> {
            serverTime = null
            doSeek = null
            position = withContext(Dispatchers.Main.immediate) { viewmodel.player.currentPositionMs().div(1000.0).roundToLong() }
            changeState = 1
            this.play = play
        }
    }

    fun sendSeek(newPosMs: Long) {
        if (viewmodel.isSoloMode) return

        sender.sendAsync<PacketCreator.State> {
            serverTime = null
            doSeek = true
            position = newPosMs / 1000L
            changeState = 1
            this.play = viewmodel.player.isPlaying() == true
        }
    }

    /** Sends a chat message to the server **/
    fun sendMessage(msg: String) {
        if (viewmodel.isSoloMode) return

        sender.sendAsync<PacketCreator.Chat> {
            message = msg
        }
    }

    /** This pauses playback on the main (necessary) thread **/
    fun pausePlayback() {
        if (viewmodel.lifecycleManager.isInBackground) return
        onMainThread {
            viewmodel.player.pause()
        }
        platformCallback.onPlayback(true)
    }

    /** This resumes playback on the main thread, and hides system UI **/
    fun playPlayback() {
        if (viewmodel.lifecycleManager.isInBackground) return
        onMainThread {
            viewmodel.player.play()
        }
        platformCallback.onPlayback(false)
    }

    fun seekBckwd() {
        viewmodel.player.playerScopeIO.launch {
            val dec = valueSuspendingly(DataStoreKeys.PREF_INROOM_PLAYER_SEEK_BACKWARD_JUMP, 10)

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

    //TODO Start with main dispatcher then switch
    fun seekFrwrd() {
        viewmodel.player.playerScopeIO.launch {
            val inc = valueSuspendingly(DataStoreKeys.PREF_INROOM_PLAYER_SEEK_FORWARD_JUMP, 10)

            val currentMs = withContext(Dispatchers.Main) { viewmodel.player.currentPositionMs() }
            val newPos = ((currentMs) + (inc * 1000L)).coerceIn(0, viewmodel.playerManager.media.value?.fileDuration?.toLong()?.times(1000L) ?: 0)
            sendSeek(newPos)
            viewmodel.player.seekTo(newPos)

            if (viewmodel.isSoloMode) {
                viewmodel.seeks.add(Pair((currentMs), newPos * 1000))
            }
        }
    }

    /** This broadcasts a message to show it in the chat section **/
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