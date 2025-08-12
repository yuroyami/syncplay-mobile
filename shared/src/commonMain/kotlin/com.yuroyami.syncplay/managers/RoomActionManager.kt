package com.yuroyami.syncplay.managers

import androidx.lifecycle.viewModelScope
import com.yuroyami.syncplay.models.Message
import com.yuroyami.syncplay.protocol.sending.Packet
import com.yuroyami.syncplay.logic.managers.datastore.DataStoreKeys
import com.yuroyami.syncplay.logic.managers.datastore.valueSuspendingly
import com.yuroyami.syncplay.utils.platformCallback
import com.yuroyami.syncplay.logic.AbstractManager
import com.yuroyami.syncplay.logic.SyncplayViewmodel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RoomActionManager(viewmodel: SyncplayViewmodel): AbstractManager(viewmodel) {

    val p = viewmodel.p

    fun sendPlayback(play: Boolean) {
        if (viewmodel.isSoloMode) return

        p.send<Packet.State> {
            serverTime = null
            doSeek = null
            position = null //FIXME 0
            changeState = 1
            this.play = play
        }
    }

    fun sendSeek(newPosMs: Long) {
        if (viewmodel.isSoloMode) return

        viewmodel.player?.playerScopeMain?.launch {
            p.send<Packet.State> {
                serverTime = null
                doSeek = true
                position = newPosMs / 1000L
                changeState = 1
                this.play = viewmodel.player?.isPlaying() == true
            }
        }
    }

    /** Sends a chat message to the server **/
    fun sendMessage(msg: String) {
        if (viewmodel.isSoloMode) return

        p.send<Packet.Chat> {
            message = msg
        }
    }

    /** This pauses playback on the main (necessary) thread **/
    fun pausePlayback() {
        if (background == true) return
        player?.pause()
        platformCallback.onPlayback(true)
    }

    /** This resumes playback on the main thread, and hides system UI **/
    fun playPlayback() {
        if (background == true) return
        player?.play()
        platformCallback.onPlayback(false)
    }

    fun seekBckwd() {
        player?.playerScopeIO?.launch {
            val dec = valueSuspendingly(DataStoreKeys.PREF_INROOM_PLAYER_SEEK_BACKWARD_JUMP, 10)

            val currentMs =
                withContext(Dispatchers.Main) { player!!.currentPositionMs() }
            var newPos = ((currentMs) - (dec * 1000L)).coerceIn(
                0, media?.fileDuration?.toLong()?.times(1000L) ?: 0
            )

            if (newPos < 0) {
                newPos = 0
            }

            sendSeek(newPos)
            player?.seekTo(newPos)

            if (isSoloMode) {
                seeks.add(Pair(currentMs, newPos * 1000))
            }
        }
    }

    fun seekFrwrd() {
        player?.playerScopeIO?.launch {
            val inc = valueSuspendingly(DataStoreKeys.PREF_INROOM_PLAYER_SEEK_FORWARD_JUMP, 10)

            val currentMs =
                withContext(Dispatchers.Main) { player!!.currentPositionMs() }
            val newPos = ((currentMs) + (inc * 1000L)).coerceIn(
                0,
                media?.fileDuration?.toLong()?.times(1000L) ?: 0
            )

            sendSeek(newPos)
            player?.seekTo(newPos)

            if (isSoloMode) {
                seeks.add(Pair((currentMs), newPos * 1000))
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
                isMainUser = chatter == p.session.currentUsername,
                content = message.invoke(),
                isError = isError
            )

            /** Adding the message instance to our message sequence **/
            withContext(Dispatchers.Main) {
                p.session.messageSequence.add(msg)
            }
        }
    }

}