package app.room

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.Screen
import app.home.JoinConfig
import app.i18n.Localization
import app.player.PlayerEngine
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
import app.uicomponents.DropPlan
import app.uicomponents.DroppedMedia
import app.uicomponents.dropRefusal
import app.utils.FileComparison
import app.utils.availablePlatformPlayerEngines
import app.utils.instantiateNetworkManager
import app.uicomponents.frames.NoticeQueue
import app.uicomponents.frames.NoticeSeverity
import app.utils.ioDispatcher
import app.utils.loggy
import app.utils.platformFileAt
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * The category of an OSD notice (a short message over the video). The categorized
 * [RoomViewmodel.dispatchOSD] checks the user's preference for each category, like the five
 * toggles in the "Messages" settings tab of Syncplay PC. There is no NON_OPERATOR case on purpose.
 * The dispatcher applies that filter to a [SAME_ROOM] event with an `originUser` argument, in a
 * room (the group of people watching together) that operators control.
 */
enum class OSDCategory {
    /** Pause, play, seek, join, leave and file-load events from a user in the current room. */
    SAME_ROOM,
    /** Activity in a different room, such as a user who joins or leaves another room. */
    OTHER_ROOM,
    /** Local sync notices (slowing down, rewinding, fast-forwarding to catch up). */
    SLOWDOWN,
    /** Red warnings: a file mismatch, being alone in the room, connection errors and similar. */
    WARNING
}

/**
 * The ViewModel of the room screen, where synchronized playback happens. A room is the group of
 * people watching together.
 *
 * It holds all room managers: networking, playback, protocol, session state and user actions.
 * It serves both online rooms and solo mode (watching alone, with no server).
 *
 * @property joinConfig The room connection settings, or null for solo mode.
 * @property backStack The navigation stack, used to leave the room.
 * @param startMedia Media to open once the engine is ready: a file or link dropped onto the home
 * screen.
 * @param engineOverride The engine to use instead of the platform's choice. Only the sync tests
 * pass one: an engine whose playhead is a clock.
 * @param transportOverride Builds the network transport instead of the platform. Only the sync
 * tests pass one: an in-memory link to the app's own server.
 */
class RoomViewmodel(
    val joinConfig: JoinConfig?,
    val backStack: SnapshotStateList<Screen>,
    startMedia: DroppedMedia? = null,
    private val engineOverride: PlayerEngine? = null,
    private val transportOverride: ((RoomViewmodel) -> NetworkManager)? = null,
) : ViewModel() {

    /************ Managers ***************/

    /** The UI state of the room screen. */
    val uiState: RoomUiStateManager by lazy { RoomUiStateManager(this) }

    /** The player lifecycle, controls and state. */
    val playerManager: PlayerManager by lazy { PlayerManager(this) }

    /**
     * The network connection to the Syncplay server. It is built on first use, not inside a
     * coroutine, so a callback, the dispatcher or a fast leave can never reach it before it exists.
     */
    private val networkManagerHolder = lazy { transportOverride?.invoke(this) ?: instantiateNetworkManager() }
    val networkManager: NetworkManager by networkManagerHolder

    /** The Syncplay protocol state and its events. */
    val protocol: ProtocolManager by lazy { ProtocolManager(this) }

    /** Reactions to protocol events, such as a pause by another user (the receiving side). */
    val callback: RoomCallback by lazy { RoomCallback(this) }

    /** Actions of the local user that go to the server (the sending side). */
    val dispatcher: RoomEventDispatcher by lazy { RoomEventDispatcher(this) }

    /** The [app.protocol.WireMessageHandler] that routes incoming wire messages. */
    val serverHandler: RoomServerMessageHandler by lazy { RoomServerMessageHandler(this) }

    /** The shared playlist: the file list that everyone in the room follows. */
    val playlistManager: SharedPlaylistManager by lazy { SharedPlaylistManager(this) }

    /** Who the room is waiting for, and the countdown once it is waiting for nobody. */
    val readiness: ReadinessManager by lazy { ReadinessManager(this) }

    /** Where each file was left, and the offer to continue from there. */
    val resume: ResumeManager by lazy { ResumeManager(this) }

    /** The seeks so far, as (from, to) pairs in milliseconds, used to undo a seek. */
    val seeks = mutableStateListOf<Pair<Long, Long>>()

    /** Media dropped onto the window. It waits here until the engine is ready: see [openDropped]. */
    private val dropped = MutableStateFlow(startMedia)

    init {
        // The log shows how long after entering the room the engine was ready and the connection
        // started. For a slow join, the log must show whether the wait was the engine, the dial
        // or the server.
        val roomEnteredAt = TimeSource.Monotonic.markNow()
        viewModelScope.launch(ioDispatcher) {
            val playerInitialization = launch {
                // The engine of the previous room may still be shutting down (the mpv handle is
                // global to the process). Never build the next engine over it.
                PlayerManager.awaitPendingDestroy()
                val preferred = Preferences.PLAYER_ENGINE.value()
                val engine = engineOverride
                    ?: availablePlatformPlayerEngines.firstOrNull { it.name == preferred && it.isAvailable }
                    ?: availablePlatformPlayerEngines.firstOrNull { it.isAvailable }
                if (engine == null) {
                    // No engine can play here, for example on desktop when KitePlayer is missing.
                    loggy("No available player engine on this platform")
                    dispatchOSD(OSDCategory.WARNING) { Localization.strings.roomNoPlayerEngine }
                    dispatcher.broadcastMessage(isChat = false, isError = true) { Localization.strings.roomNoPlayerEngine }
                    return@launch
                }
                if (engineOverride == null && engine.name != preferred) {
                    // A debug-only engine can disappear when a release build replaces the app.
                    // Save the fallback engine, so the picker never shows a stale engine name
                    // that is not the one in use.
                    Preferences.PLAYER_ENGINE.set(engine.name)
                }
                playerManager.player = engine.createImpl(this@RoomViewmodel)
                playerManager.isPlayerReady.value = true
                loggy("Room: ${engine.name} ready ${roomEnteredAt.elapsedNow().inWholeMilliseconds}ms after entering the room")
            }

            // Dropped media opens once the engine exists, so a drop during the start is kept.
            launch {
                playerInitialization.join()
                if (!playerManager.isPlayerReady.value) return@launch
                dropped.filterNotNull().collect { media ->
                    dropped.value = null
                    openDroppedNow(media)
                }
            }

            joinConfig?.let {
                launch {
                    // The first State and playlist messages can read player capabilities or load
                    // media at once, so the player must be ready before that inbound path opens.
                    playerInitialization.join()
                    if (!playerManager.isPlayerReady.value) {
                        // No engine, so the room never tries to connect. Log it, or the room looks
                        // disconnected with nothing to explain why.
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
                    // Pasted together with the room name. onConnected identifies with it again, so
                    // the operator role also survives every later reconnect.
                    session.currentOperatorPassword = joinConfig.operatorPassword

                    // connect() picks TLS from the settings and this transport. It refuses to
                    // connect when encryption is required and not available.
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
        // Leaving is the last chance to save the position in this file.
        resume.record()
        backStack.removeAt(backStack.lastIndex)
    }

    /** What the room does with a drop: it opens the media, or says why it refused the drop. */
    fun onMediaDrop(plan: DropPlan) = when (plan) {
        is DropPlan.Open -> openDropped(plan.media)
        is DropPlan.Refuse -> dispatchWarning { Localization.strings.dropRefusal(plan.why) }
    }

    /**
     * Opens dropped media with the calls of the add-media routes, so the announcement, the shared
     * playlist and the resume offer work the same. The add-media card and panel close, as they do
     * after a pick. Before the engine is ready, the media waits for it.
     */
    fun openDropped(media: DroppedMedia) {
        if (uiState.mediaAddExpanded.value) uiState.collapseMediaAdd()
        uiState.toggleAddMedia(false)
        dropped.value = media
    }

    private suspend fun openDroppedNow(media: DroppedMedia) {
        try {
            when (media) {
                is DroppedMedia.File -> player.injectVideoFile(platformFileAt(media.path))
                is DroppedMedia.Link -> player.injectVideoURL(media.url)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            // A file can vanish or become unreadable between the drop and the load.
            loggy("Room: the dropped media did not open: $e")
            dispatchWarning { Localization.strings.roomMsgProblemLoadingFile }
        }
    }

    /**
     * Warns when your file differs from someone else's, in Syncplay's words. [changedUser] has just
     * loaded a file, so the line compares your file with that person's. Null means that you loaded
     * a file, and one line then sums up how your file differs across the room. A file that differs
     * in name, size and duration at once is another file, not another copy, so it gets no line.
     */
    fun checkFileMismatches(changedUser: String? = null) {
        if (isSoloMode) return
        if (!Preferences.FILE_MISMATCH_WARNING.value()) return

        viewModelScope.launch {
            val localMedia = media ?: return@launch
            val comparisons = session.userList.value
                .filter { it.name != session.currentUsername && (changedUser == null || it.name == changedUser) }
                .mapNotNull { user -> user.file }
                .map { theirs ->
                    FileComparison.differences(
                        localMedia.fileName, localMedia.fileSize, localMedia.fileDuration,
                        theirs.fileName, theirs.fileSize, theirs.fileDuration,
                    )
                }
            val kinds = FileComparison.warnedDifferences(comparisons)
            if (kinds.isEmpty()) return@launch

            // Syncplay's separator: "name, size, duration".
            val warning: suspend () -> String = {
                val strings = Localization.strings
                val list = kinds.joinToString(", ") { kind ->
                    when (kind) {
                        FileComparison.Difference.Name -> strings.roomFileMismatchWarningName
                        FileComparison.Difference.Size -> strings.roomFileMismatchWarningSize
                        FileComparison.Difference.Duration -> strings.roomFileMismatchWarningDuration
                    }
                }
                if (changedUser != null) strings.roomFileMismatchWarningCore(list) else strings.roomFileMismatchWarningRoom(list)
            }
            dispatcher.broadcastMessage(message = warning, isChat = false, isError = true)
            dispatchOSD(OSDCategory.WARNING, getter = warning)
        }
    }

    /**
     * Whether the room is in solo mode (offline playback, with no server). In solo mode, the
     * online parts such as networking and session sync are off.
     */
    val isSoloMode: Boolean
        get() = joinConfig == null


    /**
     * The room's short-lived notices: at most three on screen, and a warning is never dropped to
     * make room for an info notice.
     */
    val notices = NoticeQueue()

    fun dispatchOSD(getter: suspend () -> String) = dispatchNotice(NoticeSeverity.Info, getter)

    /**
     * Shows a warning: something that the user asked for failed or was refused. No setting hides
     * it.
     */
    fun dispatchWarning(getter: suspend () -> String) = dispatchNotice(NoticeSeverity.Warn, getter)

    /**
     * Posts a notice for as long as the notice duration preference says. Zero turns routine
     * notices off. A warning still gets at least the default hold, because zero asks for less
     * noise, not for hidden problems.
     */
    private fun dispatchNotice(severity: NoticeSeverity, getter: suspend () -> String) {
        val chosenMs = Preferences.OSD_DURATION.value() * 1000L
        val holdMs = if (severity == NoticeSeverity.Warn) maxOf(chosenMs, Preferences.OSD_DURATION.default * 1000L) else chosenMs
        if (holdMs <= 0L) return
        viewModelScope.launch { notices.post(getter(), severity, holdMs) }
    }

    /**
     * The categorized form of [dispatchOSD]. It checks the user's OSD preference for [category]
     * before it shows anything, and drops the OSD notice when that preference is off. The call
     * site still sends the chat line with `broadcastMessage` on its own.
     *
     * Mirrors the OSD gating of Syncplay PC in client.py (the constants `SHOW_SAME_ROOM_OSD`,
     * `SHOW_NONCONTROLLER_OSD`, `SHOW_DIFFERENT_ROOM_OSD`, `SHOW_SLOWDOWN_OSD` and
     * `SHOW_OSD_WARNINGS`).
     *
     * @param category The OSD category of this event.
     * @param originUser The optional name of the user who caused the event. In an
     *                   operator-controlled room ([Session.isControlledRoom]), an event from a
     *                   user who is not a controller also needs the `OSD_NON_OPERATOR` preference.
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

    /************ Shortcuts to the managers ***************/

    val player: PlayerImpl
        get() = playerManager.player

    val session: Session
        get() = protocol.session

    /** The loaded media file, or null. */
    var media: MediaFile?
        get() = playerManager.media.value
        set(value) {
            playerManager.media.value = value
        }

    val hasVideo: StateFlow<Boolean>
        get() = playerManager.hasVideo

    /**
     * Releases the managers and their resources when the ViewModel is destroyed: the player, the
     * network connection, the UI state and the protocol.
     */
    override fun onCleared() {
        loggy("²²²²²²²²²²²² Clearing viewmodel")
        playerManager.invalidate()
        // Solo mode may never have built one. Building it here only to tear it down is waste.
        if (networkManagerHolder.isInitialized()) networkManager.invalidate()
        uiState.invalidate()
        protocol.invalidate()
        super.onCleared()
    }
}
