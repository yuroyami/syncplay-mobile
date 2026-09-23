package app.protocol

import androidx.lifecycle.viewModelScope
import app.AbstractManager
import app.player.models.MediaFile
import app.protocol.models.ConnectionState
import app.protocol.models.ClockOffsetEstimator
import app.preferences.Preferences
import app.preferences.value
import app.protocol.models.PingService
import app.protocol.wire.IgnoringOnTheFlyData
import app.protocol.wire.PingData
import app.protocol.wire.PlaystateData
import app.protocol.wire.StateData
import app.room.RoomViewmodel
import app.protocol.sync.SyncState
import app.protocol.sync.LocalSeek
import app.protocol.sync.LocalStateIntent
import app.protocol.sync.LocalStateIntents
import app.protocol.sync.reportablePosition
import app.protocol.sync.extrapolatedGlobalPositionMs
import app.protocol.sync.PositionInputs
import app.protocol.sync.PendingSeekPosition
import app.protocol.sync.PendingSeekPositions
import app.protocol.sync.SyncAction
import app.utils.SyncClock
import app.utils.ioDispatcher
import app.utils.loggy
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ProtocolManager(val viewmodel: RoomViewmodel) : AbstractManager(viewmodel) {

    var session: Session = Session(this)

    var globalPaused: Boolean = true
    var globalPositionMs: Double = 0.0

    /**
     * When [globalPositionMs] was last set from a server `State`, the message that carries the
     * room's position and pause state. A room is the group of people watching together. Used to
     * extrapolate the room's *current* expected position via [extrapolatedGlobalPositionMs].
     */
    var lastGlobalPositionSetAt: Instant? = null

    /**
     * The server's `ignoringOnTheFly` counter, adopted from an inbound `State` and echoed back
     * once in the next outbound one. Updated atomically.
     */
    private val _serverIgnFly = atomic(0)
    var serverIgnFly: Int
        get() = _serverIgnFly.value
        set(value) { _serverIgnFly.value = value }

    /**
     * Our own `ignoringOnTheFly` counter. It counts local state changes until the server
     * acknowledges the latest one, and while it is non-zero, inbound playstates are ignored.
     * Packet construction and enqueue share [syncLock] with inbound counter updates; the atomic
     * also serves readers outside that lock.
     */
    private val _clientIgnFly = atomic(0)
    var clientIgnFly: Int
        get() = _clientIgnFly.value
        set(value) { _clientIgnFly.value = value }

    /**
     * PC's `DEFAULT_REWIND_THRESHOLD` (constants.py): how many seconds ahead of the room a client
     * may drift before it is rewound. The live threshold comes from the SYNC_REWIND_THRESHOLD
     * preference, which keeps PC's `MINIMUM_REWIND_THRESHOLD` of 3 seconds.
     */
    val rewindThreshold = 4L

    /**
     * When a server `State` playstate was last applied. `null` arms the one-shot first sync: the
     * next inbound `State` seeks to the room position and applies the room's pause state (decided
     * in [app.protocol.sync.decideSync], applied by [app.room.RoomServerMessageHandler.onState]).
     *
     * `@Volatile` because the inbound `State` consumer (Dispatchers.Default in NetworkManager)
     * reads it, while other threads write it: [resetSyncAnchorForReconnect] from the reconnect
     * loop, and [reanchorSyncOnFileLoad] from a player engine's load callback (Main, mpv IO or a
     * VLCKit delegate). Without it, the consumer can keep seeing a stale non-null value on ARM and
     * silently skip the re-anchor. [lastStateReceivedAt] is volatile for the same reason.
     */
    @Volatile
    var lastGlobalUpdate: Instant? = null

    /**
     * Position masking for a freshly loaded file. Non-null (a future instant) means the file is
     * still catching up, and every outbound `State` advertises the room's extrapolated position
     * instead of the engine's own. The engine sits at about 0 for the first second or so after a
     * load, until the first-sync seek lands and the engine converges. `null` means report the
     * true local position (not loading, or already caught up).
     *
     * This is the embedded-player version of the desktop client's `getCalculatedPosition`
     * (players/mpv.py), which returns `getGlobalPosition()` while `fileLoaded == False`. Without
     * it, a late loader reports about 0 as soon as it attaches a file. The official server adopts
     * it as the slowest watcher and broadcasts about 0, every other client rewinds to 0, and the
     * loader's own rewind logic pulls it to 0 too. The first sync from [reanchorSyncOnFileLoad]
     * then seeks to a position that the loader itself just corrupted.
     *
     * [reportableStatePositionSec] clears it (back to null) once the engine converges, once the
     * file proves unable to ever reach the room position, or once this deadline passes. From then
     * on the true local position is reported again, so a real standing desync (buffering) stays
     * visible to the room.
     *
     * One field on purpose, not an (armed, deadline) pair: a reader on the inbound `State` thread
     * must never see a half-written "armed but no deadline" state and take it for a timeout. That
     * would unmask and advertise the engine's 0, the exact value this masking exists to hide.
     * `@Volatile` for the same cross-thread reason as [lastGlobalUpdate]: [markAwaitingRoomResync]
     * writes it on the main thread, and the inbound `State` consumer reads it.
     */
    @Volatile
    var awaitingRoomResyncDeadline: Instant? = null

    var pingService = PingService()

    /**
     * How far our clock sits from the server's, from the timestamps already on the wire.
     *
     * Nothing acts on it yet. It is only measured and logged, so a real two-device session can
     * check whether the numbers are sane.
     */
    val clockOffset = ClockOffsetEstimator()

    /** Tracks whether playback speed has been adjusted for desync correction. */
    var speedChanged = false

    /**
     * When this client first fell behind the room. Null when it is not behind. After a
     * fast-forward it is set in the future, as a cooldown.
     */
    var behindFirstDetected: Instant? = null

    /**
     * The whole sync anchor (the room state carried from one `State` to the next) as one value,
     * which is what [app.protocol.sync.decideSync] reads and returns. The individual fields stay
     * because the rest of the room reads them by name.
     */
    var syncState: SyncState
        get() = SyncState(
            serverIgnFly = serverIgnFly,
            clientIgnFly = clientIgnFly,
            globalPaused = globalPaused,
            globalPositionMs = globalPositionMs,
            lastGlobalPositionSetAt = lastGlobalPositionSetAt,
            lastGlobalUpdate = lastGlobalUpdate,
            behindFirstDetected = behindFirstDetected,
            speedChanged = speedChanged,
        )
        set(value) {
            serverIgnFly = value.serverIgnFly
            clientIgnFly = value.clientIgnFly
            globalPaused = value.globalPaused
            globalPositionMs = value.globalPositionMs
            lastGlobalPositionSetAt = value.lastGlobalPositionSetAt
            lastGlobalUpdate = value.lastGlobalUpdate
            behindFirstDetected = value.behindFirstDetected
            speedChanged = value.speedChanged
        }

    /**
     * Held across a read-modify-write of the sync anchor.
     *
     * The anchor is eight fields projected into one [SyncState] and written back whole, so a
     * concurrent write to any one of them between the read and the write is lost. The window is
     * short, but the writers are a reconnect, a file load and the outbound State builder, all of
     * which run on their own threads. Nothing inside the lock suspends: the decision is a pure
     * function and the reads around it are plain.
     */
    val syncLock = SynchronizedObject()

    private val localStateIntents = LocalStateIntents()
    private val pendingSeekPositions = PendingSeekPositions()
    private var intentMedia: MediaFile? = null
    private var intentRoom: String? = null

    val localStateRevision: Long get() = synchronized(syncLock) { localStateIntents.revision }

    fun isLocalStateRevisionCurrent(revision: Long): Boolean = synchronized(syncLock) {
        localStateIntents.isCurrent(revision)
    }

    /**
     * Drops queued local state after a media or room change. A queued local seek belongs to the
     * media and room it was made for, never to a replacement.
     */
    private fun refreshLocalIntentContext() {
        if (intentMedia !== viewmodel.media || intentRoom != session.currentRoom) {
            localStateIntents.clear()
            pendingSeekPositions.clear()
            intentMedia = viewmodel.media
            intentRoom = session.currentRoom
        }
    }

    fun clearLocalStateIntents() = synchronized(syncLock) {
        localStateIntents.clear()
        pendingSeekPositions.clear()
        intentMedia = viewmodel.media
        intentRoom = session.currentRoom
    }

    /**
     * Records a local state change and enqueues its `State` in one step under [syncLock]. The
     * network writer does the IO, so nothing here needs its own IO coroutine.
     */
    fun sendLocalState(position: Double, play: Boolean, seek: LocalSeek? = null) = synchronized(syncLock) {
        if (viewmodel.isSoloMode) return@synchronized
        refreshLocalIntentContext()
        pendingSeekPositions.clear()
        localStateIntents.offer(LocalStateIntent(position, play, seek))
        flushPendingLocalState(serverTime = null)
        Unit
    }

    /**
     * Sends the queued local state if the `ignoringOnTheFly` gate allows it, and says whether it
     * did. onState calls it after adopting the inbound counters, before deciding corrections.
     */
    fun flushPendingLocalState(serverTime: Double?): Boolean = synchronized(syncLock) {
        refreshLocalIntentContext()
        val intent = localStateIntents.takeReady(clientIgnFly == 0 || serverIgnFly != 0)
            ?: return@synchronized false
        val packet = buildStatePacket(serverTime, intent.seek?.let { true }, intent.positionSeconds, true, intent.playing)
        localStateIntents.sent(intent, clientIgnFly, SyncClock.nowMillis())
        viewmodel.networkManager.sendAsync(packet)
        true
    }

    /**
     * Matches an inbound self-seek echo to the seek we sent, and returns that seek's origin. Runs
     * before a newly flushed seek can replace the in-flight seek's metadata.
     */
    fun consumeLocalSeekEcho(state: StateData): LocalSeek? = synchronized(syncLock) {
        refreshLocalIntentContext()
        val playstate = state.playstate
        val seek = if (playstate?.doSeek == true && playstate.setBy == session.currentUsername) {
            localStateIntents.consumeSeekEcho(
                playstate.position ?: 0.0, state.ignoringOnTheFly?.client, SyncClock.nowMillis(),
            )
        } else null
        // An old, unrelated self-seek echo can reuse counter 1 after a later send. When the
        // echoed target does not match, keep the later seek's origin.
        if (clientIgnFly == 0 && !(playstate?.doSeek == true && playstate.setBy == session.currentUsername)) {
            localStateIntents.forgetAcknowledgedSeek()
        }
        seek
    }

    /**
     * Sends an acknowledgement `State`. The gate, the counter snapshot and the enqueue run under
     * the same lock as inbound `State` handling.
     */
    fun sendStateAcknowledgement(serverTime: Double?, position: Double?, play: Boolean?) = synchronized(syncLock) {
        viewmodel.networkManager.sendAsync(buildStatePacket(serverTime, null, position, false, play))
    }

    val isSeekPending: Boolean get() = synchronized(syncLock) { pendingSeekPositions.current != null }

    fun queueSeekPosition(action: SyncAction): PendingSeekPosition? = synchronized(syncLock) {
        refreshLocalIntentContext()
        if (viewmodel.media == null || viewmodel.uiState.isInBackground) return@synchronized null
        pendingSeekPositions.begin(action, session.currentUsername)
    }

    fun completeSeekPosition(command: PendingSeekPosition) = synchronized(syncLock) {
        pendingSeekPositions.complete(command)
    }

    /**
     * Set during a room transition, so the events it causes are not broadcast as divergence.
     *
     * Owned by [beginRoomChange] and [endRoomChange], with a timeout, rather than a bare flag. A
     * room creation that the server refuses, or whose answer never arrives, must not leave it
     * true for the rest of the session and mute every divergence broadcast with nothing on
     * screen to explain it.
     */
    var isRoomChanging = false
        private set

    private var roomChangeWatchdog: Job? = null

    /** Mutes divergence broadcasts until [endRoomChange], or for five seconds, whichever is first. */
    fun beginRoomChange() {
        isRoomChanging = true
        roomChangeWatchdog?.cancel()
        roomChangeWatchdog = viewmodel.viewModelScope.launch {
            delay(ROOM_CHANGE_TIMEOUT)
            isRoomChanging = false
        }
    }

    fun endRoomChange() {
        roomChangeWatchdog?.cancel()
        roomChangeWatchdog = null
        isRoomChanging = false
    }

    val supportsChat = MutableStateFlow(true)
    val supportsManagedRooms = MutableStateFlow(false)
    val supportsSharedPlaylists = MutableStateFlow(true)

    val isManagedRoom = MutableStateFlow(false)

    /**
     * When the last `State` arrived from the server. The watchdog uses it to detect a silent
     * disconnect: the socket looks healthy here, but the server has stopped sending `State`
     * packets (common on flaky networks, especially on iOS).
     */
    @Volatile
    var lastStateReceivedAt: Instant? = null

    private var watchdogJob: Job? = null
    private var listProbeJob: Job? = null
    private var playbackBroadcastJob: Job? = null

    /**
     * Our current belief about the player's pause state. The flow collector started by
     * [startChannelHealthMonitoring] watches [PlayerManager.isNowPlaying], which every engine
     * updates from its own native callback (for example ExoPlayer's `Player.Listener`, mpv's
     * property observer, VLCKit's delegate and AVPlayer's KVO). It broadcasts a `State` only when
     * the engine-reported playing state DIVERGES from this expectation. The watchdog tick runs
     * the same check.
     *
     * That way, user-initiated pauses (which already broadcast via
     * [RoomEventDispatcher.controlPlayback]) and server-driven pauses (applied by
     * [RoomServerMessageHandler]) are not broadcast again. The path that updates the player also
     * calls [noteExpectedPlaybackState], so the engine's resulting flow emission matches the
     * expectation. Only engine-driven pauses and resumes (buffer underrun, audio focus loss,
     * EOF) cause an actual broadcast.
     *
     * Driven by the flow rather than by polling: the engines expose event APIs for everything,
     * and a poll that samples a player still converging on a seek target produces phantom seeks.
     */
    @Volatile
    private var expectedPaused: Boolean = true

    /**
     * The room's *intended* play state as the app knows it, with no engine probe. Set
     * synchronously by [noteExpectedPlaybackState] before the player is touched, and by the
     * divergence check. Outbound paths must read this instead of `player.isPlaying()`. On
     * VLCKit 4 that call returns a stale pre-transition value in the async window right after a
     * pause or play, and broadcasting it makes the server think the watcher unpaused the room.
     */
    val expectedPlaying: Boolean get() = !expectedPaused

    /**
     * Starts the channel-health coroutines for the current room session.
     *
     * Three jobs run while connected:
     *  - **List probe**: sends an empty `List` request every [LIST_PROBE_INTERVAL_SECONDS], so
     *    the server has to answer. This keeps the connection active and finds a broken socket
     *    early: a write that keeps failing makes NetworkManager report the lost socket, which
     *    starts a reconnect.
     *  - **State watchdog**: runs every [WATCHDOG_INTERVAL_SECONDS]. If no `State` has arrived
     *    for [STATE_TIMEOUT_SECONDS] seconds while we still count as connected, it calls
     *    `onDisconnected()`, which starts a reconnect. This detects a silent disconnect, where
     *    the socket looks healthy here but the server has stopped sending `State`. The
     *    reference client gives up on a silent server after 12.5 seconds (`PROTOCOL_TIMEOUT`),
     *    and the reference server drops a silent watcher after the same time.
     *  - **Playback divergence**: collects [PlayerManager.isNowPlaying] and broadcasts
     *    engine-driven pause changes (see [expectedPaused]).
     *
     * Does nothing in solo mode (watching alone, with no server). Only onConnected() calls it,
     * so that check is a backup.
     */
    fun startChannelHealthMonitoring() {
        if (viewmodel.isSoloMode) return
        stopChannelHealthMonitoring()

        // Seed the watchdog so it doesn't immediately fire before any State arrives.
        lastStateReceivedAt = SyncClock.now()

        val network = viewmodel.networkManager

        listProbeJob = viewmodel.viewModelScope.launch(ioDispatcher) {
            while (isActive) {
                delay(LIST_PROBE_INTERVAL_SECONDS.seconds)
                if (network.state.value == ConnectionState.CONNECTED) {
                    // Fire-and-forget. Awaiting the write would start the next interval only when
                    // the previous write landed, so a slow socket would stretch the 15 s probe
                    // interval well past the server's own tolerance.
                    network.sendAsync(WireMessage.listRequest())
                }
            }
        }

        watchdogJob = viewmodel.viewModelScope.launch(ioDispatcher) {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_SECONDS.seconds)
                if (network.state.value != ConnectionState.CONNECTED) continue

                // The collector below only fires on a change. An engine that came up in the wrong
                // state, or a flip that arrived while nothing was collecting, would sit there
                // silently, so the same comparison runs on this tick too.
                broadcastPlaybackDivergence(viewmodel.playerManager.isNowPlaying.value)

                /* Logs the clock estimate on every tick once it has settled. Nothing acts on it;
                 * the log lets a real two-device session check whether it is sane. */
                if (clockOffset.settled) {
                    loggy(
                        "Clock offset: ${(clockOffset.offsetSeconds * 1000).toInt()}ms " +
                            "(best round trip ${(clockOffset.bestRoundTripSeconds * 1000).toInt()}ms, " +
                            "spread ${(clockOffset.dispersionSeconds * 1000).toInt()}ms)"
                    )
                }

                val last = lastStateReceivedAt ?: continue
                val elapsedSec = (SyncClock.now() - last).inWholeSeconds
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
        playbackBroadcastJob = viewmodel.viewModelScope.launch(ioDispatcher) {
            var seenInitial = false
            viewmodel.playerManager.isNowPlaying.collect { isPlaying ->
                if (!seenInitial) {
                    seenInitial = true
                    return@collect
                }
                if (network.state.value != ConnectionState.CONNECTED) return@collect
                if (lastGlobalUpdate == null) return@collect
                if (isRoomChanging) return@collect
                // A backgrounded client is paused locally and catches up on return. Nothing the
                // engine does in the background is news for the room.
                if (viewmodel.uiState.isInBackground) return@collect

                val expectedPlaying = !expectedPaused
                broadcastPlaybackDivergence(isPlaying)
            }
        }
    }

    /**
     * Engine-driven pause or resume (buffer underrun, audio focus loss, EOF): tells the room and
     * updates the expectation, so neither the collector nor the watchdog says it twice. Anything
     * the app does on purpose notes its expectation first, so it matches here and sends nothing.
     */
    private fun broadcastPlaybackDivergence(isPlaying: Boolean) {
        if (viewmodel.networkManager.state.value != ConnectionState.CONNECTED) return
        if (lastGlobalUpdate == null || isRoomChanging) return
        if (viewmodel.uiState.isInBackground) return
        if (isPlaying == expectedPlaying) return

        expectedPaused = !isPlaying
        sendLocalState(position = reportableStatePositionSec(), play = isPlaying)
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
     * Records the pause state the player should reach after a deliberate change: a user action
     * via [RoomEventDispatcher.controlPlayback], or a server-driven state applied by
     * [RoomServerMessageHandler]. The flow collector in [startChannelHealthMonitoring] uses it to
     * skip its own broadcast: the engine's resulting `isNowPlaying` update matches the
     * expectation and counts as the result of the action that just happened.
     *
     * Call it BEFORE the player's pause or play (or before the engine can fire its callback for
     * a server-driven change). Otherwise the collector briefly sees the engine update against a
     * stale expectation and broadcasts a redundant `State`.
     */
    fun noteExpectedPlaybackState(paused: Boolean) {
        expectedPaused = paused
    }

    override fun invalidate() {
        stopChannelHealthMonitoring()
        clearLocalStateIntents()
        lastStateReceivedAt = null
        lastGlobalUpdate = null
        session = Session(this)
        globalPaused = true
        globalPositionMs = 0.0
        lastGlobalPositionSetAt = null
        serverIgnFly = 0
        clientIgnFly = 0
        speedChanged = false
        behindFirstDetected = null
        isRoomChanging = false
        awaitingRoomResyncDeadline = null
        expectedPaused = true
        pingService = PingService()
    }

    /**
     * Called when the app returns to the foreground after a background pause. The room moved on
     * meanwhile, so the next `State` must seek and re-apply pause or play exactly like a fresh
     * file load. Until the player converges, the ACK advertises the room position, not ours.
     */
    fun resumeFromBackground() {
        if (viewmodel.isSoloMode || viewmodel.media == null) return
        markAwaitingRoomResync()
        reanchorSyncOnFileLoad()
    }

    /**
     * Light per-connection reset for a TRANSIENT reconnect. A room change or a teardown uses
     * [invalidate] instead. Clears the queued local state, any room change in progress, the
     * clock offset estimate, [lastGlobalUpdate], [lastGlobalPositionSetAt] and both
     * `ignoringOnTheFly` counters. The first server `State` on the new socket then runs the
     * first-sync re-anchor again (seek to the room position and re-apply its pause state).
     * Without this, [lastGlobalUpdate] stays non-null across the reconnect, the re-anchor is
     * skipped, and drift under the rewind threshold (4 s by default) can persist when the
     * server's rejoin `State` is attributed to us or to nobody. Mirrors PC's
     * `_performRetryStateReset`.
     *
     * Deliberately keeps [session] (the user list and playlist must survive), the player,
     * [speedChanged] and [behindFirstDetected] (the normal sync algorithm corrects the slowdown
     * and fast-forward state once `State` packets resume), and [pingService].
     */
    fun resetSyncAnchorForReconnect() = synchronized(syncLock) {
        clearLocalStateIntents()
        endRoomChange()
        clockOffset.reset()
        lastGlobalUpdate = null
        lastGlobalPositionSetAt = null
        serverIgnFly = 0
        clientIgnFly = 0
    }

    /**
     * Re-arms the one-shot first sync, so the NEXT inbound server `State` seeks the newly loaded
     * file to the room's position and applies the room's pause state (the
     * `lastGlobalUpdate == null` branch of [app.protocol.sync.decideSync]).
     *
     * Called once per newly loaded file, from [app.player.PlayerImpl.announceFileLoaded]: the
     * engine has confirmed the load or knows the duration, so it can seek by the time the next
     * `State` arrives. Without this call, one `State` that arrives while the media is still
     * loading sets [lastGlobalUpdate] (every applied `State` sets it, with or without media). The
     * first sync is then skipped for good: the new file sits paused at position 0, and in a
     * normal room nothing pulls it forward (rewind only fires when AHEAD, and fast-forward is off
     * in a normal room unless dontSlowWithMe is set). The server then adopts this watcher as the
     * slowest member and rewinds everyone else back to it. The desktop client avoids this in its
     * external player layer, which reports `getGlobalPosition()` while no file is loaded and
     * seeks on file open. The embedded player has no such layer, so this re-anchor does the same
     * job.
     *
     * Clears ONLY [lastGlobalUpdate]. It keeps the `ignoringOnTheFly` counters (a mid-session load
     * must keep tracking in-flight local state changes) and [lastGlobalPositionSetAt] (it only
     * feeds position extrapolation). [resetSyncAnchorForReconnect] resets more, because there the
     * socket itself changed.
     */
    fun reanchorSyncOnFileLoad() = synchronized(syncLock) {
        lastGlobalUpdate = null
    }

    /** Everything [reportablePosition] needs, gathered from the room. */
    private fun positionInputs() = PositionInputs(
        now = SyncClock.now(),
        globalPositionMs = globalPositionMs,
        globalPositionSetAt = lastGlobalPositionSetAt,
        globalPaused = globalPaused,
        hasMedia = viewmodel.media != null,
        isInBackground = viewmodel.uiState.isInBackground,
        localPositionMs = viewmodel.playerManager.estimatedPositionMs().toDouble(),
        durationMs = viewmodel.playerManager.timeFullMillis.value.toDouble(),
        awaitingRoomResyncDeadline = awaitingRoomResyncDeadline,
        userOffsetSeconds = userTimeOffsetSeconds(),
        pendingSeekPositionMs = pendingSeekPositions.current?.targetMs,
    )

    /**
     * How far this viewer's copy runs ahead of the room, in seconds. The slider stores tenths
     * offset by 600, so its middle is no shift at all.
     *
     * Public because every position path needs it, and a path that forgets it drifts by exactly
     * the amount the offset was set to remove.
     */
    fun userTimeOffsetSeconds(): Double = (Preferences.USER_TIME_OFFSET.value() - 600) / 10.0

    /**
     * The room's *current* expected position in ms, extrapolated from the last server `State`.
     * While the room plays, it advances by the wall-clock time since [lastGlobalPositionSetAt].
     * Mirrors Python's `getGlobalPosition()`. Without the extrapolation, a SYNC_ON_PAUSE seek
     * lands on a stale frame from the last 1 Hz tick.
     */
    fun extrapolatedGlobalPositionMs(): Double = synchronized(syncLock) {
        extrapolatedGlobalPositionMs(positionInputs())
    }

    fun reportableStatePositionSec(): Double = synchronized(syncLock) {
        refreshLocalIntentContext()
        val report = reportablePosition(positionInputs())
        // Single volatile write, and only to disarm: see the field's own note on why this is
        // one field rather than a pair.
        if (!report.keepMasking) awaitingRoomResyncDeadline = null
        report.positionSeconds
    }

    fun markAwaitingRoomResync() {
        clearLocalStateIntents()
        awaitingRoomResyncDeadline = SyncClock.now() + AWAITING_ROOM_RESYNC_TIMEOUT_SECONDS.seconds
    }

    /**
     * Builds an outbound `State` packet, with the same `ignoringOnTheFly` bookkeeping as the
     * Python reference client (it changes [serverIgnFly] and [clientIgnFly] as side effects).
     *
     * [position] goes on the wire as full-precision seconds (Double). Never round it to whole
     * seconds: the server's desync detection needs sub-second precision, and so does
     * `min(watchers)`, which must not pick us as the slowest.
     */
    private fun buildStatePacket(
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
            clientLatencyCalculation = SyncClock.nowSeconds(),
            clientRtt = pingService.rtt
        )

        // The increment and both reads are one step: a handler writing the anchor back between
        // them would otherwise see a count that never existed.
        val (snapshotServer, snapshotClient) = synchronized(syncLock) {
            if (isLocalStateChange) _clientIgnFly.incrementAndGet()
            _serverIgnFly.value to _clientIgnFly.value
        }
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
         * The Syncplay protocol version advertised in `Hello.realversion`.
         *
         * 1.7.5 is `RECENT_CLIENT_THRESHOLD` in PC's constants.py. For any lower version, the
         * reference server adds a "new version available" warning to its message of the day.
         * The client implements every protocol feature up to 1.7.5 (managedRooms, readiness,
         * setOthersReadiness, sharedPlaylists, chat), so the claim is accurate.
         */
        const val SYNCPLAY_PROTOCOL_VERSION = "1.7.5"

        /**
         * The compatibility value PC clients put in `Hello.version` (`sendHello` in protocols.py:
         * `hello["version"] = "1.2.255"  # Used so newer clients work on 1.2.X server`).
         * Old 1.2.X servers only read `version`; newer servers prefer `realversion`.
         */
        const val SYNCPLAY_LEGACY_VERSION = "1.2.255"

        /** PC's `SEEK_THRESHOLD`, in seconds: a smaller player jump does not count as a seek. */
        const val SEEK_THRESHOLD = 1L

        /** Playback speed while this client is ahead, so the rest of the room can catch up. */
        const val SLOWDOWN_RATE = app.protocol.sync.SLOWDOWN_RATE

        /** Default time difference, in seconds, at which slowdown starts. */
        const val SLOWDOWN_THRESHOLD = app.protocol.sync.SLOWDOWN_THRESHOLD

        /** Time difference, in seconds, at which the speed returns to normal. */
        const val SLOWDOWN_RESET_THRESHOLD = app.protocol.sync.SLOWDOWN_RESET_THRESHOLD

        /** Default time behind the room, in seconds, at which the fast-forward timer starts. */
        const val FASTFORWARD_BEHIND_THRESHOLD = app.protocol.sync.FASTFORWARD_BEHIND_THRESHOLD

        /** Default time behind the room, in seconds, at which fast-forward fires after the wait. */
        const val FASTFORWARD_THRESHOLD = app.protocol.sync.FASTFORWARD_THRESHOLD

        /** Extra seconds added to a fast-forward target, to overshoot slightly. */
        const val FASTFORWARD_EXTRA_TIME = app.protocol.sync.FASTFORWARD_EXTRA_TIME

        /** Cooldown in seconds after a fast-forward, before it can fire again. */
        const val FASTFORWARD_RESET_THRESHOLD = app.protocol.sync.FASTFORWARD_RESET_THRESHOLD

        /** How often the list probe sends an empty `List`, to keep the connection active. */
        const val LIST_PROBE_INTERVAL_SECONDS = 15L

        /** How often the State watchdog checks whether the server has gone silent. */
        const val WATCHDOG_INTERVAL_SECONDS = 5L

        /**
         * If no `State` has arrived for this many seconds, the channel counts as broken and a
         * reconnect starts. Set just above the reference `PROTOCOL_TIMEOUT` of 12.5 seconds,
         * after which the reference client gives up on a silent server and the reference server
         * drops a silent watcher.
         */
        const val STATE_TIMEOUT_SECONDS = 15L

        /**
         * How long a room transition may mute divergence broadcasts before it stops waiting for
         * the outcome. Well above a round trip, and short enough that a lost answer goes
         * unnoticed.
         */
        val ROOM_CHANGE_TIMEOUT = 5.seconds

        /**
         * Maximum seconds a freshly loaded file may advertise the room position instead of its
         * own while catching up. After that, [reportableStatePositionSec] reports the true local
         * position even if the engine never converged (a mismatched or short file, a slow device).
         */
        const val AWAITING_ROOM_RESYNC_TIMEOUT_SECONDS = 30L
    }
}
