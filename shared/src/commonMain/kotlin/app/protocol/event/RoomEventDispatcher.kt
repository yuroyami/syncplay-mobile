package app.protocol.event

import androidx.lifecycle.viewModelScope
import app.AbstractManager
import app.i18n.Localization
import app.player.Playback
import app.preferences.Preferences
import app.preferences.Preferences.UNPAUSE_ACTION
import app.preferences.value
import app.protocol.ProtocolManager.Companion.SYNCPLAY_LEGACY_VERSION
import app.protocol.ProtocolManager.Companion.SYNCPLAY_PROTOCOL_VERSION
import app.protocol.Session
import app.protocol.WireMessage
import app.protocol.models.RoomFeatures
import app.protocol.sync.localToRoomSeconds
import app.protocol.sync.LocalSeek
import app.protocol.wire.HelloData
import app.protocol.wire.Room
import app.room.OSDCategory
import app.room.RoomViewmodel
import app.room.models.Message
import app.room.models.collapsedForChat
import app.utils.loggy
import app.utils.md5
import app.utils.platformCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

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
                    // PC wire shape: `version` carries the 1.2.X compatibility constant,
                    // `realversion` the actual protocol version (protocols.py:165-166).
                    version = SYNCPLAY_LEGACY_VERSION,
                    realversion = SYNCPLAY_PROTOCOL_VERSION,
                    features = clientFeatures
                )
            )
        )
    }

    /** Announce this exact gesture; queued seeks retain their own origin for the self echo. */
    fun sendSeek(newPosMs: Long, fromMs: Long, recordUndo: Boolean = true) {
        if (viewmodel.isSoloMode) return
        // Capture and enqueue before moving the engine. Independent IO launches could reorder
        // this seek with the next pause/seek even though the socket writer itself is ordered.
        viewmodel.protocol.sendLocalState(
            position = localToRoomSeconds(newPosMs, viewmodel.protocol.userTimeOffsetSeconds()),
            play = viewmodel.protocol.expectedPlaying,
            seek = LocalSeek(fromMs, newPosMs, recordUndo),
        )
    }

    fun sendMessage(msg: String) {
        if (viewmodel.isSoloMode) return
        network.sendAsync(WireMessage.chatRequest(msg))
    }

    fun controlPlayback(playback: Playback, tellServer: Boolean) {
        // In the background the player is paused on purpose and stays that way: nothing here
        // reaches the engine, and nothing is told to the room (a backgrounded client used to
        // broadcast its pause and drag everyone down with it). Inbound pause/play is skipped
        // too; the return to the foreground re-anchors to the room's real state in one step.
        if (viewmodel.uiState.isInBackground) return

        /* If this is a user-initiated play request, check readiness gating */
        if (playback == Playback.PLAY && tellServer && !viewmodel.isSoloMode
            && viewmodel.session.roomFeatures.supportsReadiness
        ) {
            if (!instaplayConditionsMet()) {
                /* Block the unpause — set as ready instead, and say so where the user is looking. */
                loggy("SYNCPLAY Readiness: Conditions not met, setting as ready instead of unpausing")
                viewmodel.session.ready.value = true
                viewmodel.readiness.evaluate()
                network.sendAsync(WireMessage.readiness(isReady = true, manuallyInitiated = true))
                broadcastMessage(isChat = false) { Localization.strings.roomSetAsReady }
                viewmodel.dispatchOSD(OSDCategory.WARNING) { Localization.strings.roomSetAsReady }
                return
            }
        }

        // Set the expectation BEFORE we touch the player. The engine's isNowPlaying
        // callback fires synchronously inside player.pause()/play() on some engines
        // (notably ExoPlayer), and the protocol's flow collector reads this expectation
        // to decide whether to re-broadcast the change. If we set it AFTER, there's a
        // race where the collector sees the engine update against the stale expectation
        // and broadcasts a redundant State packet.
        viewmodel.protocol.noteExpectedPlaybackState(paused = !playback.play)

        /* Skip native playback without media. The bundled VLCKit cookie-jar patch
         * dereferences the media descriptor inside native play before validating it;
         * the player handle itself need not be null. VLCKit's queued play makes that
         * boundary asynchronous. The user's room intent is still announced below. */
        if (viewmodel.media != null) {
            // A pause is the natural place to write down where we are.
            if (playback == Playback.PAUSE) viewmodel.resume.record()
            onMainThread {
                when (playback) {
                    Playback.PAUSE -> viewmodel.player.pause()
                    Playback.PLAY -> viewmodel.player.play()
                }
            }
        }

        platformCallback.onPlayback(!playback.play)

        if (viewmodel.isSoloMode || !tellServer) return

        /* Readiness follows a deliberate pause: walking away used to leave the room a watcher who
         * showed as ready forever, so PC's changeReadyState(!paused) is mirrored here. A follower
         * in a controlled room cannot control playback, so its readiness is left alone. */
        if (session.roomFeatures.supportsReadiness && !session.isInControlledRoomWithoutController()) {
            if (session.ready.value != playback.play) {
                session.ready.value = playback.play
                network.sendAsync(WireMessage.readiness(isReady = playback.play, manuallyInitiated = false))
            }
        }

        // During loading this advertises the room position instead of the engine's ~0.
        viewmodel.protocol.sendLocalState(
            position = viewmodel.protocol.reportableStatePositionSec(),
            play = playback.play,
        )
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
        if (session.isInControlledRoomWithoutController()) return false

        val unpauseAction = UNPAUSE_ACTION.value()
        val session = viewmodel.session

        return when (unpauseAction) {
            "IfAlreadyReady" -> session.ready.value
            "IfOthersReady" -> session.ready.value || session.areAllOtherUsersReady()
            "IfMinUsersReady" -> {
                // PC's instaplayConditionsMet gates ALL modes behind "if you're ready you
                // can always unpause" (client.py:1023, the leading `if isReady() or ...`).
                // Without this short-circuit, a user who is already ready but alone — or
                // whose peers aren't all ready — gets silently blocked and re-marked ready,
                // whereas PC would just play. Mirror the other modes here.
                session.ready.value ||
                    (session.areAllOtherUsersReady() && session.usersInRoomCount() >= 2)
            }
            "Always" -> true
            else -> true
        }
    }

    /**
     * The one seek path. Every user seek goes through here, in this order and nowhere else:
     * record the origin, announce it (a no-op in solo mode), move the engine, and in solo mode
     * record the pair for undo (online, the inbound echo records it). [fromMs] is the position
     * before the user's gesture; the seekbar captures it on the first drag event, because by
     * the time the finger lifts the preview has moved even though the engine has not.
     */
    fun seek(targetMs: Long, fromMs: Long? = null, recordUndo: Boolean = true) {
        // Chat commands and hardware media keys remain reachable during room startup.
        if (!viewmodel.playerManager.isPlayerReady.value) return
        val media = viewmodel.media ?: return
        viewmodel.player.playerScopeMain.launch {
            if (viewmodel.media !== media) return@launch
            seekNow(targetMs, fromMs, recordUndo)
        }
    }

    /** Announces and records a seek whose move the engine makes on its own (a chapter jump). */
    fun announceSeek(targetMs: Long, fromMs: Long) {
        sendSeek(targetMs, fromMs)
        rememberForUndo(fromMs, targetMs)
    }

    fun seekBckwd() = seekBy(-Preferences.SEEK_BACKWARD_JUMP.value())
    fun seekFrwrd() = seekBy(Preferences.SEEK_FORWARD_JUMP.value())

    fun seekBy(deltaSeconds: Int) {
        if (!viewmodel.playerManager.isPlayerReady.value) return
        val media = viewmodel.media ?: return
        viewmodel.player.playerScopeMain.launch {
            if (viewmodel.media !== media) return@launch
            seekByMillis(deltaSeconds * 1000L)
        }
    }

    /**
     * Submits a relative seek in main-thread request order, preserving subsecond offsets from
     * system controls. Returns the clamped target after [app.player.PlayerImpl.seekTo] returns,
     * or null when the player is unavailable. This reports submission, not native completion.
     */
    suspend fun seekByMillis(deltaMs: Long): Long? {
        val media = viewmodel.media ?: return null
        return withContext(Dispatchers.Main.immediate) {
            if (!viewmodel.playerManager.isPlayerReady.value || viewmodel.media !== media) return@withContext null
            val currentMs = viewmodel.player.currentPositionMs()
            seekNow(currentMs + deltaMs, currentMs, recordUndo = true)
        }
    }

    /** Undoes a recorded seek: back to where it started, announced like any other seek. */
    fun undoSeek(seek: Pair<Long, Long>) {
        viewmodel.seeks.remove(seek)
        seek(targetMs = seek.first, fromMs = seek.second, recordUndo = false)
    }

    private suspend fun seekNow(targetMs: Long, fromMs: Long?, recordUndo: Boolean): Long? {
        // Prepare only for the currently loaded file; an unavailable seek must not move peers.
        if (!viewmodel.playerManager.isPlayerReady.value) return null
        val media = viewmodel.media ?: return null
        val player = viewmodel.player
        val origin = fromMs ?: player.currentPositionMs()
        val duration = viewmodel.playerManager.timeFullMillis.value
        val target = if (duration > 0L) targetMs.coerceIn(0L, duration) else targetMs.coerceAtLeast(0L)
        val preparedTarget = player.prepareSeekTarget(target) ?: return null
        if (viewmodel.media !== media || viewmodel.player !== player || !viewmodel.playerManager.isPlayerReady.value) return null
        sendSeek(preparedTarget, origin, recordUndo)
        player.seekTo(preparedTarget)
        if (recordUndo) rememberForUndo(origin, preparedTarget)
        return preparedTarget
    }

    /** Online the inbound echo records the seek; solo mode has no echo, so it is recorded here. */
    private fun rememberForUndo(fromMs: Long, toMs: Long) {
        if (!viewmodel.isSoloMode) return
        if (abs(toMs - fromMs) < RoomCallback.SEEK_NOOP_THRESHOLD_MS) return
        viewmodel.seeks.add(Pair(fromMs, toMs))
    }

    fun broadcastMessage(isChat: Boolean, chatter: String = "", isError: Boolean = false, message: suspend () -> String) {
        if (viewmodel.isSoloMode) return

        viewmodel.viewModelScope.launch {
            val text = message.invoke().collapsedForChat()
            // A notice that was only blank lines has nothing to show.
            if (text.isEmpty()) return@launch
            val msg = Message(
                sender = if (isChat) chatter else null,
                isMainUser = chatter == viewmodel.session.currentUsername,
                content = text,
                isError = isError
            )
            // Bounded: a long session must not keep every line ever shown.
            viewmodel.session.messageSequence.update { (it + msg).takeLast(Session.MAX_MESSAGES) }
        }
    }

    companion object {
        /** Static feature manifest the client advertises in its `Hello`. */
        val clientFeatures = RoomFeatures()
    }
}
