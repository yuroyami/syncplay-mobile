package app.protocol.server

import app.protocol.ProtocolManager
import app.protocol.ProtocolManager.Companion.SEEK_THRESHOLD
import app.protocol.event.RoomEventHandler
import app.protocol.models.ClientMessage
import app.protocol.network.NetworkManager
import app.room.RoomViewmodel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Clock

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
        callback: RoomEventHandler
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

        if (protocol.lastGlobalUpdate != null && position != null) {
            val playerDiff = withContext(Dispatchers.Main.immediate) { abs(viewmodel.player.currentPositionMs() / 1000.0 - position) }
            val globalDiff = abs(protocol.globalPositionMs / 1000.0 - position)
            val surelyPausedChanged = protocol.globalPaused != paused && paused == viewmodel.player.isPlaying()
            val seeked = playerDiff > SEEK_THRESHOLD && globalDiff > SEEK_THRESHOLD

            dispatcher.sendAsync<ClientMessage.State> {
                serverTime = latencyCalculation
                this.doSeek = seeked
                this.position = withContext(Dispatchers.Main.immediate) { viewmodel.player.currentPositionMs().div(1000L) } // if dontSlowDownWithMe useGlobalPosition or else usePlayerPosition
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