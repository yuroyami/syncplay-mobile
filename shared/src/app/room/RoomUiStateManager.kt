package app.room

import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.ui.focus.FocusRequester
import app.AbstractManager
import app.player.PlayerImpl.TrackType
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

    /** Opens one side panel and closes the others. A tool panel also closes the control strip. */
    private fun openSide(target: MutableStateFlow<Boolean>, forcedState: Boolean?) {
        panelCoordinator.open(target, forcedState)
        if (target.value && toolPanels.any { it === target }) controlPanel.value = false
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
     * What Back does in the room. It closes the top open layer, and asks to leave only when
     * nothing is open. On a TV remote, Back is the only way out of a panel.
     */
    fun back() {
        when {
            gifPanelVisible.value -> gifPanelVisible.value = false
            anySidePanelOpen -> closeSidePanels()
            controlPanel.value -> toggleControlPanel(false)
            railActionsExpanded.value -> railActionsExpanded.value = false
            else -> askLeave.value = true
        }
    }

    fun toggleControlPanel(forcedState: Boolean? = null) {
        controlPanel.value = forcedState ?: !controlPanel.value
        if (controlPanel.value) {
            collapseMediaAdd(restore = false)
            toolPanels.forEach { it.value = false }
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

    /** True after [onLifecycleStop] outside picture-in-picture, until the room is in front again. */
    @Volatile
    var background = false

    fun onLifecycleCreate() {
        // Nothing to do
    }

    fun onLifecycleStart() = leaveBackground()

    fun onLifecycleResume() = leaveBackground()

    fun onLifecyclePause() {
        // Nothing to do
    }

    /**
     * Pauses playback, unless the room is in picture-in-picture. The pause is local. The expected
     * state changes first, so the divergence collector sees nothing new. The outbound State keeps
     * reporting the room's position, so a phone in a pocket never pulls the room back to where it
     * stopped.
     */
    fun onLifecycleStop() {
        if (hasEnteredPipMode.value) return
        background = true
        if (!viewmodel.playerManager.isPlayerReady.value) return
        viewmodel.protocol.noteExpectedPlaybackState(paused = true)
        onMainThread { viewmodel.player.pause() }
    }

    /** Back in front: the next server State seeks to the room position and applies play or pause. */
    private fun leaveBackground() {
        if (!background) return
        background = false
        viewmodel.protocol.resumeFromBackground()
    }

    val isInBackground: Boolean
        get() = background


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
    }
}