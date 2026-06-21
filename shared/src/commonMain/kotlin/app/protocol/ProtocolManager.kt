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

    /**
     * Position drift threshold (seconds) at which the client triggers a corrective rewind
     * to catch up with the room. Matches PC's `DEFAULT_REWIND_THRESHOLD = 4`
     * ([syncplay-pc-src-master/syncplay/constants.py:63](../../../../../../syncplay-pc-src-master/syncplay/constants.py#L63)).
     * PC's `MINIMUM_REWIND_THRESHOLD = 3`, so values below 3 wouldn't be valid if this
     * ever becomes user-configurable.
     */
    val rewindThreshold = 4L

    /**
     * Timestamp of the last received global state update, used for sync timing. A `null` value
     * arms the one-shot "first sync" in [app.room.RoomServerMessageHandler.onState] (force-seek
     * to the room position + apply pause/play on the next inbound `State`).
     *
     * `@Volatile` because it is read on the inbound-State consumer thread (Dispatchers.Default in
     * NetworkManager) but written from other threads too: [resetSyncAnchorForReconnect] from the
     * reconnect loop and [reanchorSyncOnFileLoad] from a player-engine load callback (Main / mpv
     * IO / VLCKit delegate). Without the volatile, the consumer thread could keep observing a
     * stale non-null value on ARM and silently skip the re-anchor. Mirrors [lastStateReceivedAt].
     */
    @Volatile
    var lastGlobalUpdate: Instant? = null

    /**
     * Position-masking window for a freshly-loaded file: non-null (a future instant) means we are
     * still catching up and every outbound `State` must advertise the room's extrapolated position
     * instead of the engine's own (which sits at ~0 for the first second or so after a load, before
     * the first-sync seek lands and the engine converges). `null` means "report the true local
     * position" (not loading, or already caught up).
     *
     * This is the embedded-player equivalent of the desktop client's `getCalculatedPosition`
     * (players/mpv.py), which returns `getGlobalPosition()` whenever `fileLoaded == False`. Without
     * it a late loader reports position ~0 the instant it attaches a file, the official server adopts
     * it as the slowest watcher and broadcasts ~0, every other client rewinds back to 0, and the
     * loader's own rewind logic then yanks it to 0 too — so the [reanchorSyncOnFileLoad] first-sync
     * seeks to a position the loader itself just corrupted. [reportableStatePositionSec] clears it
     * (back to null) the moment the engine converges, the file proves too short to ever reach the
     * room position, or this deadline passes — after which the true local position is reported again
     * so a genuine standing desync (buffering) stays visible to the room.
     *
     * A SINGLE field — not a `(Boolean armed, Instant deadline)` pair — on purpose: a reader on the
     * inbound-State thread must never observe a half-written "armed but no deadline" state and
     * mistake it for timed-out, which would unmask and advertise the engine's ~0 (the exact poison
     * this prevents). `@Volatile` for the same cross-thread reason as [lastGlobalUpdate]: written
     * from the player load thread ([markAwaitingRoomResync] in parseMedia), read on the
     * inbound-State consumer thread.
     */
    @Volatile
    var awaitingRoomResyncDeadline: Instant? = null

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
    private var playbackBroadcastJob: Job? = null

    /**
     * Our current belief about the player's pause state. The flow collector started by
     * [startChannelHealthMonitoring] watches [PlayerManager.isNowPlaying] (which every
     * engine updates from its own native callback — ExoPlayer's `Player.Listener`,
     * MPV's property observer, VLC's delegate, AVPlayer's KVO) and broadcasts a State
     * packet only when the engine-reported playing state DIVERGES from this expectation.
     *
     * That way, user-initiated pauses (which already broadcast via
     * [RoomEventDispatcher.controlPlayback]) and server-driven pauses (applied by
     * [RoomServerMessageHandler]) don't get re-broadcast — the path that updates the
     * player also calls [noteExpectedPlaybackState] so the engine callback's resulting
     * flow emission matches our expectation. Only engine-driven auto-pauses/resumes
     * (buffer underrun, audio focus loss, EOF, etc.) trigger an actual broadcast.
     *
     * Callback-driven rather than polled: mobile player engines expose event APIs for
     * everything, so reacting to the flow avoids the phantom seeks a poll produces when
     * it samples a player still mid-converging on a seek target.
     */
    private var expectedPaused: Boolean = true

    /**
     * The room's *intended* play state as the app authoritatively knows it, with no engine
     * probe. Set synchronously by [noteExpectedPlaybackState] before the player is touched and
     * by the divergence collector. Outbound paths must read this instead of `player.isPlaying()`,
     * which on VLCKit 4 returns a stale pre-transition value in the async window right after a
     * pause/play toggle — broadcasting that stale value makes the server think the watcher
     * unpaused the room.
     */
    val expectedPlaying: Boolean get() = !expectedPaused

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
     *    connected, fires `onDisconnected()` which kicks off a reconnect. Detects silent
     *    disconnects where the socket looks healthy locally but the server stopped sending
     *    State. The Syncplay server gives up after ~10–15 unanswered State broadcasts,
     *    matching this 15s threshold.
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

        // React to engine-reported pause changes via the StateFlow they all update.
        // Suppress the very first emission (the StateFlow's current value at collection
        // time, not a change) and any emission matching our [expectedPaused] expectation.
        playbackBroadcastJob = viewmodel.viewModelScope.launch(Dispatchers.IO) {
            var seenInitial = false
            viewmodel.playerManager.isNowPlaying.collect { isPlaying ->
                if (!seenInitial) {
                    seenInitial = true
                    return@collect
                }
                if (network.state.value != ConnectionState.CONNECTED) return@collect
                if (lastGlobalUpdate == null) return@collect
                if (isRoomChanging) return@collect

                val expectedPlaying = !expectedPaused
                if (isPlaying == expectedPlaying) return@collect

                // Engine-driven pause/resume (buffer underrun, audio focus loss, EOF
                // transitions, etc.). Tell the room and update our expectation so we
                // don't re-broadcast on the next emission.
                expectedPaused = !isPlaying
                viewmodel.networkManager.sendAsync(
                    buildStatePacket(
                        serverTime = null,
                        doSeek = null,
                        position = reportableStatePositionSec(),
                        isLocalStateChange = true,
                        play = isPlaying
                    )
                )
            }
        }
    }

    /** Cancels the channel-health coroutines. Safe to call multiple times. */
    fun stopChannelHealthMonitoring() {
        watchdogJob?.cancel()
        watchdogJob = null
        listProbeJob?.cancel()
        listProbeJob = null
        playbackBroadcastJob?.cancel()
        playbackBroadcastJob = null
    }

    /**
     * Records what we expect the player's pause state to be after a deliberate change —
     * a user action via [RoomEventDispatcher.controlPlayback] or a server-driven state
     * applied by [RoomServerMessageHandler]. The flow collector in
     * [startChannelHealthMonitoring] uses this to suppress its own re-broadcast: when
     * the engine's resulting `isNowPlaying` callback fires, it matches expectation and
     * is treated as the natural follow-on of the action that just happened.
     *
     * Must be called BEFORE invoking the player's pause/play (or before the engine has
     * a chance to fire its callback for a server-driven change), otherwise there's a
     * brief window where the flow collector sees the engine update against a stale
     * expectation and broadcasts a redundant State packet.
     */
    fun noteExpectedPlaybackState(paused: Boolean) {
        expectedPaused = paused
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
        awaitingRoomResyncDeadline = null
        pingService = PingService()
    }

    /**
     * Lightweight per-connection reset for a TRANSIENT reconnect (NOT a room change or teardown,
     * which use [invalidate]). Clears only the sync anchor and the ignoringOnTheFly counters so
     * the first server `State` on the new socket re-triggers the first-sync re-anchor in
     * RoomServerMessageHandler (force-seek to the authoritative room position + re-apply
     * pause/play). Without this, [lastGlobalUpdate] stays non-null across the reconnect, the
     * re-anchor is skipped, and sub-rewind-threshold (<4s) drift can persist when the server's
     * rejoin `State` is self/null-attributed. Mirrors PC's `_performRetryStateReset`.
     *
     * Deliberately leaves [session] (userlist/playlist must survive), the player, [speedChanged]
     * / [behindFirstDetected] (the slowdown/fastforward state self-heals via the normal sync
     * algorithm once States resume), and [pingService] intact.
     */
    fun resetSyncAnchorForReconnect() {
        lastGlobalUpdate = null
        lastGlobalPositionSetAt = null
        serverIgnFly = 0
        clientIgnFly = 0
    }

    /**
     * Re-arms the one-shot first-sync so the NEXT inbound server `State` hard-seeks the
     * freshly-loaded file to the room's authoritative position and applies its pause/play state
     * (the `lastGlobalUpdate == null` branch in [app.room.RoomServerMessageHandler.onState]).
     *
     * Called exactly once per newly-loaded file, from [app.player.PlayerImpl.announceFileLoaded]
     * (the engine's confirmed file-loaded / duration-known callback, so the engine is seekable by
     * the time the next `State` arrives). Without it, a client that receives even ONE `State`
     * while its media is still loading latches [lastGlobalUpdate] non-null — it is stamped on
     * every processed `State`, with or without media — so by the time the file is ready the
     * first-sync is permanently skipped: the new file sits at position 0, paused, and in a normal
     * room nothing pulls it forward (rewind only fires when AHEAD, fastforward is off in normal
     * rooms). The server then adopts this watcher as the slowest member and rewinds everyone else
     * back to it. On the desktop client the external player layer hides this by reporting
     * `getGlobalPosition()` while no file is loaded and force-seeking on file open; the embedded
     * player has no such layer, so this re-anchor restores the same intent.
     *
     * Clears ONLY [lastGlobalUpdate]. Deliberately NOT the `ignoringOnTheFly` counters (a
     * mid-session load must keep tracking in-flight local state changes) nor [lastGlobalPositionSetAt]
     * (harmless to keep; only feeds position extrapolation). That narrower scope is what separates
     * this from [resetSyncAnchorForReconnect], which resets more because the socket itself changed.
     */
    fun reanchorSyncOnFileLoad() {
        lastGlobalUpdate = null
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
     * The position (seconds) to ADVERTISE to the server in any outbound `State`, mirroring the
     * desktop client's `getCalculatedPosition` (players/mpv.py): while a freshly-loaded file has not
     * yet caught up to the room (see [awaitingRoomResyncDeadline]), report the room's extrapolated position
     * instead of the local engine's (which sits at ~0 right after a load). Once the engine converges
     * — or the file is provably too short to ever reach the room position — the flag clears and the
     * true local position is reported, so genuine desync (buffering) is still visible to the room.
     *
     * Reads the engine position on the main thread, so callers may invoke it from any coroutine.
     */
    suspend fun reportableStatePositionSec(): Double {
        val globalMs = extrapolatedGlobalPositionMs()
        // No file → nothing local to report; advertise the room position (the server pushes
        // file-less watchers last anyway, but this keeps us from ever announcing a bare 0).
        if (viewmodel.media == null) return globalMs / 1000.0

        // Single volatile read: non-null = still masking, null = report the real local position.
        val deadline = awaitingRoomResyncDeadline

        val localMs = withContext(Dispatchers.Main.immediate) {
            viewmodel.player.currentPositionMs()
        }.toDouble()

        if (deadline == null) return localMs / 1000.0

        // Catching up from a fresh load. Keep masking with the room position until the engine has
        // converged, or until it has reached a point past which it cannot catch up (file shorter
        // than the room position — e.g. a mismatched file), or the backstop deadline passes, so we
        // don't lie about position forever.
        val durationMs = viewmodel.playerManager.timeFullMillis.value.toDouble()
        val thresholdMs = SEEK_THRESHOLD * 1000.0
        val converged = abs(localMs - globalMs) <= thresholdMs
        val cannotCatchUp = durationMs > 0.0 && globalMs >= durationMs - thresholdMs
        val timedOut = Clock.System.now() >= deadline
        if (converged || cannotCatchUp || timedOut) {
            awaitingRoomResyncDeadline = null
            return localMs / 1000.0
        }
        return globalMs / 1000.0
    }

    /**
     * Arms the position-masking window for a freshly-loaded file. Called from
     * [app.player.PlayerImpl.parseMedia] BEFORE `media.value` is set, so an inbound-State ACK can
     * never observe media!=null with the mask still disarmed. See [awaitingRoomResyncDeadline].
     */
    fun markAwaitingRoomResync() {
        awaitingRoomResyncDeadline = Clock.System.now() + AWAITING_ROOM_RESYNC_TIMEOUT_SECONDS.seconds
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
        isLocalStateChange: Boolean,
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

        if (isLocalStateChange) {
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

        /**
         * The compatibility shim PC clients put in `Hello.version` (protocols.py:165:
         * `hello["version"] = "1.2.255"  # Used so newer clients work on 1.2.X server`).
         * Legacy 1.2.X servers only read `version`; modern servers prefer `realversion`.
         */
        const val SYNCPLAY_LEGACY_VERSION = "1.2.255"

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

        /** Max seconds a freshly-loaded file may advertise the room position instead of its own
         * while catching up, before [reportableStatePositionSec] reverts to the true local
         * position even if it never converged (mismatched/short file, slow device). */
        const val AWAITING_ROOM_RESYNC_TIMEOUT_SECONDS = 30L
    }
}
