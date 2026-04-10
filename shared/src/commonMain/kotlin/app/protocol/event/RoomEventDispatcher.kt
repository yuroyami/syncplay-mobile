package app.protocol.event

import androidx.lifecycle.viewModelScope
import app.AbstractManager
import app.player.Playback
import app.preferences.Preferences
import app.preferences.Preferences.UNPAUSE_ACTION
import app.preferences.value
import app.room.RoomViewmodel
import app.room.models.Message
import app.utils.loggy
import app.utils.platformCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_set_as_ready
import kotlin.math.roundToLong

/**
 * Handles user-initiated actions and outbound protocol messages for playback control,
 * seeking, and chat. Counterpart to [RoomCallback]. All send operations are no-ops in solo mode.
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

    fun controlPlayback(playback: Playback, tellServer: Boolean) {
        //TODO if (viewmodel.uiState.isInBackground) return

        /* If this is a user-initiated play request, check readiness gating */
        if (playback == Playback.PLAY && tellServer && !viewmodel.isSoloMode
            && viewmodel.session.roomFeatures.supportsReadiness
        ) {
            if (!instaplayConditionsMet()) {
                /* Block the unpause — set as ready instead */
                loggy("SYNCPLAY Readiness: Conditions not met, setting as ready instead of unpausing")
                viewmodel.session.ready.value = true
                network.sendAsync<ClientMessage.Readiness> {
                    isReady = true
                    manuallyInitiated = true
                }
                broadcastMessage(isChat = false) { getString(Res.string.room_set_as_ready) }
                return
            }
        }

        onMainThread {
            when (playback) {
                Playback.PAUSE -> viewmodel.player.pause()
                Playback.PLAY -> viewmodel.player.play()
            }
        }

        platformCallback.onPlayback(!playback.play)

        if (viewmodel.isSoloMode || !tellServer) return

        this@RoomEventDispatcher.network.sendAsync<ClientMessage.State> {
            serverTime = null
            doSeek = null
            position = withContext(Dispatchers.Main.immediate) { viewmodel.player.currentPositionMs().div(1000.0).roundToLong() }
            changeState = 1
            this.play = playback.play
        }
    }

    /**
     * Checks whether the user is allowed to unpause based on the readiness unpause mode.
     * Mirrors the PC client's `instaplayConditionsMet()`.
     */
    private fun instaplayConditionsMet(): Boolean {
        val unpauseAction = UNPAUSE_ACTION.value()
        val session = viewmodel.session

        return when (unpauseAction) {
            "IfAlreadyReady" -> session.ready.value
            "IfOthersReady" -> session.ready.value || session.areAllOtherUsersReady()
            "IfMinUsersReady" -> {
                session.areAllOtherUsersReady() && session.usersInRoomCount() >= 2
            }
            "Always" -> true
            else -> true
        }
    }

    fun seekBckwd() = seekBy(-Preferences.SEEK_BACKWARD_JUMP.value())
    fun seekFrwrd() = seekBy(Preferences.SEEK_FORWARD_JUMP.value())

    private fun seekBy(deltaSeconds: Int) {
        viewmodel.player.playerScopeMain.launch {
            val currentMs = viewmodel.player.currentPositionMs()
            val dur = viewmodel.playerManager.timeFullMillis.value
            val newPos = (currentMs + deltaSeconds * 1000L).let {
                if (dur > 0L) it.coerceIn(0, dur) else it.coerceAtLeast(0)
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