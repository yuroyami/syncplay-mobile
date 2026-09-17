package app.room

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.Screen
import app.home.JoinConfig
import app.i18n.Localization
import app.player.PlayerImpl
import app.player.PlayerManager
import app.player.models.MediaFile
import app.preferences.Preferences
import app.preferences.set
import app.preferences.value
import app.protocol.ProtocolManager
import app.protocol.Session
import app.protocol.resolveServerEndpoint
import app.protocol.event.RoomCallback
import app.protocol.event.RoomEventDispatcher
import app.protocol.network.NetworkManager
import app.room.sharedplaylist.SharedPlaylistManager
import app.utils.FileComparison
import app.utils.availablePlatformPlayerEngines
import app.utils.instantiateNetworkManager
import app.uicomponents.frames.NoticeQueue
import app.uicomponents.frames.NoticeSeverity
import app.utils.ioDispatcher
import app.utils.loggy
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Classification used by the categorized [RoomViewmodel.dispatchOSD] overload to decide whether
 * an OSD overlay should be shown, based on the user's per-category preferences. Mirrors the
 * five toggles in Syncplay PC's "Messages" settings tab. Note: the NON_OPERATOR layer is applied
 * implicitly by the dispatcher when a [SAME_ROOM] event has an `originUser` argument and the
 * room is operator-controlled — there is no NON_OPERATOR case here on purpose.
 */
enum class OSDCategory {
    /** Pause/play/seek/join/leave/file-load events from a user in your current room. */
    SAME_ROOM,
    /** Activity from a different room (peer's chat / cross-room presence). */
    OTHER_ROOM,
    /** Local sync mechanism notices (slowing down, rewinding, fast-forwarding to catch up). */
    SLOWDOWN,
    /** Red warnings: file mismatch, alone-in-room, connection errors, etc. */
    WARNING
}

/**
 * ViewModel for the Syncplay room screen where synchronized playback occurs.
 *
 * Coordinates all room-level managers including networking, media playback, protocol handling,
 * session state, and user interactions. Supports both online synchronized rooms and solo mode.
 *
 * @property joinConfig The room connection configuration, or null for solo mode
 * @property backStack The navigation stack for leaving the room
 */
class RoomViewmodel(val joinConfig: JoinConfig?, val backStack: SnapshotStateList<Screen>) : ViewModel() {

    /************ Managers ***************/

    /** Manages and holds UI state for the room screen */
    val uiState: RoomUiStateManager by lazy { RoomUiStateManager(this) }

    /** Manages media player lifecycle, controls, and state */
    val playerManager: PlayerManager by lazy { PlayerManager(this) }

    /**
     * Manages the network connection and communication with the Syncplay server. Built on first
     * touch rather than inside a coroutine: as a lateinit assigned from a launch, anything that
     * reached it first (a callback, the dispatcher, a fast leave) threw.
     */
    private val networkManagerHolder = lazy { instantiateNetworkManager() }
    val networkManager: NetworkManager by networkManagerHolder

    /** Manages the Syncplay protocol and its events */
    val protocol: ProtocolManager by lazy { ProtocolManager(this) }

    /** Manages callbacks from protocol events (e.g., when someone pauses) - receiving actions */
    val callback: RoomCallback by lazy { RoomCallback(this) }

    /** Manages actions performed by the user to send to the server - sending actions */
    val dispatcher: RoomEventDispatcher by lazy { RoomEventDispatcher(this) }

    /** Implements [app.protocol.WireMessageHandler] — routes incoming wire messages. */
    val serverHandler: RoomServerMessageHandler by lazy { RoomServerMessageHandler(this) }

    /** Manages the shared playlist and all playlist-related functionality */
    val playlistManager: SharedPlaylistManager by lazy { SharedPlaylistManager(this) }

    /** Who the room is waiting for, and the countdown once it is waiting for nobody. */
    val readiness: ReadinessManager by lazy { ReadinessManager(this) }

    /** Where each file was left, and the offer to pick it up. */
    val resume: ResumeManager by lazy { ResumeManager(this) }

    /**
     * List of seek operations as pairs of (fromPosition, toPosition) in milliseconds.
     * Used for tracking and potentially reverting seek operations.
     */
    val seeks = mutableStateListOf<Pair<Long, Long>>()

    init {
        // From landing in the room to the first packet, in the log. A report of a flaky join
        // needs to say whether the wait was the engine, the dial, or the server.
        val roomEnteredAt = TimeSource.Monotonic.markNow()
        viewModelScope.launch(ioDispatcher) {
            val playerInitialization = launch {
                // The previous room's engine may still be tearing down (mpv's handle is
                // process-global); never build the next one over it.
                PlayerManager.awaitPendingDestroy()
                val preferred = Preferences.PLAYER_ENGINE.value()
                val engine = availablePlatformPlayerEngines
                    .firstOrNull { it.name == preferred && it.isAvailable }
                    ?: availablePlatformPlayerEngines.firstOrNull { it.isAvailable }
                if (engine == null) {
                    // Nothing can play here (desktop with KitePlayer unavailable, for one).
                    loggy("No available player engine on this platform")
                    dispatchOSD(OSDCategory.WARNING) { Localization.strings.roomNoPlayerEngine }
                    dispatcher.broadcastMessage(isChat = false, isError = true) { Localization.strings.roomNoPlayerEngine }
                    return@launch
                }
                if (engine.name != preferred) {
                    // A debug-only engine can disappear when a release build replaces the app.
                    // Persist the effective fallback so the picker never displays a stale,
                    // unselected engine name after that transition.
                    Preferences.PLAYER_ENGINE.set(engine.name)
                }
                playerManager.player = engine.createImpl(this@RoomViewmodel)
                playerManager.isPlayerReady.value = true
                loggy("Room: ${engine.name} ready ${roomEnteredAt.elapsedNow().inWholeMilliseconds}ms after entering the room")
            }

            joinConfig?.let {
                launch {
                    // Initial State/playlist messages can read player capabilities or load
                    // media immediately. Publish the player before opening that inbound path.
                    playerInitialization.join()
                    if (!playerManager.isPlayerReady.value) {
                        // No engine, so no connection is ever attempted. Say so: the room would
                        // otherwise sit there looking disconnected with nothing explaining it.
                        loggy("Room: no player engine, so the room will not connect")
                        return@launch
                    }
                    val endpoint = resolveServerEndpoint(joinConfig.ip)
                    session.tlsPeerHost = endpoint.certificateHost
                    session.serverHost = endpoint.dialHost
                    session.fallbackHost = endpoint.fallbackDialHost
                    session.serverPort = joinConfig.port
                    session.currentUsername = joinConfig.user
                    session.currentRoom = joinConfig.room
                    session.currentPassword = joinConfig.pw
                    // Pasted alongside the room name; onConnected re-identifies with it, so this
                    // also survives every later reconnect.
                    session.currentOperatorPassword = joinConfig.operatorPassword

                    // connect() decides TLS from the settings and this transport, and refuses
                    // outright when encryption is required and cannot be had.
                    loggy("Room: connecting ${roomEnteredAt.elapsedNow().inWholeMilliseconds}ms after entering the room")
                    networkManager.connect()
                }
            }
        }
    }

    /**
     * Exits the current room and returns to the home screen.
     */
    fun goHome() {
        // Leaving is the last chance to write down where this file got to.
        resume.record()
        backStack.removeAt(backStack.lastIndex)
    }

    /**
     * Checks for file mismatches between the local media and other users' files.
     */
    fun checkFileMismatches() {
        if (isSoloMode) return
        if (!Preferences.FILE_MISMATCH_WARNING.value()) return //Return if user doesn't want warnings

        viewModelScope.launch {
            val localMedia = media ?: return@launch //No media is loaded

            for (user in session.userList.value) {
                if (user.name == session.currentUsername) continue //We ain't gonna compare with ourselves
                val theirFile = user.file ?: continue //User has no file

                // Map mismatch conditions to their warning messages, using the python-parity
                // comparators (utils.py sameFilename/sameFileduration/sameFilesize):
                // case-insensitive names, raw-vs-hashed cross-comparison for privacy-mode peers,
                // the **Hidden filename** / size-0 sentinels matching anything, and a 2.5s
                // duration tolerance.
                val mismatches = listOf(
                    !FileComparison.sameFilename(localMedia.fileName, theirFile.fileName) to Localization.strings.roomFileMismatchWarningName,
                    !FileComparison.sameFileduration(localMedia.fileDuration ?: 0.0, theirFile.fileDuration ?: 0.0) to Localization.strings.roomFileMismatchWarningDuration,
                    !FileComparison.sameFilesize(localMedia.fileSize, theirFile.fileSize) to Localization.strings.roomFileMismatchWarningSize
                )

                // If all three mismatch, skip showing a warning
                val matchingMismatches = mismatches.filter { it.first }
                if (matchingMismatches.isEmpty() || matchingMismatches.size == 3) continue

                // Build warning message dynamically
                val warning = buildString {
                    append(Localization.strings.roomFileMismatchWarningCore(user.name))
                    mismatches.filter { it.first }
                        .forEach { append(it.second) }
                }

                dispatcher.broadcastMessage(message = { warning }, isChat = false, isError = true)
                dispatchOSD(OSDCategory.WARNING) { warning }
            }
        }
    }

    /**
     * Indicates whether the room is in solo mode (offline playback).
     * When true, online-only components like networking and session sync are disabled.
     */
    val isSoloMode: Boolean
        get() = joinConfig == null


    /** The room's transient messages: at most three on screen, a warning never dropped for info. */
    val notices = NoticeQueue()

    fun dispatchOSD(getter: suspend () -> String) = dispatchNotice(NoticeSeverity.Info, getter)

    /** Something the person asked for failed or was refused. No notice switch hides it. */
    fun dispatchWarning(getter: suspend () -> String) = dispatchNotice(NoticeSeverity.Warn, getter)

    /**
     * The hold comes from the notice duration preference. Zero switches routine notices off, and a
     * warning still gets the default hold: zero asks for less chatter, not for hidden problems.
     */
    private fun dispatchNotice(severity: NoticeSeverity, getter: suspend () -> String) {
        val chosenMs = Preferences.OSD_DURATION.value() * 1000L
        val holdMs = if (severity == NoticeSeverity.Warn) maxOf(chosenMs, Preferences.OSD_DURATION.default * 1000L) else chosenMs
        if (holdMs <= 0L) return
        viewModelScope.launch { notices.post(getter(), severity, holdMs) }
    }

    /**
     * Categorized variant of [dispatchOSD] that consults the user's per-category OSD preferences
     * before showing. If the relevant toggle is off, the OSD is silently dropped (the chat-log
     * `broadcastMessage` should still happen separately at the call site, unaffected by this).
     *
     * Mirrors Syncplay PC's gating in `client.SyncplayClient.handleOSDMessage` /
     * `showOSDMessage` (constants `SHOW_SAME_ROOM_OSD`, `SHOW_NON_CONTROLLER_OSD`,
     * `SHOW_DIFFERENT_ROOM_OSD`, `SHOW_SLOWDOWN_OSD`, `SHOW_OSD_WARNINGS`).
     *
     * @param category which OSD class this event belongs to.
     * @param originUser optional username of the user that triggered the event. When the
     *                   current room is operator-controlled (`session.currentOperatorPassword
     *                   != null`) and the originator is not in the controller list, the event
     *                   is additionally gated by [OSDCategory.NON_OPERATOR]'s preference.
     */
    fun dispatchOSD(category: OSDCategory, originUser: String? = null, getter: suspend () -> String) {
        val prefs = app.preferences.Preferences
        val baseAllowed = when (category) {
            OSDCategory.SAME_ROOM -> prefs.OSD_SAME_ROOM.value()
            OSDCategory.OTHER_ROOM -> prefs.OSD_OTHER_ROOM.value()
            OSDCategory.SLOWDOWN -> prefs.OSD_SLOWDOWN.value()
            OSDCategory.WARNING -> prefs.OSD_WARNINGS.value()
        }
        if (!baseAllowed) return

        // Layer the non-operator filter on top of same-room events when in a controlled room.
        if (category == OSDCategory.SAME_ROOM && originUser != null && session.isControlledRoom()) {
            val originIsController = session.userList.value.firstOrNull { it.name == originUser }?.isController ?: false
            if (!originIsController && !prefs.OSD_NON_OPERATOR.value()) return
        }

        val severity = when (category) {
            OSDCategory.SAME_ROOM -> NoticeSeverity.Info
            OSDCategory.OTHER_ROOM -> NoticeSeverity.Quiet
            OSDCategory.SLOWDOWN -> NoticeSeverity.Sync
            OSDCategory.WARNING -> NoticeSeverity.Warn
        }
        dispatchNotice(severity, getter)
    }

    /************ Extension Properties for Quick Access ***************/

    /** Quick access to the current media player instance */
    val player: PlayerImpl
        get() = playerManager.player

    /** Quick access to the current room session state */
    val session: Session
        get() = protocol.session

    /** Quick access to the currently loaded media file, if any */
    var media: MediaFile?
        get() = playerManager.media.value
        set(value) {
            playerManager.media.value = value
        }

    /** Quick access to whether the current media contains video */
    val hasVideo: StateFlow<Boolean>
        get() = playerManager.hasVideo

    /**
     * Cleans up all managers and resources when the ViewModel is destroyed.
     * Ensures proper shutdown of network connections, player, and other subsystems.
     */
    override fun onCleared() {
        loggy("²²²²²²²²²²²² Clearing viewmodel")
        playerManager.invalidate()
        // Solo mode may never have needed one; building it here only to tear it down is waste.
        if (networkManagerHolder.isInitialized()) networkManager.invalidate()
        uiState.invalidate()
        protocol.invalidate()
        super.onCleared()
    }
}
