package app.protocol.event

import androidx.lifecycle.viewModelScope
import app.AbstractManager
import app.player.Playback
import app.preferences.Preferences
import app.preferences.Preferences.UNPAUSE_ACTION
import app.preferences.value
import app.protocol.ProtocolManager.Companion.SYNCPLAY_PROTOCOL_VERSION
import app.protocol.WireMessage
import app.protocol.models.RoomFeatures
import app.protocol.wire.HelloData
import app.protocol.wire.Room
import app.room.RoomViewmodel
import app.room.models.Message
import app.utils.loggy
import app.utils.md5
import app.utils.platformCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_set_as_ready

/**
 * Handles user-initiated actions and outbound protocol messages for playback control,
 * seeking, and chat. Counterpart to [RoomCallback]. All send operations are no-ops in solo mode.
 */
class RoomEventDispatcher(val viewmodel: RoomViewmodel) : AbstractManager(viewmodel) {
    val network = viewmodel.networkManager
    val session = viewmodel.session

    suspend fun sendHello() {
        val passwordHash = session.currentPassword.takeIf { it.isNotEmpty() }
            ?.let { md5(it).toHexString(HexFormat.Default) }
        network.send(
            WireMessage.Hello(
                HelloData(
                    username = session.currentUsername,
                    password = passwordHash,
                    room = Room(session.currentRoom),
                    version = SYNCPLAY_PROTOCOL_VERSION,
                    realversion = SYNCPLAY_PROTOCOL_VERSION,
                    features = clientFeatures
                )
            )
        )
    }

    /**
     * The player position (ms) from *before* the most recent user-initiated seek.
     * Used for the "X seeked from A to B" OSD/chat message and for the Undo Seek history.
     *
     * Callers must set this *before* moving the player (slider, popup, chapter, fast-seek
     * button), since once the player has moved, `currentPositionMs()` returns the
     * post-seek value. [sendSeek] auto-fills it as a fallback if it hasn't been set
     * during this user gesture, but that captures the post-seek position when the
     * caller already nudged the player first — so prefer setting it explicitly.
     */
    var pendingSeekFromMs: Long = 0L

    fun sendSeek(newPosMs: Long) {
        if (viewmodel.isSoloMode) return

        viewmodel.viewModelScope.launch(Dispatchers.IO) {
            val playing = viewmodel.player.isPlaying()
            // Prime the polling baseline so the next ProtocolManager.pollPlayerStateOnce
            // doesn't re-broadcast this seek as if the player had jumped on its own.
            viewmodel.protocol.primePollBaseline(paused = !playing, positionMs = newPosMs)
            network.send(
                viewmodel.protocol.buildStatePacket(
                    serverTime = null,
                    doSeek = true,
                    position = newPosMs / 1000.0,
                    changeState = 1,
                    play = playing
                )
            )
        }
    }

    fun sendMessage(msg: String) {
        if (viewmodel.isSoloMode) return
        network.sendAsync(WireMessage.chatRequest(msg))
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
                network.sendAsync(WireMessage.readiness(isReady = true, manuallyInitiated = true))
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

        viewmodel.viewModelScope.launch(Dispatchers.IO) {
            val (posSec, posMs) = withContext(Dispatchers.Main.immediate) {
                val ms = viewmodel.player.currentPositionMs()
                (ms / 1000.0) to ms
            }
            viewmodel.protocol.primePollBaseline(paused = !playback.play, positionMs = posMs)
            network.send(
                viewmodel.protocol.buildStatePacket(
                    serverTime = null,
                    doSeek = null,
                    position = posSec,
                    changeState = 1,
                    play = playback.play
                )
            )
        }
    }

    /**
     * Checks whether the user is allowed to unpause based on the readiness unpause mode.
     * Mirrors the PC client's `instaplayConditionsMet()`.
     */
    private fun instaplayConditionsMet(): Boolean {
        // python's first gate: if we can't control a controlled room, we can never unpause
        // it ourselves — no point pretending we can. Without this, mobile lets the user
        // try, then the server's forcePositionUpdate echoes their state back as paused,
        // creating a brief unpause-then-repause flicker on the local player.
        if (isInControlledRoomWithoutController()) return false

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

    private fun isInControlledRoomWithoutController(): Boolean {
        if (!session.roomFeatures.supportsManagedRooms) return false
        if (!session.currentRoom.startsWith("+")) return false
        return session.userList.value
            .firstOrNull { it.name == session.currentUsername }
            ?.isController != true
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

            pendingSeekFromMs = currentMs
            sendSeek(newPos)
            viewmodel.player.seekTo(newPos)
            if (viewmodel.isSoloMode) viewmodel.seeks.add(Pair(currentMs, newPos))
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
            viewmodel.session.messageSequence.update { it + msg }
        }
    }

    private companion object {
        /** Static feature manifest the client advertises in its `Hello`. */
        val clientFeatures = RoomFeatures()
    }
}
