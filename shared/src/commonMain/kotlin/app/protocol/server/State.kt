package app.protocol.server

import app.protocol.ProtocolManager
import app.protocol.ProtocolManager.Companion.SEEK_THRESHOLD
import app.protocol.ProtocolManager.Companion.FASTFORWARD_BEHIND_THRESHOLD
import app.protocol.ProtocolManager.Companion.FASTFORWARD_EXTRA_TIME
import app.protocol.ProtocolManager.Companion.FASTFORWARD_RESET_THRESHOLD
import app.protocol.ProtocolManager.Companion.FASTFORWARD_THRESHOLD
import app.protocol.ProtocolManager.Companion.SLOWDOWN_RATE
import app.protocol.ProtocolManager.Companion.SLOWDOWN_RESET_THRESHOLD
import app.protocol.ProtocolManager.Companion.SLOWDOWN_THRESHOLD
import app.preferences.Preferences
import app.preferences.value
import app.protocol.event.RoomCallback
import app.protocol.event.ClientMessage
import app.protocol.network.NetworkManager
import app.room.RoomViewmodel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Handles incoming [State] messages from the Syncplay server, encoding global playback state
 * for the shared session: position, pause state, ping/latency, and "ignoring on the fly" counters.
 *
 * Processes synchronization adjustments, desync detection, and sends acknowledgments back to the server.
 */
@Serializable
data class State(
    @SerialName("State") val state: StateData
) : ServerMessage {

    @Serializable
    data class StateData(
        val ping: PingData? = null,
        val ignoringOnTheFly: IgnoringOnTheFlyData? = null,
        val playstate: PlaystateData? = null
    )

    /** Network timing data for latency calculation and synchronization. */
    @Serializable
    data class PingData(
        val latencyCalculation: Double? = null,
        val clientLatencyCalculation: Double? = null,
        val serverRtt: Double? = null
    )

    /**
     * Prevents feedback loops during rapid state changes by temporarily ignoring
     * a client's state updates on the server side.
     */
    @Serializable
    data class IgnoringOnTheFlyData(
        val server: Int? = null,
        val client: Int? = null
    )

    /** @property setBy Username of the user who initiated this state change. */
    @Serializable
    data class PlaystateData(
        val doSeek: Boolean? = null,
        val position: Double? = null,
        val setBy: String? = null,
        val paused: Boolean? = null
    )

    /**
     * Synchronizes local playback with the incoming server state.
     * Adheres to the original implementation line by line.
     */
    override suspend fun handle(
        protocol: ProtocolManager,
        viewmodel: RoomViewmodel,
        dispatcher: NetworkManager,
        callback: RoomCallback
    ) {
        var position: Double? = null
        var paused: Boolean? = null
        var doSeek: Boolean? = null
        var setBy: String? = null

        var messageAge = 0.0
        var latencyCalculation: Double? = null

        state.ignoringOnTheFly?.let { ignoringOnTheFly ->
            if (ignoringOnTheFly.server != null) {
                protocol.serverIgnFly = ignoringOnTheFly.server
                protocol.clientIgnFly = 0
            } else if (ignoringOnTheFly.client != null) {
                if (protocol.clientIgnFly == ignoringOnTheFly.client) {
                    protocol.clientIgnFly = 0
                }
            }
        }

        state.playstate?.let { playstate ->
            position = playstate.position ?: 0.0
            paused = playstate.paused
            doSeek = playstate.doSeek
            setBy = playstate.setBy
        }

        state.ping?.let { ping ->
            latencyCalculation = ping.latencyCalculation

            ping.clientLatencyCalculation?.let { timestamp ->
                val serverRtt = ping.serverRtt ?: return@let

                protocol.pingService.receiveMessage(timestamp.roundToLong(), serverRtt)
            }
            messageAge = protocol.pingService.forwardDelay
        }

        if (position != null && paused != null && protocol.clientIgnFly == 0) {
            val pausedChanged = protocol.globalPaused != paused || paused == viewmodel.player.isPlaying()
            val diff = withContext(Dispatchers.Main.immediate) { (viewmodel.player.currentPositionMs() / 1000.0) - position }

            /* Updating Global State */
            protocol.globalPaused = paused
            protocol.globalPositionMs = position * 1000L
            if (!paused) protocol.globalPositionMs += messageAge //Account for network drift

            //loggy("GLOBAL_POSITION_MS: ${protocol.globalPositionMs}")
            //loggy("msgAge: $messageAge")

            if (protocol.lastGlobalUpdate == null) {
                if (viewmodel.media != null) {
                    withContext(Dispatchers.Main) {
                        viewmodel.player.seekTo(position.toLong())
                        if (paused) viewmodel.player.pause() else viewmodel.player.play()
                    }
                }
            }

            protocol.lastGlobalUpdate = Clock.System.now()

            if (doSeek == true && setBy != null) {
                if (protocol.speedChanged) {
                    withContext(Dispatchers.Main) { viewmodel.player.setSpeed(1.0) }
                    protocol.speedChanged = false
                }
                callback.onSomeoneSeeked(setBy, position)
            }

            /* Rewind check if someone is behind */
            if (diff > protocol.rewindThreshold && doSeek != true && Preferences.SYNC_REWIND.value()) {
                if (protocol.speedChanged) {
                    withContext(Dispatchers.Main) { viewmodel.player.setSpeed(1.0) }
                    protocol.speedChanged = false
                }
                callback.onSomeoneBehind(setBy ?: "", position)
            }

            /* Fast-forward if persistently behind (like PC client's fastforward on desync) */
            if (diff < -FASTFORWARD_BEHIND_THRESHOLD && doSeek != true && Preferences.SYNC_FASTFORWARD.value()) {
                val now = Clock.System.now()
                if (protocol.behindFirstDetected == null) {
                    protocol.behindFirstDetected = now
                } else {
                    val durationBehind = (now - protocol.behindFirstDetected!!).inWholeMilliseconds / 1000.0
                    if (durationBehind > (FASTFORWARD_THRESHOLD - FASTFORWARD_BEHIND_THRESHOLD)
                        && diff < -FASTFORWARD_THRESHOLD
                    ) {
                        callback.onSomeoneFastForwarded(setBy ?: "", position + FASTFORWARD_EXTRA_TIME)
                        protocol.behindFirstDetected = now + FASTFORWARD_RESET_THRESHOLD.seconds
                    }
                }
            } else {
                protocol.behindFirstDetected = null
            }

            /* Slow down to cover time difference (like PC client's _slowDownToCoverTimeDifference) */
            if (doSeek != true && !paused) {
                if (Preferences.SYNC_SLOWDOWN.value()) {
                    if (diff > SLOWDOWN_THRESHOLD && !protocol.speedChanged) {
                        if (setBy != null && setBy != protocol.session.currentUsername) {
                            withContext(Dispatchers.Main) { viewmodel.player.setSpeed(SLOWDOWN_RATE) }
                            protocol.speedChanged = true
                        }
                    } else if (protocol.speedChanged && diff < SLOWDOWN_RESET_THRESHOLD) {
                        withContext(Dispatchers.Main) { viewmodel.player.setSpeed(1.0) }
                        protocol.speedChanged = false
                    }
                } else if (protocol.speedChanged) {
                    withContext(Dispatchers.Main) { viewmodel.player.setSpeed(1.0) }
                    protocol.speedChanged = false
                }
            }

            if (pausedChanged) {
                if (paused && protocol.speedChanged) {
                    withContext(Dispatchers.Main) { viewmodel.player.setSpeed(1.0) }
                    protocol.speedChanged = false
                }
                if (!paused) callback.onSomeonePlayed(setBy ?: "")
                if (paused) callback.onSomeonePaused(setBy ?: "")
            }
        }

        if (protocol.lastGlobalUpdate != null && position != null) {
            val playerDiff = withContext(Dispatchers.Main.immediate) { abs(viewmodel.player.currentPositionMs() / 1000.0 - position) }
            val globalDiff = abs(protocol.globalPositionMs / 1000.0 - position)
            val surelyPausedChanged = protocol.globalPaused != paused && paused == viewmodel.player.isPlaying()
            val seeked = playerDiff > SEEK_THRESHOLD && globalDiff > SEEK_THRESHOLD

            dispatcher.sendAsync<ClientMessage.State> {
                serverTime = latencyCalculation
                this.doSeek = seeked
                this.position = if (Preferences.SYNC_DONT_SLOW_WITH_ME.value()) {
                    protocol.globalPositionMs.div(1000.0).toLong()
                } else {
                    withContext(Dispatchers.Main.immediate) { viewmodel.player.currentPositionMs().div(1000L) }
                }
                changeState = if (surelyPausedChanged) 1 else 0
                play = viewmodel.player.isPlaying()
            }
        } else {
            dispatcher.sendAsync<ClientMessage.State> {
                serverTime = latencyCalculation
                this.doSeek = null
                this.position = null
                changeState = 0
                play = null
            }
        }

    }
}