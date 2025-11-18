package com.yuroyami.syncplay.managers.protocol.handler

import com.yuroyami.syncplay.managers.protocol.creator.PacketOut
import com.yuroyami.syncplay.managers.protocol.handler.PacketHandler.Companion.SEEK_THRESHOLD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Clock

/**
 * Handles incoming [State] messages from the Syncplay server, which represent the global playback state
 * and synchronization information for the shared viewing session.
 *
 * This message is central to Syncplay's synchronization mechanism, containing:
 * - Current playback position and pause state
 * - Ping/latency calculations for network timing
 * - "Ignoring on the fly" counters for conflict resolution
 *
 * The handler processes this state information and:
 * 1. Updates local global state tracking
 * 2. Applies synchronization adjustments to the local player
 * 3. Detects and handles desynchronization scenarios
 * 4. Sends state acknowledgments back to the server
 *
 * @property state The complete state data container from the server
 */
@Serializable
data class State(
    @SerialName("State") val state: StateData
): SyncplayMessage {

    /**
     * Container for all state information in a [State] message.
     *
     * @property ping Optional ping and latency timing information
     * @property ignoringOnTheFly Optional conflict resolution counters
     * @property playstate Optional playback state (position, pause, seek events)
     */
    @Serializable
    data class StateData(
        val ping: PingData? = null,
        val ignoringOnTheFly: IgnoringOnTheFlyData? = null,
        val playstate: PlaystateData? = null
    )

    /**
     * Network timing information for latency calculation and synchronization.
     *
     * @property latencyCalculation Server timestamp for round-trip time calculation
     * @property clientLatencyCalculation Echo of client's previously sent timestamp
     * @property serverRtt Server's measured round-trip time
     */
    @Serializable
    data class PingData(
        val latencyCalculation: Double? = null,
        val clientLatencyCalculation: Double? = null,
        val serverRtt: Double? = null
    )

    /**
     * Conflict resolution mechanism to prevent feedback loops during rapid state changes.
     *
     * When a client is "ignored on the fly," its state changes are temporarily disregarded
     * by the server to maintain stability.
     *
     * @property server Server-side ignore counter
     * @property client Client-side ignore counter (for acknowledgment)
     */
    @Serializable
    data class IgnoringOnTheFlyData(
        val server: Int? = null,
        val client: Int? = null
    )

    /**
     * Core playback state information including position, pause state, and seek events.
     *
     * @property doSeek Whether this state change was caused by an explicit seek operation
     * @property position Current playback position in seconds
     * @property setBy Username of the user who initiated this state change
     * @property paused Whether playback is currently paused
     */
    @Serializable
    data class PlaystateData(
        val doSeek: Boolean? = null,
        val position: Double? = null,
        val setBy: String? = null,
        val paused: Boolean? = null
    )

    /**
     * Processes the incoming [State] message and synchronizes local playback accordingly.
     *
     * This coroutine handles:
     * - Updating global state tracking variables
     * - Processing ping/latency information for network timing
     * - Resolving "ignoring on the fly" conflicts
     * - Applying synchronized playback state to the local player
     * - Detecting desynchronization and triggering appropriate responses
     * - Sending state acknowledgment back to the server
     *
     * The method implements Syncplay's core synchronization logic including:
     * - Network drift compensation using message age
     * - Rewind detection for clients that are behind
     * - Seek operation attribution and notification
     * - Pause/play state change detection and callbacks
     *
     * This adheres to the original implementation line by line.
     *
     * @receiver [PacketHandler] providing access to protocol state, viewmodel, and network operations
     */
    context(packetHandler: PacketHandler)
    override suspend fun handle() {
        with(packetHandler) {
            var position: Double? = null
            var paused: Boolean? = null
            var doSeek: Boolean? = null
            var setBy: String? = null

            var messageAge = 0.0
            var latencyCalculation: Double? = null

            state.ignoringOnTheFly?.let { ignoringOnTheFly ->
                if (ignoringOnTheFly.server != null) {
                    viewmodel.protocolManager.serverIgnFly = ignoringOnTheFly.server
                    viewmodel.protocolManager.clientIgnFly = 0
                } else if (ignoringOnTheFly.client != null) {
                    if (viewmodel.protocolManager.clientIgnFly == ignoringOnTheFly.client) {
                        viewmodel.protocolManager.clientIgnFly = 0
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

                    viewmodel.protocolManager.pingService.receiveMessage(timestamp.roundToLong(), serverRtt)
                }
                messageAge = 10.0 //FIXME viewmodel.protocolManager.pingService.forwardDelay
            }

            if (position != null && paused != null && viewmodel.protocolManager.clientIgnFly == 0) {
                val pausedChanged = viewmodel.protocolManager.globalPaused != paused || paused == viewmodel.player.isPlaying()
                val diff = withContext(Dispatchers.Main.immediate) { (viewmodel.player.currentPositionMs() / 1000.0) - position }

                /* Updating Global State */
                viewmodel.protocolManager.globalPaused = paused
                protocol.globalPositionMs = position * 1000L
                if (!paused) protocol.globalPositionMs += messageAge //Account for network drift

                //loggy("GLOBAL_POSITION_MS: ${protocol.globalPositionMs}")
                //loggy("msgAge: $messageAge")

                if (lastGlobalUpdate == null) {
                    if (protocol.viewmodel.playerManager.media.value != null) {
                        withContext(Dispatchers.Main) {
                            viewmodel.player.seekTo(position.toLong())
                            if (paused) viewmodel.player.pause() else viewmodel.player.play()
                        }
                    }
                }

                lastGlobalUpdate = Clock.System.now()

                if (doSeek == true && setBy != null) {
                    callback.onSomeoneSeeked(setBy, position)
                }

                /* Rewind check if someone is behind */
                if (diff > protocol.rewindThreshold && doSeek != true /* && rewindOnDesync pref */) {
                    callback.onSomeoneBehind(setBy ?: "", position)
                }

                //if (fastforwardOnDesyncPref && (currentUser.canControl() == false or dontSlowDownWithMe == true)
                //      if (diff < (constants.FASTFORWARD_BEHIND_THRESHOLD * -1)  && doSeek != true
                //          if (behindFirstDetected == true)
                //              behindFirstDetected = now()
                //          else
                //              durationBehind = now() - behindFirstDetected
                //              if (durationBehind > if (durationBehind > (self._config['fastforwardThreshold']-constants.FASTFORWARD_BEHIND_THRESHOLD))\ and (diff < (self._config['fastforwardThreshold'] * -1))
                //                  madeChangeOnPlayer = fastforwardPlayerDueToTimeDifference(position, setBy)
                //                  behindFirstDetected = now() + constants.FASTFORWARD_RESET_THRESHOLD
                //      else behindFirstDetected = null

                //if self._player.speedSupported and not doSeek and not paused and  not self._config['slowOnDesync'] == False:
                //madeChangeOnPlayer = self._slowDownToCoverTimeDifference(diff, setBy)

                if (pausedChanged) {
                    if (!paused) callback.onSomeonePlayed(setBy ?: "")
                    if (paused) callback.onSomeonePaused(setBy ?: "")
                }
            }

            if (lastGlobalUpdate != null && position != null) {
                val playerDiff = withContext(Dispatchers.Main.immediate) { abs(viewmodel.player.currentPositionMs() / 1000.0 - position) }
                val globalDiff = abs(protocol.globalPositionMs / 1000.0 - position)
                val surelyPausedChanged = protocol.globalPaused != paused && paused == viewmodel.player.isPlaying()
                val seeked = playerDiff > SEEK_THRESHOLD && globalDiff > SEEK_THRESHOLD

                sender.sendAsync<PacketOut.State> {
                    serverTime = latencyCalculation
                    this.doSeek = seeked
                    this.position = withContext(Dispatchers.Main.immediate) { viewmodel.player.currentPositionMs().div(1000L) } // if dontSlowDownWithMe useGlobalPosition or else usePlayerPosition
                    changeState = if (surelyPausedChanged) 1 else 0
                    play = viewmodel.player.isPlaying()
                }
            } else {
                sender.sendAsync<PacketOut.State> {
                    serverTime = latencyCalculation
                    this.doSeek = null
                    this.position = null
                    changeState = 0
                    play = null
                }
            }
        }
    }
}