package com.yuroyami.syncplay.managers.protocol.handler

import com.yuroyami.syncplay.managers.protocol.creator.PacketCreator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Clock

@Serializable
data class State(
    @SerialName("State") val state: StateData
): SyncplayMessage {

    @Serializable
    data class StateData(
        val ping: PingData? = null,
        val ignoringOnTheFly: IgnoringOnTheFlyData? = null,
        val playstate: PlaystateData? = null
    )

    @Serializable
    data class PingData(
        val latencyCalculation: Double? = null,
        val clientLatencyCalculation: Double? = null,
        val serverRtt: Double? = null
    )

    @Serializable
    data class IgnoringOnTheFlyData(
        val server: Int? = null,
        val client: Int? = null
    )

    @Serializable
    data class PlaystateData(
        val doSeek: Boolean? = null,
        val position: Double? = null,
        val setBy: String? = null,
        val paused: Boolean? = null
    )

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
                        viewmodel.player.seekTo(position.toLong())
                        if (paused) viewmodel.player.pause() else viewmodel.player.play()
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

                sender.sendAsync<PacketCreator.State> {
                    serverTime = latencyCalculation
                    this.doSeek = seeked
                    this.position = withContext(Dispatchers.Main.immediate) { viewmodel.player.currentPositionMs().div(1000L) } // if dontSlowDownWithMe useGlobalPosition or else usePlayerPosition
                    changeState = if (surelyPausedChanged) 1 else 0
                    play = viewmodel.player.isPlaying()
                }
            } else {
                sender.sendAsync<PacketCreator.State> {
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