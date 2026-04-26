package app.protocol

import androidx.lifecycle.viewModelScope
import app.AbstractManager
import app.protocol.models.ConnectionState
import app.protocol.models.PingService
import app.protocol.wire.IgnoringOnTheFlyData
import app.protocol.wire.PingData
import app.protocol.wire.PlaystateData
import app.protocol.wire.StateData
import app.room.RoomViewmodel
import app.utils.generateTimestampMillis
import app.utils.loggy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ProtocolManager(val viewmodel: RoomViewmodel) : AbstractManager(viewmodel) {

    var session: Session = Session(this)

    var globalPaused: Boolean = true
    var globalPositionMs: Double = 0.0

    /** Tracks conflicting state updates during rapid changes. */
    var serverIgnFly: Int = 0

    /** Prevents responding to our own state changes until the server acknowledges them. */
    var clientIgnFly: Int = 0

    /** Position drift threshold for hard seek. 12s per Syncplay spec — do not change. */
    val rewindThreshold = 12L

    /** Timestamp of the last received global state update, used for sync timing. */
    var lastGlobalUpdate: Instant? = null

    var pingService = PingService()

    /** Tracks whether playback speed has been adjusted for desync correction. */
    var speedChanged = false

    /** Timestamp when we first detected the client is behind. Null if not behind. */
    var behindFirstDetected: Instant? = null

    /** Set during room transitions to ignore stale packets from the previous room. */
    var isRoomChanging = false

    val supportsChat = MutableStateFlow(true)
    val supportsManagedRooms = MutableStateFlow(false)

    val isManagedRoom = MutableStateFlow(false)

    /**
     * Timestamp of the last `State` message received from the server. The watchdog uses this to
     * detect silent disconnects — i.e. the TCP socket looks healthy on our end but the
     * server stopped sending State packets (common on flaky networks, especially iOS).
     */
    @Volatile
    var lastStateReceivedAt: Instant? = null

    private var watchdogJob: Job? = null
    private var listProbeJob: Job? = null

    /**
     * Starts the channel-health coroutines for the current room session.
     *
     * Two jobs run while CONNECTED:
     *  - **List-probe ping** — sends an empty `List` request every [LIST_PROBE_INTERVAL_SECONDS]
     *    so the server is forced to respond. Keeps the channel warm and surfaces a broken
     *    socket early: if the send fails, NetworkManager's retry/onError path queues the
     *    packet and flips state to DISCONNECTED.
     *  - **State watchdog** — runs every [WATCHDOG_INTERVAL_SECONDS]. If no State message
     *    has arrived for [STATE_TIMEOUT_SECONDS] seconds while we still believe ourselves
     *    connected, fires `onDisconnected()` which kicks off a reconnect. This is what
     *    fixes the "app doesn't know it's disconnected until I send a chat" bug — the
     *    Syncplay server gives up after ~10–15 unanswered State broadcasts, which matches
     *    this 15s threshold.
     *
     * No-op in solo mode — called from onConnected(), so the guard is defense-in-depth.
     */
    fun startChannelHealthMonitoring() {
        if (viewmodel.isSoloMode) return
        stopChannelHealthMonitoring()

        // Seed the watchdog so it doesn't immediately fire before any State arrives.
        lastStateReceivedAt = Clock.System.now()

        val network = viewmodel.networkManager

        listProbeJob = viewmodel.viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(LIST_PROBE_INTERVAL_SECONDS.seconds)
                if (network.state.value == ConnectionState.CONNECTED) {
                    network.send(WireMessage.listRequest())
                }
            }
        }

        watchdogJob = viewmodel.viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_SECONDS.seconds)
                if (network.state.value != ConnectionState.CONNECTED) continue

                val last = lastStateReceivedAt ?: continue
                val elapsedSec = (Clock.System.now() - last).inWholeSeconds
                if (elapsedSec >= STATE_TIMEOUT_SECONDS) {
                    loggy("Channel watchdog: no State received for ${elapsedSec}s — marking disconnected")
                    // Drop the now-stale socket before firing the callback so the reconnect
                    // logic in onDisconnected() doesn't try to reuse a dead connection.
                    network.terminateExistingConnection()
                    viewmodel.callback.onDisconnected()
                    break
                }
            }
        }
    }

    /** Cancels the channel-health coroutines. Safe to call multiple times. */
    fun stopChannelHealthMonitoring() {
        watchdogJob?.cancel()
        watchdogJob = null
        listProbeJob?.cancel()
        listProbeJob = null
    }

    override fun invalidate() {
        stopChannelHealthMonitoring()
        lastStateReceivedAt = null
        session = Session(this)
        globalPaused = true
        globalPositionMs = 0.0
        serverIgnFly = 0
        clientIgnFly = 0
        speedChanged = false
        behindFirstDetected = null
        pingService = PingService()
    }

    /**
     * Builds an outbound `State` packet, applying the same `ignoringOnTheFly` bookkeeping
     * (mutating [serverIgnFly] / [clientIgnFly] as side effects) as the python reference
     * client.
     */
    fun buildStatePacket(
        serverTime: Double?,
        doSeek: Boolean?,
        position: Long?,
        changeState: Int,
        play: Boolean?
    ): WireMessage.State {
        val clientIgnoreIsNotSet = clientIgnFly == 0 || serverIgnFly != 0

        val playstate = if (clientIgnoreIsNotSet && position != null && play != null) {
            PlaystateData(
                position = position.toDouble(),
                paused = !play,
                doSeek = doSeek
            )
        } else null

        val ping = PingData(
            latencyCalculation = serverTime,
            clientLatencyCalculation = generateTimestampMillis() / 1000.0,
            clientRtt = pingService.rtt
        )

        if (changeState == 1) {
            clientIgnFly += 1
        }

        val ignoring = if (clientIgnFly != 0 || serverIgnFly != 0) {
            val ign = IgnoringOnTheFlyData(
                server = serverIgnFly.takeIf { it != 0 },
                client = clientIgnFly.takeIf { it != 0 }
            )
            if (serverIgnFly != 0) serverIgnFly = 0
            ign
        } else null

        return WireMessage.State(
            StateData(playstate = playstate, ping = ping, ignoringOnTheFly = ignoring)
        )
    }

    companion object {
        /** Playback drift threshold in seconds before a corrective seek is triggered. */
        const val SEEK_THRESHOLD = 1L

        /** Playback speed used to gradually catch up when ahead of others. */
        const val SLOWDOWN_RATE = 0.95

        /** Time difference (seconds) at which slowdown kicks in. */
        const val SLOWDOWN_THRESHOLD = 1.5

        /** Time difference (seconds) at which speed reverts to normal. */
        const val SLOWDOWN_RESET_THRESHOLD = 0.1

        /** Time difference (seconds, negative/behind) at which fastforward detection starts. */
        const val FASTFORWARD_BEHIND_THRESHOLD = 1.75

        /** Time difference (seconds, behind) at which fastforward triggers after waiting. */
        const val FASTFORWARD_THRESHOLD = 5.0

        /** Extra time (seconds) added when fastforwarding to overshoot slightly. */
        const val FASTFORWARD_EXTRA_TIME = 0.25

        /** Cooldown (seconds) after a fastforward before it can trigger again. */
        const val FASTFORWARD_RESET_THRESHOLD = 3.0

        /** How often the list-probe coroutine fires an empty List to keep the channel warm. */
        const val LIST_PROBE_INTERVAL_SECONDS = 15L

        /** How often the State watchdog checks whether the server has gone silent. */
        const val WATCHDOG_INTERVAL_SECONDS = 5L

        /** If no State message has arrived in this many seconds, we assume the channel is
         * broken and trigger a reconnect. Chosen to match the Syncplay server's own
         * ~10–15s threshold for dropping unresponsive clients. */
        const val STATE_TIMEOUT_SECONDS = 15L
    }
}
