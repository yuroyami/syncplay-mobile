package app.room

import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.ui.focus.FocusRequester
import app.AbstractManager
import app.player.PlayerImpl.TrackType
import androidx.lifecycle.viewModelScope
import app.preferences.Preferences
import app.preferences.flow
import app.preferences.value
import app.protocol.models.ConnectionState
import app.utils.loggy
import app.utils.platformCallback
import app.utils.supportsBackgroundSound
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** The shortest time between two idle-timer restarts from mouse moves. */
private val POINTER_ACTIVITY_SPACING = 250.milliseconds

class RoomUiStateManager(val viewmodel: RoomViewmodel) : AbstractManager(viewmodel) {

    val msg = MutableStateFlow<String>("")

    /**
     * A counter bumped on each press or key event in the room. A room is the group of people
     * watching together. The HUD (the controls over the video) restarts its idle timer on each
     * bump.
     */
    val hudActivity = MutableStateFlow(0L)
    fun noteHudActivity() { hudActivity.value = hudActivity.value + 1 }

    /**
     * Set by the rail (the strip of buttons that opens the panels), the system back action and
     * Escape on desktop. The room then asks to leave.
     */
    val askLeave = MutableStateFlow(false)

    /** The focus target of the chat input field, so a key can move focus to it. */
    val chatFocus = FocusRequester()

    /** The focus target of the control panel button, so focus can go back when the panel closes. */
    val controlsFocus = FocusRequester()

    /** The focus target of the add key in the bottom bar, so focus can go back when its panel closes. */
    val mediaKeyFocus = FocusRequester()

    /** True while the user drags the seek bar. The HUD never hides during a drag. */
    val scrubbing = MutableStateFlow(false)

    /** Brings the HUD back and restarts its idle timer. */
    fun showHud() {
        visibleHUD.value = true
        noteHudActivity()
    }

    /** True while the room hides the mouse pointer. See [HudAutoHideState.hidesPointer]. */
    val pointerHidden = MutableStateFlow(false)

    /** How many controls a mouse pointer rests on. Any number above zero holds the HUD open. */
    val hoveredControls = MutableStateFlow(0)

    private var lastPointerActivity: TimeMark? = null

    /**
     * A mouse pointer moved. Hidden controls come back, and visible ones restart their idle timer.
     * A mouse sends dozens of moves a second, so the timer restarts at most every quarter second.
     */
    fun notePointerMoved() {
        val last = lastPointerActivity
        if (visibleHUD.value && last != null && last.elapsedNow() < POINTER_ACTIVITY_SPACING) return
        lastPointerActivity = TimeSource.Monotonic.markNow()
        showHud()
    }

    val hasEnteredPipMode = MutableStateFlow(false)
    val visibleHUD = MutableStateFlow(true)
    /** Whether the managed room dialog is open. The user picks create or identify inside it. */
    val managedRoom = MutableStateFlow(false)

    val tabCardUserInfo = MutableStateFlow(false)
    val tabCardSharedPlaylist = MutableStateFlow(false)
    val tabCardRoomPreferences = MutableStateFlow(false)
    val tabCardTracks = MutableStateFlow(false)
    /**
     * The tab that the tracks panel shows. It lives here, because the panel leaves composition
     * when it closes, and also in picture-in-picture and locked mode.
     */
    val tracksTab = MutableStateFlow(TrackType.AUDIO)
    val tabCardGestures = MutableStateFlow(false)
    val tabCardSeekTo = MutableStateFlow(false)
    val tabCardAddMedia = MutableStateFlow(false)

    /**
     * Whether the room actions on the rail are unfolded. They fold after a short time without
     * input.
     */
    val railActionsExpanded = MutableStateFlow(false)
    val tabLock = MutableStateFlow(false)

    val controlPanel = MutableStateFlow(false)

    val gifPanelVisible = MutableStateFlow(false)
    val chatMediaSizeDp = MutableStateFlow(0f)

    /**
     * True in the portrait room, where the main box (the area under the video) shows one page at a
     * time in place of chat. A page is a side panel, the GIF drawer, the Controls page or the More
     * page. The portrait layout sets it. On entry, only one open page stays open.
     */
    var onePage: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (value) keepOnePage()
        }

    /**
     * Image URLs that the user tapped to load. Chat does not fetch an image from a peer's host on
     * its own, so a tap is what shows one, for this room session only.
     */
    val revealedImages = mutableStateSetOf<String>()

    /** Muted usernames: their chat lines are not rendered. Kept for this room session. */
    val mutedUsers = mutableStateSetOf<String>()

    fun toggleMute(username: String) {
        if (!mutedUsers.remove(username)) mutedUsers.add(username)
    }

    fun triggerHaptic() {
        app.utils.platformCallback.performHapticFeedback()
    }

    /** True while the user has navigated away for file picking. */
    var wentForFilePick = false

    private val sidePanels
        get() = listOf(tabCardUserInfo, tabCardSharedPlaylist, tabCardRoomPreferences, tabCardTracks, tabCardGestures, tabCardSeekTo, tabCardAddMedia)

    /** The panels that the control strip opens. The strip and these panels never show together. */
    private val toolPanels
        get() = listOf(tabCardTracks, tabCardGestures, tabCardSeekTo, tabCardAddMedia)

    /**
     * Opens one side panel and closes the others. A tool panel also closes the control strip. In
     * [onePage] mode, a panel closes every other page too.
     */
    private fun openSide(target: MutableStateFlow<Boolean>, forcedState: Boolean?) {
        panelCoordinator.open(target, forcedState)
        if (target.value && toolPanels.any { it === target }) controlPanel.value = false
        if (target.value && onePage) closePagesExcept(target)
    }

    /** The pages of [onePage] mode, in the order that [keepOnePage] keeps them. */
    private val pages
        get() = listOf(gifPanelVisible) + sidePanels + listOf(controlPanel, railActionsExpanded)

    /** Whether a page covers chat in [onePage] mode. */
    val anyPageOpen: Boolean
        get() = pages.any { it.value }

    private fun closePagesExcept(kept: MutableStateFlow<Boolean>) = pages.forEach { if (it !== kept) it.value = false }

    /**
     * Keeps one page when the portrait layout starts. The GIF drawer wins, because the composer
     * holds its search, then a side panel, the Controls page and the More page. The add card of
     * the landscape room becomes the add media page.
     */
    private fun keepOnePage() {
        if (mediaAddExpanded.value) toggleAddMedia(true)
        val kept = pages.firstOrNull { it.value } ?: return
        closePagesExcept(kept)
    }

    /**
     * Closes every page, so the main box of the portrait room shows chat. With [keepGifs], the GIF
     * drawer stays open, because the chat field is then its search.
     */
    fun showChat(keepGifs: Boolean = false) {
        pages.forEach { if (!(keepGifs && it === gifPanelVisible)) it.value = false }
    }

    /** Opens or closes the GIF drawer. In [onePage] mode, opening it closes the other pages. */
    fun toggleGifPanel(forcedState: Boolean? = null) {
        gifPanelVisible.value = forcedState ?: !gifPanelVisible.value
        if (gifPanelVisible.value && onePage) closePagesExcept(gifPanelVisible)
    }

    /**
     * Opens or closes the More page of the portrait room. Its flag also unfolds the room actions of
     * the rail in landscape.
     */
    fun toggleMorePage(forcedState: Boolean? = null) {
        railActionsExpanded.value = forcedState ?: !railActionsExpanded.value
        if (railActionsExpanded.value && onePage) closePagesExcept(railActionsExpanded)
    }

    private val panelCoordinator by lazy { SidePanelCoordinator(sidePanels) }
    val mediaAddExpanded get() = panelCoordinator.mediaExpanded
    fun expandMediaAdd() = panelCoordinator.expandMedia()
    fun collapseMediaAdd(restore: Boolean = true) = panelCoordinator.collapseMedia(restore)

    /** Whether any of the seven side panels is showing. */
    val anySidePanelOpen: Boolean
        get() = sidePanels.any { it.value }

    /** Closes any open side panel, for a control that needs the side of the room. */
    fun closeSidePanels() = sidePanels.forEach { it.value = false }

    /**
     * What Back does in the room. It closes the top open layer, and asks to leave only when nothing
     * is left. In [onePage] mode, the open page is the top layer, so Back shows chat again. On a TV
     * remote, Back is the only way out of a panel. Back never changes the view of the room.
     */
    fun back() {
        when {
            onePage && anyPageOpen -> showChat()
            gifPanelVisible.value -> gifPanelVisible.value = false
            anySidePanelOpen -> closeSidePanels()
            controlPanel.value -> toggleControlPanel(false)
            railActionsExpanded.value -> railActionsExpanded.value = false
            else -> askLeave.value = true
        }
    }

    /** Opens or closes the control strip, or the Controls page in [onePage] mode. */
    fun toggleControlPanel(forcedState: Boolean? = null) {
        controlPanel.value = forcedState ?: !controlPanel.value
        if (controlPanel.value) {
            collapseMediaAdd(restore = false)
            toolPanels.forEach { it.value = false }
            if (onePage) closePagesExcept(controlPanel)
        }
    }

    fun toggleUserInfo(forcedState: Boolean? = null) = openSide(tabCardUserInfo, forcedState)
    fun toggleSharedPlaylist(forcedState: Boolean? = null) = openSide(tabCardSharedPlaylist, forcedState)
    fun toggleRoomPreferences(forcedState: Boolean? = null) = openSide(tabCardRoomPreferences, forcedState)
    fun toggleTracks(forcedState: Boolean? = null) = openSide(tabCardTracks, forcedState)
    fun toggleGestures(forcedState: Boolean? = null) = openSide(tabCardGestures, forcedState)
    fun toggleSeekTo(forcedState: Boolean? = null) = openSide(tabCardSeekTo, forcedState)
    fun toggleAddMedia(forcedState: Boolean? = null) = openSide(tabCardAddMedia, forcedState)

    /** How the room lifecycle hooks map to each platform (iOS / Android):
    * - [onLifecycleCreate] → `viewDidLoad` / `onCreate`
    * - [onLifecycleStart] → `viewWillAppear` / `onStart`
    * - [onLifecycleResume] → `viewDidAppear` / `onResume`
    * - [onLifecyclePause] → `viewWillDisappear` / `onPause`
    * - [onLifecycleStop] → `viewDidDisappear` / `onStop`
    */

    /** Why this device holds its playback. While any reason is set, it stays paused here. */
    val holds = PlaybackHolds()

    private val visibleState = MutableStateFlow(true)

    /** False while the room is not on screen. Picture-in-picture counts as on screen. */
    val visible: Boolean get() = visibleState.value

    /** True while this device holds its playback: it stays paused, reports the room's position and takes no corrections. */
    val isHeld: Boolean get() = holds.held

    /** True while the room is not on screen. Such a device follows the room and never moves it. */
    val isAway: Boolean get() = !visibleState.value

    /** True while the room is not on screen and still plays its sound on this device. */
    val isListeningAway: Boolean get() = isAway && !isHeld

    /** How long the room may stay paused while it listens away. Tests make it short. */
    internal var pauseGrace: Duration = LISTEN_AWAY_PAUSE_GRACE

    /** How long a lost connection may last while the room listens away. Tests make it short. */
    internal var connectionGrace: Duration = LISTEN_AWAY_CONNECTION_GRACE

    @Volatile
    private var listeningActive = false
    private var listenJob: Job? = null
    private var connectionJob: Job? = null
    private var readinessJob: Job? = null

    fun onLifecycleCreate() {
        // Nothing to do
    }

    fun onLifecycleStart() = comeBack()

    fun onLifecycleResume() = comeBack()

    fun onLifecyclePause() {
        // Nothing to do
    }

    /**
     * The room left the screen, outside picture-in-picture. With background sound, the sound
     * keeps playing here and only the video stops. Otherwise this device holds: it pauses locally,
     * and the room is not told. iOS reports both the view and the app leaving, so only the first
     * call counts.
     */
    fun onLifecycleStop() {
        if (hasEnteredPipMode.value) return
        if (!visibleState.value) return
        visibleState.value = false
        if (keepsSound()) startListeningAway() else hold(HoldReason.LeftScreen)
    }

    private fun keepsSound(): Boolean = keepsSoundAway(
        settingOn = Preferences.KEEP_SOUND_AWAY.value(),
        platformSupports = supportsBackgroundSound,
        hasMedia = viewmodel.media != null,
        playerReady = viewmodel.playerManager.isPlayerReady.value,
        connectedOrSolo = viewmodel.isSoloMode || viewmodel.networkManager.state.value == ConnectionState.CONNECTED,
    )

    /**
     * Holds this device for [reason]. The first reason pauses it here, and the room is not told.
     * The expected state changes first, so the divergence collector sees nothing new.
     */
    fun hold(reason: HoldReason) {
        if (!holds.add(reason)) return
        loggy("Holding playback: $reason")
        stopListeningAway()
        if (!viewmodel.playerManager.isPlayerReady.value) return
        viewmodel.protocol.noteExpectedPlaybackState(paused = true)
        onMainThread { viewmodel.player.pause() }
    }

    /**
     * Drops [reason]. When it was the last one, the next server State brings this device back to
     * the room's position and play state. A room still away listens again, if it still can.
     */
    fun release(reason: HoldReason) {
        if (!holds.remove(reason)) return
        loggy("Released playback: $reason")
        if (isAway && !keepsSound()) {
            holds.add(HoldReason.LeftScreen)
            return
        }
        viewmodel.protocol.resumeFromBackground()
        if (isAway) startListeningAway()
    }

    /** Back on screen: every hold ends, video comes back, and a held device re-anchors to the room. */
    private fun comeBack() {
        if (visibleState.value && !holds.held) return
        visibleState.value = true
        stopListeningAway()
        // Even a held room tells the platform, which may still wait for the audio focus to return.
        platformCallback.onListeningAway(viewmodel, false)
        connectionJob?.cancel()
        connectionJob = null
        if (viewmodel.playerManager.isPlayerReady.value) onMainThread { viewmodel.player.suspendVideo(false) }
        if (holds.clear()) viewmodel.protocol.resumeFromBackground()
    }

    /**
     * The sound keeps playing while the room is away. The platform keeps the device awake and
     * watches the audio focus and the headphones. A room that stays paused too long, or a
     * connection that stays lost, ends it with a hold.
     */
    private fun startListeningAway() {
        if (!listeningActive) {
            listeningActive = true
            platformCallback.onListeningAway(viewmodel, true)
        }
        onMainThread { if (isAway) viewmodel.player.suspendVideo(true) }
        listenJob?.cancel()
        listenJob = viewmodel.viewModelScope.launch {
            viewmodel.playerManager.isNowPlaying.collectLatest { playing ->
                if (playing) return@collectLatest
                delay(pauseGrace)
                hold(HoldReason.ListeningEnded)
            }
        }
        if (connectionJob == null && !viewmodel.isSoloMode) {
            connectionJob = viewmodel.viewModelScope.launch {
                viewmodel.networkManager.state.collectLatest { state ->
                    if (state == ConnectionState.CONNECTED) {
                        release(HoldReason.ConnectionLost)
                        return@collectLatest
                    }
                    delay(connectionGrace)
                    if (isListeningAway) hold(HoldReason.ConnectionLost)
                }
            }
        }
    }

    private fun stopListeningAway() {
        listenJob?.cancel()
        listenJob = null
        if (!listeningActive) return
        listeningActive = false
        platformCallback.onListeningAway(viewmodel, false)
    }

    /**
     * Tells the platform, while the room is on screen, whether it could keep its sound if it left
     * now. Android starts its media service in the foreground then. Called once the player is ready.
     */
    fun watchBackgroundSoundReadiness() {
        if (readinessJob != null || !supportsBackgroundSound) return
        val connected = if (viewmodel.isSoloMode) flowOf(true)
        else viewmodel.networkManager.state.map { it == ConnectionState.CONNECTED }
        readinessJob = viewmodel.viewModelScope.launch {
            combine(
                Preferences.KEEP_SOUND_AWAY.flow(),
                viewmodel.playerManager.media,
                viewmodel.playerManager.isPlayerReady,
                connected,
                visibleState,
            ) { on, media, ready, online, shown ->
                shown && keepsSoundAway(on, true, media != null, ready, online)
            }.distinctUntilChanged().collect { platformCallback.backgroundSoundReady(it) }
        }
    }


    /** Resets all UI state to defaults. */
    override fun invalidate() {
        wentForFilePick = false
        hasEnteredPipMode.value = false
        visibleHUD.value = true
        gifPanelVisible.value = false
        chatMediaSizeDp.value = 0f
        scrubbing.value = false
        sidePanels.forEach { it.value = false }
        railActionsExpanded.value = false
        collapseMediaAdd(restore = false)
        revealedImages.clear()
        mutedUsers.clear()
        VideoBounds.forget()
        stopListeningAway()
        connectionJob?.cancel()
        connectionJob = null
        readinessJob?.cancel()
        readinessJob = null
        holds.clear()
        visibleState.value = true
    }
}