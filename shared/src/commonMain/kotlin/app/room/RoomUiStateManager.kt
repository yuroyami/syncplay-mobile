package app.room

import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.ui.focus.FocusRequester
import app.AbstractManager
import app.player.PlayerImpl.TrackType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.concurrent.Volatile

class RoomUiStateManager(val viewmodel: RoomViewmodel) : AbstractManager(viewmodel) {

    val msg = MutableStateFlow<String>("")

    /** Bumped on any pointer or key activity in the room; the HUD idle timer restarts on it. */
    val hudActivity = MutableStateFlow(0L)
    fun noteHudActivity() { hudActivity.value = hudActivity.value + 1 }

    /** Set by the rail, by system back and by desktop Escape; the room shows the leave question. */
    val askLeave = MutableStateFlow(false)

    /** The chat composer's focus target, so a key can jump to it. */
    val chatFocus = FocusRequester()

    /** The control panel's own glyph, so focus can go back to it when the panel closes. */
    val controlsFocus = FocusRequester()

    /** True while the track is being dragged; the HUD never hides mid-scrub. */
    val scrubbing = MutableStateFlow(false)

    /** Brings the HUD back and restarts its idle timer. */
    fun showHud() {
        visibleHUD.value = true
        noteHudActivity()
    }

    val hasEnteredPipMode = MutableStateFlow(false)
    val visibleHUD = MutableStateFlow(true)
    /** The managed room modal, with create or identify chosen inside it. */
    val managedRoom = MutableStateFlow(false)

    val tabCardUserInfo = MutableStateFlow(false)
    val tabCardSharedPlaylist = MutableStateFlow(false)
    val tabCardRoomPreferences = MutableStateFlow(false)
    val tabCardTracks = MutableStateFlow(false)
    /** The tab the tracks card shows. Kept here because the card leaves the composition with the HUD. */
    val tracksTab = MutableStateFlow(TrackType.AUDIO)
    val tabCardGestures = MutableStateFlow(false)
    val tabCardSeekTo = MutableStateFlow(false)
    val tabCardAddMedia = MutableStateFlow(false)

    /** The rail's room actions, folded after a short period without input. */
    val railActionsExpanded = MutableStateFlow(false)
    val tabLock = MutableStateFlow(false)

    val controlPanel = MutableStateFlow(false)

    /** GIF panel visibility state */
    val gifPanelVisible = MutableStateFlow(false)
    val chatMediaSizeDp = MutableStateFlow(0f)

    /**
     * Image URLs the user tapped to load. Chat does not fetch a peer's image host on sight, so
     * this is what un-hides one, for this room session only.
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

    /** The panels the control strip opens; the strip and these never show together. */
    private val toolPanels
        get() = listOf(tabCardTracks, tabCardGestures, tabCardSeekTo, tabCardAddMedia)

    /** One side panel at a time: opening one closes the others, and a tool panel closes the strip. */
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

    /** Closes whatever side panel is open, for a control that needs the room's right side. */
    fun closeSidePanels() = sidePanels.forEach { it.value = false }

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

    /** Room Lifecycle mapping according to platform (iOS/Android)
    * - [onLifecycleCreate] → `viewDidLoad` / `onCreate`
    * - [onLifecycleStart] → `viewWillAppear` / `onStart`
    * - [onLifecycleResume] → `viewDidAppear` / `onResume`
    * - [onLifecyclePause] → `viewWillDisappear` / `onPause`
    * - [onLifecycleStop] → `viewDidDisappear` / `onStop`
    */

    /** True after [onLifecycleStop] unless in PiP mode. */
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
     * Pauses playback unless in Picture-in-Picture mode. The pause is local: the expectation flips
     * first so the divergence collector sees no news, and the outbound State keeps advertising the
     * room's position, so a phone in a pocket never drags the room back to where it stopped.
     */
    fun onLifecycleStop() {
        if (hasEnteredPipMode.value) return
        background = true
        if (!viewmodel.playerManager.isPlayerReady.value) return
        viewmodel.protocol.noteExpectedPlaybackState(paused = true)
        onMainThread { viewmodel.player.pause() }
    }

    /** Back in front: the next server State hard-seeks us to the room and re-applies play/pause. */
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