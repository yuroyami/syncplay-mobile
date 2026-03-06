package app.room.event

import androidx.lifecycle.viewModelScope
import app.AbstractManager
import app.room.RoomViewmodel
import app.room.models.Message
import app.utils.platformCallback
import app.utils.timestampFromMillis
import app.preferences.Preferences.SEEK_BACKWARD_JUMP
import app.preferences.Preferences.SEEK_FORWARD_JUMP
import app.preferences.value
import app.protocol.models.ClientMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_seeked
import kotlin.math.roundToLong

/**
 * Handles user-initiated actions and outbound protocol messages for playback control,
 * seeking, and chat. Counterpart to [RoomEventHandler]. All send operations are no-ops in solo mode.
 */
class RoomEventDispatcher(val viewmodel: RoomViewmodel) : AbstractManager(viewmodel) {

    val sender = viewmodel.networkManager

    fun sendPlayback(play: Boolean) {
        if (viewmodel.isSoloMode) return

        sender.sendAsync<ClientMessage.State> {
            serverTime = null
            doSeek = null
            position = withContext(Dispatchers.Main.immediate) { viewmodel.player.currentPositionMs().div(1000.0).roundToLong() }
            changeState = 1
            this.play = play
        }
    }

    fun sendSeek(newPosMs: Long, oldPosms: Long) {
        if (viewmodel.isSoloMode) return

        viewmodel.roomOut.broadcastMessage(
            message = { getString(Res.string.room_seeked, viewmodel.session.currentUsername, timestampFromMillis(oldPosms), timestampFromMillis(newPosMs)) },
            isChat = false
        )

        sender.sendAsync<ClientMessage.State> {
            serverTime = null
            doSeek = true
            position = newPosMs / 1000L
            changeState = 1
            this.play = viewmodel.player.isPlaying() == true
        }
    }

    fun sendMessage(msg: String) {
        if (viewmodel.isSoloMode) return
        sender.sendAsync<ClientMessage.Chat> { message = msg }
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
            var newPos = viewmodel.videoEngineManager.timeFullMillis.value.let { dur ->
                if (dur == 0L) currentMs - (dec * 1000L) else (currentMs - (dec * 1000L)).coerceIn(0, dur)
            }
            if (newPos < 0) newPos = 0

            sendSeek(newPos, currentMs)
            viewmodel.player.seekTo(newPos)
            if (viewmodel.isSoloMode) viewmodel.seeks.add(Pair(currentMs, newPos * 1000))
        }
    }

    fun seekFrwrd() {
        viewmodel.player.playerScopeMain.launch {
            val currentMs = viewmodel.player.currentPositionMs()
            val inc = SEEK_FORWARD_JUMP.value()
            val newPos = viewmodel.videoEngineManager.timeFullMillis.value.let { dur ->
                if (dur == 0L) currentMs + (inc * 1000L) else (currentMs + (inc * 1000L)).coerceIn(0, dur)
            }

            sendSeek(newPos, currentMs)
            viewmodel.player.seekTo(newPos)
            if (viewmodel.isSoloMode) viewmodel.seeks.add(Pair(currentMs, newPos * 1000))
        }
    }

    fun broadcastMessage(isChat: Boolean, chatter: String = "", isError: Boolean = false, message: suspend () -> String) {
        if (viewmodel.isSoloMode) return

        viewmodel.viewModelScope.launch {
            val msg = Message(
                sender = if (isChat) chatter else null,
                isMainUser = chatter == viewmodel.sessionManager.session.currentUsername,
                content = message.invoke(),
                isError = isError
            )
            withContext(Dispatchers.Main) {
                viewmodel.sessionManager.session.messageSequence.add(msg)
            }
        }
    }
}