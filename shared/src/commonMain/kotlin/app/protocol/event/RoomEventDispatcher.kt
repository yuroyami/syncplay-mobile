package app.protocol.event

import androidx.lifecycle.viewModelScope
import app.AbstractManager
import app.preferences.Preferences.SEEK_BACKWARD_JUMP
import app.preferences.Preferences.SEEK_FORWARD_JUMP
import app.preferences.value
import app.protocol.models.ClientMessage
import app.room.RoomViewmodel
import app.room.models.Message
import app.utils.platformCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong

/**
 * Handles user-initiated actions and outbound protocol messages for playback control,
 * seeking, and chat. Counterpart to [RoomEventHandler]. All send operations are no-ops in solo mode.
 */
class RoomEventDispatcher(val viewmodel: RoomViewmodel) : AbstractManager(viewmodel) {
    val network = viewmodel.networkManager
    val session = viewmodel.session

    suspend fun sendHello() {
       network.send<ClientMessage.Hello> {
            username = session.currentUsername
            roomname = session.currentRoom
            serverPassword = session.currentPassword
        }
    }

    fun sendPlayback(play: Boolean) {
        if (viewmodel.isSoloMode) return

        this@RoomEventDispatcher.network.sendAsync<ClientMessage.State> {
            serverTime = null
            doSeek = null
            position = withContext(Dispatchers.Main.immediate) { viewmodel.player.currentPositionMs().div(1000.0).roundToLong() }
            changeState = 1
            this.play = play
        }
    }

    var pendingSeekFromMs: Long = 0L
    fun sendSeek(newPosMs: Long) {
        if (viewmodel.isSoloMode) return

        this@RoomEventDispatcher.network.sendAsync<ClientMessage.State> {
            serverTime = null
            doSeek = true
            position = newPosMs / 1000L
            changeState = 1
            this.play = viewmodel.player.isPlaying() == true
        }
    }

    fun sendMessage(msg: String) {
        if (viewmodel.isSoloMode) return
        this@RoomEventDispatcher.network.sendAsync<ClientMessage.Chat> { message = msg }
    }

    fun pausePlayback() {
        if (viewmodel.uiState.isInBackground) return
        onMainThread { viewmodel.player.pause() }
        platformCallback.onPlayback(true)
    }

    fun playPlayback() {
        if (viewmodel.uiState.isInBackground) return
        onMainThread { viewmodel.player.play() }
        platformCallback.onPlayback(false)
    }

    // TODO: Start with main dispatcher then switch
    fun seekBckwd() {
        viewmodel.player.playerScopeMain.launch {
            val currentMs = viewmodel.player.currentPositionMs()
            val dec = SEEK_BACKWARD_JUMP.value()
            var newPos = viewmodel.playerManager.timeFullMillis.value.let { dur ->
                if (dur == 0L) currentMs - (dec * 1000L) else (currentMs - (dec * 1000L)).coerceIn(0, dur)
            }
            if (newPos < 0) newPos = 0

            sendSeek(newPos)
            viewmodel.player.seekTo(newPos)
            if (viewmodel.isSoloMode) viewmodel.seeks.add(Pair(currentMs, newPos * 1000))
        }
    }

    fun seekFrwrd() {
        viewmodel.player.playerScopeMain.launch {
            val currentMs = viewmodel.player.currentPositionMs()
            val inc = SEEK_FORWARD_JUMP.value()
            val newPos = viewmodel.playerManager.timeFullMillis.value.let { dur ->
                if (dur == 0L) currentMs + (inc * 1000L) else (currentMs + (inc * 1000L)).coerceIn(0, dur)
            }

            sendSeek(newPos)
            viewmodel.player.seekTo(newPos)
            if (viewmodel.isSoloMode) viewmodel.seeks.add(Pair(currentMs, newPos * 1000))
        }
    }

    fun broadcastMessage(isChat: Boolean, chatter: String = "", isError: Boolean = false, message: suspend () -> String) {
        if (viewmodel.isSoloMode) return

        viewmodel.viewModelScope.launch {
            val msg = Message(
                sender = if (isChat) chatter else null,
                isMainUser = chatter == viewmodel.session.currentUsername,
                content = message.invoke(),
                isError = isError
            )
            withContext(Dispatchers.Main) {
                viewmodel.session.messageSequence.add(msg)
            }
        }
    }
}