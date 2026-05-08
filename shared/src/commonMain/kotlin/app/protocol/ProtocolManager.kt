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
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ProtocolManager(val viewmodel: RoomViewmodel) : AbstractManager(viewmodel) {

    var session: Session = Session(this)

    var globalPaused: Boolean = true
    var globalPositionMs: Double = 0.0

    /**
     * The wall-clock time at which [globalPositionMs] was last set from a server `State`.
     * Used to extrapolate the room's *current* expected position via [extrapolatedGlobalPositionMs].
     */
    var lastGlobalPositionSetAt: Instant? = null

    /** Tracks conflicting state updates during rapid changes. Updated atomically. */
    private val _serverIgnFly = atomic(0)
    var serverIgnFly: Int
        get() = _serverIgnFly.value
        set(value) { _serverIgnFly.value = value }

    /**
     * Prevents responding to our own state changes until the server acknowledges them.
     * Atomic because both [buildStatePacket] (called from `Dispatchers.IO` on every user
     * action) and the player polling loop can race on the increment-and-send sequence.
     */
    private val _clientIgnFly = atomic(0)
    var clientIgnFly: Int
        get() = _clientIgnFly.value
        set(value) { _clientIgnFly.value = value }

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
    private var playerPollJob: Job? = null

    /**
     * Per-iteration baseline for the player-state poller. Each poll compares (now − last)
     * to detect pauseChange / seeked, then advances the baseline. Reset to `null` whenever
     * the protocol-side applies a state change to the player, so the next poll doesn't
     * re-report the change as a phantom user action.
     */
    private var pollLastAt: Instant? = null
    private var pollLastPositionMs: Long = 0L
    private var pollLastPaused: Boolean = true

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

        playerPollJob = viewmodel.viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(PLAYER_POLL_INTERVAL_MS.milliseconds)
                if (network.state.value != ConnectionState.CONNECTED) continue
                if (lastGlobalUpdate == null) continue
                if (isRoomChanging) continue
                pollPlayerStateOnce()
            }
        }
    }

    /** Cancels the channel-health coroutines. Safe to call multiple times. */
    fun stopChannelHealthMonitoring() {
        watchdogJob?.cancel()
        watchdogJob = null
        listProbeJob?.cancel()
        listProbeJob = null
        playerPollJob?.cancel()
        playerPollJob = null
        pollLastAt = null
    }

    /**
     * Polls the player's pause/position once, compares to the last poll's snapshot, and
     * sends a State packet (with `changeState=1`) if the player has paused/played itself
     * (buffering, EOF, internal seek) without going through `dispatcher.controlPlayback`.
     *
     * This is the missing half of the Syncplay protocol from python's `updatePlayerStatus`
     * pipeline — without it, the server never learns that the player auto-paused on a
     * buffer dip, and instead keeps telling us to play, which fights with the player.
     *
     * The protocol's `clientIgnoringOnTheFly` counter handles dedup against our own user
     * actions, so we don't need to skip when those are pending — `buildStatePacket` will
     * correctly suppress the playstate block during the unacked window.
     */
    private suspend fun pollPlayerStateOnce() {
        val player = viewmodel.player
        val nowMs = withContext(Dispatchers.Main.immediate) { player.currentPositionMs() }
        val isPlaying = player.isPlaying()
        val nowPaused = !isPlaying
        val now = Clock.System.now()

        val lastAt = pollLastAt
        val lastPos = pollLastPositionMs
        val lastPaused = pollLastPaused

        pollLastAt = now
        pollLastPositionMs = nowMs
        pollLastPaused = nowPaused

        // First poll after a baseline reset — initialize and skip change detection.
        if (lastAt == null) return

        val pauseChanged = nowPaused != lastPaused
        val elapsedMs = (now - lastAt).inWholeMilliseconds
        val expectedRate = when {
            lastPaused -> 0.0
            speedChanged -> SLOWDOWN_RATE
            else -> 1.0
        }
        val expectedAdvanceMs = (elapsedMs * expectedRate).toLong()
        val actualAdvanceMs = nowMs - lastPos
        val seeked = abs(actualAdvanceMs - expectedAdvanceMs) > (SEEK_THRESHOLD * 1000L)

        if (pauseChanged || seeked) {
            viewmodel.networkManager.sendAsync(
                buildStatePacket(
                    serverTime = null,
                    doSeek = seeked,
                    position = nowMs / 1000.0,
                    changeState = 1,
                    play = isPlaying
                )
            )
        }
    }

    /**
     * Updates the polling baseline with the user's intent so the next poll doesn't see
     * a "change" caused by an action that already went through `controlPlayback` /
     * `sendSeek` / a server-driven seek.
     *
     * Called by the dispatcher after every user action, and by [RoomServerMessageHandler]
     * after applying server-driven state changes to the player.
     */
    fun primePollBaseline(paused: Boolean, positionMs: Long) {
        pollLastAt = Clock.System.now()
        pollLastPositionMs = positionMs
        pollLastPaused = paused
    }

    override fun invalidate() {
        stopChannelHealthMonitoring()
        lastStateReceivedAt = null
        session = Session(this)
        globalPaused = true
        globalPositionMs = 0.0
        lastGlobalPositionSetAt = null
        serverIgnFly = 0
        clientIgnFly = 0
        speedChanged = false
        behindFirstDetected = null
        pingService = PingService()
    }

    /**
     * The room's *current* expected position in ms, extrapolated from the last server
     * `State`. While the room is playing, advances by wall-clock time elapsed since
     * [lastGlobalPositionSetAt]. Mirrors python's `getGlobalPosition()` — without the
     * extrapolation, a SYNC_ON_PAUSE seek lands on a stale frame from the last 1 Hz tick.
     */
    fun extrapolatedGlobalPositionMs(): Double {
        val anchor = lastGlobalPositionSetAt ?: return globalPositionMs
        if (globalPaused) return globalPositionMs
        val elapsedMs = (Clock.System.now() - anchor).inWholeMilliseconds.toDouble()
        return globalPositionMs + elapsedMs
    }

    /**
     * Builds an outbound `State` packet, applying the same `ignoringOnTheFly` bookkeeping
     * (mutating [serverIgnFly] / [clientIgnFly] as side effects) as the python reference
     * client.
     *
     * [position] is full-precision seconds (Double) on the wire — never round to whole
     * seconds: the protocol depends on sub-second precision for both the desync-detection
     * algorithm on the server and for `min(watchers)` not picking us as the slowest.
     */
    fun buildStatePacket(
        serverTime: Double?,
        doSeek: Boolean?,
        position: Double?,
        changeState: Int,
        play: Boolean?
    ): WireMessage.State {
        val clientIgnoreIsNotSet = clientIgnFly == 0 || serverIgnFly != 0

        val playstate = if (clientIgnoreIsNotSet && position != null && play != null) {
            PlaystateData(
                position = position,
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
            _clientIgnFly.incrementAndGet()
        }

        val snapshotServer = _serverIgnFly.value
        val snapshotClient = _clientIgnFly.value
        val ignoring = if (snapshotClient != 0 || snapshotServer != 0) {
            val ign = IgnoringOnTheFlyData(
                server = snapshotServer.takeIf { it != 0 },
                client = snapshotClient.takeIf { it != 0 }
            )
            if (snapshotServer != 0) _serverIgnFly.compareAndSet(snapshotServer, 0)
            ign
        } else null

        return WireMessage.State(
            StateData(playstate = playstate, ping = ping, ignoringOnTheFly = ignoring)
        )
    }

    companion object {
        /**
         * The Syncplay protocol version we advertise in `Hello.realversion`.
         *
         * 1.7.5 is the current `RECENT_CLIENT_THRESHOLD` in PC's constants.py — sending
         * anything below it triggers a "your client is old, please upgrade" MOTD warning.
         * We implement every protocol feature up through 1.7.5 (managedRooms, readiness,
         * setOthersReadiness, sharedPlaylists, chat) so the claim is accurate.
         */
        const val SYNCPLAY_PROTOCOL_VERSION = "1.7.5"

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

        /**
         * How often [pollPlayerStateOnce] runs. PC's reference client polls the player
         * every 100 ms; on mobile 250 ms is plenty — that's still 4× the server's 1 Hz
         * State broadcast rate, so user-visible reactions are bounded by ~250 ms.
         */
        const val PLAYER_POLL_INTERVAL_MS = 250L
    }
}
