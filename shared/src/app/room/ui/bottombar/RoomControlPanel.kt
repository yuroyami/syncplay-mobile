package app.room.ui.bottombar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrowseGallery
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Shape
import app.room.RoomViewmodel
import app.room.ui.tabs.PageRow
import app.uicomponents.frames.PanelFrame
import kotlinx.coroutines.CoroutineScope
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VideoSettings
import app.i18n.Localization
import app.i18n.strings
import app.uicomponents.controls.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Box
import app.uicomponents.controls.pressFeedback
import app.uicomponents.controls.controlStates
import app.uicomponents.controls.Icon
import app.theme.Radius
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.clickable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import app.LocalRoomUiState
import app.LocalRoomViewmodel
import androidx.compose.foundation.layout.padding
import app.theme.Type
import app.theme.palette
import app.preferences.Preferences.UNDO_SEEK_NO_CONFIRM
import app.preferences.set
import app.preferences.watchPref
import app.theme.Space
import app.uicomponents.controls.AccentAction
import app.uicomponents.controls.Feedback
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import app.uicomponents.LocalIsTelevision
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import app.uicomponents.controls.GlyphButton
import app.uicomponents.controls.SecondaryAction
import app.uicomponents.frames.Modal
import app.uicomponents.frames.ModalSize
import app.utils.ioDispatcher
import app.utils.timestampFromMillis
import kotlinx.coroutines.launch

/*
 * The control panel: a row of icon buttons in landscape (the control strip), and a page of rows in
 * the portrait room (the Controls page). Both run the same actions. The audio and subtitle panel
 * is in the side dock (CardTracks). The subtitle search and the chapter list have their own files.
 */

/**
 * The button that opens the control panel: in the bottom bar, or at the top end of the stage of
 * the portrait room. [active] tints it while the Controls page is open.
 */
@Composable
fun RoomControlPanelButton(modifier: Modifier, active: Boolean = false) {
    val viewmodel = LocalRoomViewmodel.current
    val cardController = LocalRoomUiState.current
    val hasVideo by viewmodel.hasVideo.collectAsState()

    if (hasVideo) {
        GlyphButton(
            icon = Icons.Filled.VideoSettings,
            name = strings.roomControlPanel,
            size = Space.glyphLarge,
            modifier = modifier.videoKeyTile(),
            tint = if (active) palette.accent else palette.ink,
            focusRequester = cardController.controlsFocus,
            onClick = { cardController.toggleControlPanel() },
        )
    }
}

/**
 * What the keys of the control strip and the rows of the Controls page do. One set of actions
 * keeps a row doing exactly what its strip key does.
 */
private class ControlActions(
    private val viewmodel: RoomViewmodel,
    private val scope: CoroutineScope,
    private val undoNoConfirm: () -> Boolean,
) {
    private val ui get() = viewmodel.uiState

    /** The seek that waits for the undo question, or null. */
    var pendingUndoSeek by mutableStateOf<Pair<Long, Long>?>(null)

    /** Where an undo returns to: the origin of the last own seek. */
    val undoTarget: Long? get() = viewmodel.seeks.lastOrNull()?.first

    fun aspectRatio() {
        scope.launch(ioDispatcher) {
            val label = viewmodel.player.switchAspectRatio()
            if (label.isNotBlank()) viewmodel.dispatchOSD { label }
        }
    }

    fun seekTo() = ui.toggleSeekTo()

    /* Only the local user's seeks can be undone (see RoomCallback.onSomeoneSeeked). */
    fun undoSeek() {
        val last = viewmodel.seeks.lastOrNull()
        when {
            last == null -> viewmodel.dispatchWarning { Localization.strings.roomNoRecentSeek }
            undoNoConfirm() -> undo(last)
            else -> pendingUndoSeek = last
        }
    }

    fun undo(seek: Pair<Long, Long>) {
        ui.controlPanel.value = false
        viewmodel.dispatcher.undoSeek(seek)
        viewmodel.dispatchOSD { Localization.strings.roomSeekUndone }
    }

    /* The gesture switches are here, not in settings, so they can change during playback. */
    fun gestures() = ui.toggleGestures()

    fun tracks() {
        if (ui.tabCardTracks.value) {
            ui.toggleTracks(false)
            return
        }
        viewmodel.viewModelScope.launch {
            // The panel never opens without media. The engine needs media to list tracks.
            viewmodel.player.analyzeTracks(viewmodel.media ?: return@launch)
            ui.toggleTracks(true)
        }
    }
}

/** The actions for one strip or page. Its caller also shows [UndoSeekAsk]. */
@Composable
private fun rememberControlActions(): ControlActions {
    val scope = rememberCoroutineScope()
    val viewmodel = LocalRoomViewmodel.current
    val undoNoConfirm by UNDO_SEEK_NO_CONFIRM.watchPref()
    val noConfirm by rememberUpdatedState(undoNoConfirm)
    return remember(viewmodel, scope) { ControlActions(viewmodel, scope) { noConfirm } }
}

/** The question before an undo of a seek, while [ControlActions.pendingUndoSeek] holds one. */
@Composable
private fun UndoSeekAsk(actions: ControlActions) {
    val scope = rememberCoroutineScope()
    UndoSeekModal(
        seek = actions.pendingUndoSeek,
        onDismiss = { actions.pendingUndoSeek = null },
        onUndo = { always ->
            val seek = actions.pendingUndoSeek ?: return@UndoSeekModal
            actions.pendingUndoSeek = null
            if (always) scope.launch { UNDO_SEEK_NO_CONFIRM.set(true) }
            actions.undo(seek)
        },
    )
}

/**
 * Under a remote or a keyboard, focus moves to [target] when the control panel opens, and back to
 * its own button when the panel closes. A remote cannot find the panel on its own.
 */
@Composable
private fun ControlPanelFocus(target: FocusRequester) {
    val cardController = LocalRoomUiState.current
    val remoteOrKeyboard = LocalIsTelevision.current || LocalInputModeManager.current.inputMode == InputMode.Keyboard
    val open by cardController.controlPanel.collectAsState()
    var wasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(open, remoteOrKeyboard) {
        if (!remoteOrKeyboard) return@LaunchedEffect
        val focus = if (open) target else if (wasOpen) cardController.controlsFocus else null
        wasOpen = open
        if (focus == null) return@LaunchedEffect
        repeat(8) {
            if (runCatching { focus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            delay(50.milliseconds)
        }
    }
}

@Composable
fun RoomControlPanelCard(modifier: Modifier) {
    val viewmodel = LocalRoomViewmodel.current
    val actions = rememberControlActions()

    /* The row sits above the seek bar, and Up from there lands on the transport keys instead. So
     * the row takes focus when it opens. */
    val row = remember { FocusRequester() }
    ControlPanelFocus(row)

    Row(
        modifier = modifier.focusRequester(row).focusGroup(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (viewmodel.player.canChangeAspectRatio) {
            GlyphButton(Icons.Filled.AspectRatio, name = strings.roomAspectRatio, size = Space.glyphLarge, onClick = actions::aspectRatio)
        }

        GlyphButton(Icons.Filled.BrowseGallery, name = strings.roomSeekTo, size = Space.glyphLarge, onClick = actions::seekTo)

        // The key shows the position that an undo returns to, so the user sees it before the tap.
        UndoSeekKey(target = actions.undoTarget, onClick = actions::undoSeek)

        GlyphButton(Icons.Filled.TouchApp, name = strings.roomGesturesPanelTitle, size = Space.glyphLarge, onClick = actions::gestures)

        GlyphButton(Icons.Filled.Subtitles, name = strings.roomTracks, size = Space.glyphLarge, onClick = actions::tracks)
    }

    UndoSeekAsk(actions)
}

/**
 * The Controls page of the portrait room: the actions of the control strip as 48dp rows with
 * their names, plus the chapter list when the video has chapters. A page is what the main box
 * under the video shows in place of chat.
 */
@Composable
fun RoomControlsPage(modifier: Modifier, shape: Shape) {
    val viewmodel = LocalRoomViewmodel.current
    val ui = LocalRoomUiState.current
    val actions = rememberControlActions()
    val media by viewmodel.playerManager.media.collectAsState()
    val durationMs by viewmodel.playerManager.timeFullMillis.collectAsState()
    // The seek bar analyzes the chapters. The list is read again once the duration is known.
    val chapters = remember(media?.location, durationMs) { media?.chapters?.toList().orEmpty() }
    var showChapters by remember(media?.location) { mutableStateOf(false) }

    // The host moves focus into the page under a remote or a keyboard, and back to the Controls key.
    PanelFrame(
        title = strings.roomControlPanel,
        modifier = modifier,
        shape = shape,
        onClose = { ui.toggleControlPanel(false) },
    ) {
        PageRow(Icons.Filled.Subtitles, strings.roomTracks, opens = true, onClick = actions::tracks)
        PageRow(Icons.Filled.BrowseGallery, strings.roomSeekTo, opens = true, onClick = actions::seekTo)
        PageRow(Icons.Filled.History, strings.roomUndoSeek, value = actions.undoTarget?.let(::timestampFromMillis), onClick = actions::undoSeek)
        if (viewmodel.player.canChangeAspectRatio) {
            // The engine reports only the label of the next mode, so this row shows no value.
            PageRow(Icons.Filled.AspectRatio, strings.roomAspectRatio, onClick = actions::aspectRatio)
        }
        PageRow(Icons.Filled.TouchApp, strings.roomGesturesPanelTitle, opens = true, onClick = actions::gestures)
        if (viewmodel.player.supportsChapters && chapters.isNotEmpty()) {
            PageRow(Icons.Filled.FormatListNumbered, strings.roomChapters, value = chapters.size.toString(), opens = true) {
                showChapters = true
            }
        }
    }
    UndoSeekAsk(actions)
    ChaptersModal(open = showChapters, onDismiss = { showChapters = false })
}

/** The undo icon in a normal 48dp key, with the return time as a small badge under it. */
@Composable
private fun UndoSeekKey(target: Long?, onClick: () -> Unit) {
    val p = palette
    val name = strings.roomUndoSeek
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .height(Space.touchMin)
            .widthIn(min = Space.touchMin)
            .clip(Radius.controlShape)
            .clickable(interactionSource = source, indication = null, role = Role.Button) { Feedback.tick(); onClick() }
            .hoverable(source)
            .semantics { contentDescription = name }
            .controlStates(source, Radius.controlShape)
            .pointerHoverIcon(PointerIcon.Hand)
            .pressFeedback(source),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.History,
            contentDescription = null,
            tint = p.ink,
            modifier = Modifier.size(Space.glyphLarge).offset(y = if (target != null) -4.dp else 0.dp),
        )
        if (target != null) {
            Text(
                text = timestampFromMillis(target),
                style = Type.group,
                color = p.inkDim,
                maxLines = 1,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp, start = 4.dp, end = 4.dp),
            )
        }
    }
}

@Composable
private fun UndoSeekModal(seek: Pair<Long, Long>?, onDismiss: () -> Unit, onUndo: (always: Boolean) -> Unit) {
    Modal(
        open = seek != null,
        onDismiss = onDismiss,
        title = strings.roomUndoSeekTitle,
        size = ModalSize.Ask,
        actions = {
            SecondaryAction(strings.roomUndoSeekAlways, onClick = { onUndo(true) })
            SecondaryAction(strings.roomUndoSeekCancel, onClick = onDismiss)
            AccentAction(strings.roomUndoSeekConfirm, onClick = { onUndo(false) })
        },
    ) {
        if (seek != null) {
            Text(
                // second is the current position, and first is where the seek started.
                text = strings.roomUndoSeekMessage(timestampFromMillis(seek.second), timestampFromMillis(seek.first)),
                style = Type.note,
                color = palette.inkDim,
            )
        }
    }
}
