package app.room.ui.rightcards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewModelScope
import app.LocalRoomViewmodel
import app.i18n.Localization
import app.i18n.strings
import app.preferences.settings.AskModal
import app.theme.Motion
import app.theme.Radius
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.PopupMediaDirs.MediaDirsPopup
import app.uicomponents.controls.AccentAction
import app.uicomponents.controls.AddGlyph
import app.uicomponents.controls.Feedback
import app.uicomponents.controls.Field
import app.uicomponents.controls.GlyphButton
import app.uicomponents.controls.Icon
import app.uicomponents.controls.ListRow
import app.uicomponents.controls.MoreGlyph
import app.uicomponents.controls.PlayGlyph
import app.uicomponents.controls.RowGap
import app.uicomponents.controls.RowLabel
import app.uicomponents.controls.Rule
import app.uicomponents.controls.SecondaryAction
import app.uicomponents.controls.Text
import app.uicomponents.frames.Modal
import app.uicomponents.frames.ModalSize
import app.uicomponents.frames.PanelFrame
import app.utils.appName
import app.utils.getText
import app.utils.ioDispatcher
import app.utils.playlistExs
import app.utils.rememberFileSaver
import app.utils.mediaFileKitType
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlin.time.Clock
import kotlinx.coroutines.launch
import syncplaymobile.shared.generated.resources.cancel
import syncplaymobile.shared.generated.resources.delete
import syncplaymobile.shared.generated.resources.done
import syncplaymobile.shared.generated.resources.play
import androidx.compose.material.icons.filled.Undo
import androidx.compose.runtime.collectAsState

/** Which header key is unfolded, if any. */
private enum class PlaylistGroup { Add, Shuffle, More }

object CardSharedPlaylist {

    /**
     * The shared playlist panel: rows with a play mark on the current entry, and three glyphs in
     * the header (add, shuffle, more). A tap on one unfolds its options in a strip under the
     * header as rows, part of the chrome, and a second tap or a choice folds it back. Pickers launch
     * straight from the strip: the panel stays composed, so their results always land. A long
     * press lifts a row to move it, and the room hears one move when it lands.
     */
    @Composable
    fun SharedPlaylistCard(shape: Shape = Radius.panelShape) {
        val viewmodel = LocalRoomViewmodel.current
        val scope = rememberCoroutineScope { ioDispatcher }
        val playlist = viewmodel.playlistManager
        val p = palette

        val mediaFilePicker = rememberFilePickerLauncher(type = mediaFileKitType, mode = FileKitMode.Multiple()) { files ->
            if (files.isNullOrEmpty()) return@rememberFilePickerLauncher
            viewmodel.viewModelScope.launch(ioDispatcher) { playlist.addFiles(files) }
        }
        val mediaDirectoryPicker = rememberDirectoryPickerLauncher { directory ->
            directory ?: return@rememberDirectoryPickerLauncher
            scope.launch { playlist.addFolderToPlaylist(directory) }
        }
        var shouldShuffle by remember { mutableStateOf(false) }
        val playlistLoadPicker = rememberFilePickerLauncher(type = FileKitType.File(extensions = playlistExs)) { file ->
            if (file != null) playlist.loadPlaylistLocally(file, alsoShuffle = shouldShuffle)
            shouldShuffle = false
        }
        val playlistSaver = rememberFileSaver { file ->
            file ?: return@rememberFileSaver
            playlist.savePlaylistLocally(file)
        }

        val mediaDirsOpen = remember { mutableStateOf(false) }
        var urlsOpen by remember { mutableStateOf(false) }
        var group by remember { mutableStateOf<PlaylistGroup?>(null) }
        // Clearing empties the list for the whole room, so it asks first.
        val askClear = remember { mutableStateOf(false) }
        var itemActions by remember { mutableStateOf<Int?>(null) }

        val items = viewmodel.session.sharedPlaylist
        val current by remember { viewmodel.session.spIndex }

        fun toggle(g: PlaylistGroup) { group = if (group == g) null else g }

        PanelFrame(
            title = strings.roomSharedPlaylist,
            modifier = Modifier.fillMaxSize(),
            shape = shape,
            scrollable = false,
            actions = {
                HeaderKey(AddGlyph, strings.roomSharedPlaylistAdd, group == PlaylistGroup.Add) { toggle(PlaylistGroup.Add) }
                HeaderKey(Icons.Filled.Shuffle, strings.roomSharedPlaylistButtonShuffle, group == PlaylistGroup.Shuffle) { toggle(PlaylistGroup.Shuffle) }
                // Only there when there is something to take back: a shuffle, a clear or a
                // wrong delete is one tap and reaches everyone.
                val canUndo by playlist.canUndo.collectAsState()
                if (canUndo) {
                    HeaderKey(
                        icon = Icons.Filled.Undo,
                        name = strings.roomSharedPlaylistUndo,
                        open = false,
                    ) { playlist.undoLastPlaylistChange() }
                }
                HeaderKey(MoreGlyph, strings.roomSharedPlaylistMore, group == PlaylistGroup.More) { toggle(PlaylistGroup.More) }
            },
        ) {
            // The strip: the header grown by one row, on the accent's faint ground.
            AnimatedVisibility(group != null, enter = expandVertically(Motion.move()) + fadeIn(Motion.quick()), exit = shrinkVertically(Motion.move()) + fadeOut(Motion.quick())) {
                Column(Modifier.fillMaxWidth().background(p.accent.copy(alpha = 0.06f))) {
                    Column(Modifier.fillMaxWidth().padding(vertical = Space.gapTight)) {
                        when (group) {
                            PlaylistGroup.Add -> {
                                Chip(Icons.AutoMirrored.Filled.NoteAdd, strings.roomSharedPlaylistButtonAddFile) { group = null; mediaFilePicker.launch() }
                                Chip(Icons.Filled.CreateNewFolder, strings.roomSharedPlaylistButtonAddFolder) { group = null; mediaDirectoryPicker.launch() }
                                Chip(Icons.Filled.AddLink, strings.roomSharedPlaylistButtonAddUrl) { group = null; urlsOpen = true }
                            }
                            PlaylistGroup.Shuffle -> {
                                Chip(Icons.Filled.Shuffle, strings.roomSharedPlaylistButtonShuffle) { group = null; scope.launch { playlist.shuffle(false) } }
                                Chip(Icons.Filled.Shuffle, strings.roomSharedPlaylistButtonShuffleRest) { group = null; scope.launch { playlist.shuffle(true) } }
                            }
                            PlaylistGroup.More -> {
                                Chip(Icons.Filled.Download, strings.roomSharedPlaylistButtonPlaylistImport) { group = null; playlistLoadPicker.launch() }
                                Chip(Icons.Filled.Download, strings.roomSharedPlaylistButtonPlaylistImportNShuffle) {
                                    // The flag is read by the picker's callback, so it is set before the launch.
                                    group = null
                                    shouldShuffle = true
                                    playlistLoadPicker.launch()
                                }
                                Chip(Icons.Filled.Save, strings.roomSharedPlaylistButtonPlaylistExport) {
                                    group = null
                                    if (items.isEmpty()) {
                                        viewmodel.dispatchWarning { Localization.strings.roomSharedPlaylistPlaylistIsEmpty }
                                    } else {
                                        playlistSaver.launch(suggestedName = "SharedPlaylist_${Clock.System.now()}", extension = "txt")
                                    }
                                }
                                Chip(Icons.Filled.Folder, strings.roomSharedPlaylistButtonSetMediaDirectories) { group = null; mediaDirsOpen.value = true }
                                Chip(Icons.Filled.ClearAll, strings.roomSharedPlaylistClearPlaylist) { group = null; askClear.value = true }
                            }
                            null -> Unit
                        }
                    }
                    Rule()
                }
            }
            if (items.isEmpty()) {
                Text(
                    text = strings.roomSharedPlaylistEmpty,
                    style = Type.note,
                    color = p.inkDim,
                    modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.gap),
                )
            }
            val listState = rememberLazyListState()
            val dragScope = rememberCoroutineScope()
            val drag = remember(listState) { PlaylistDragState(listState, dragScope) }
            val lifted = drag.rows
            // While a row is lifted the panel draws its own copy of the list, in the dragged order.
            val rows = lifted ?: items.mapIndexed { index, entry -> index to entry }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().pointerInput(drag) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { at -> if (drag.start(at, items.toList())) Feedback.tick() },
                        onDrag = { change, amount ->
                            change.consume()
                            drag.drag(amount.y)
                        },
                        onDragEnd = { drag.end(items.toList())?.let { (from, to) -> playlist.moveItem(from, to) } },
                        onDragCancel = { drag.end(emptyList()) },
                    )
                },
            ) {
                // A playlist may hold the same filename twice, so the position is part of the key.
                itemsIndexed(rows, key = { _, (source, entry) -> "$source:$entry" }) { index, (source, entry) ->
                    val held = lifted != null && index == drag.draggedAt
                    ListRow(
                        onClick = { itemActions = source },
                        selected = source == current,
                        modifier = when {
                            held -> Modifier.zIndex(1f).graphicsLayer { translationY = drag.draggedOffset }
                                .background(p.panel).background(p.accent.copy(alpha = 0.10f))
                            lifted != null -> Modifier.animateItem()
                            else -> Modifier
                        },
                    ) {
                        if (source == current) {
                            Icon(PlayGlyph, contentDescription = null, tint = p.ok, modifier = Modifier.size(Space.glyph))
                        } else {
                            Spacer(Modifier.size(Space.glyph))
                        }
                        RowGap()
                        Text(entry, style = Type.note, color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        val target = itemActions
        Modal(open = target != null, onDismiss = { itemActions = null }, title = target?.let { items.getOrNull(it) }, size = ModalSize.Ask, inset = false) {
            if (target != null) {
                ActionRow(PlayGlyph, strings.play) { itemActions = null; playlist.sendPlaylistSelection(target) }
                // One step at a time, and the sheet stays on the same entry, so a remote can walk it.
                if (target > 0) {
                    ActionRow(Icons.Filled.KeyboardArrowUp, strings.roomSharedPlaylistMoveUp) { playlist.moveItem(target, target - 1); itemActions = target - 1 }
                }
                if (target < items.lastIndex) {
                    ActionRow(Icons.Filled.KeyboardArrowDown, strings.roomSharedPlaylistMoveDown) { playlist.moveItem(target, target + 1); itemActions = target + 1 }
                }
                ActionRow(Icons.Filled.Delete, strings.delete) { itemActions = null; playlist.deleteItemFromPlaylist(target) }
            }
        }

        MediaDirsPopup(mediaDirsOpen)
        AskModal(
            open = askClear,
            title = strings.roomSharedPlaylistClearPlaylist,
            text = strings.roomSharedPlaylistClearQuestion,
            destructive = true,
            onYes = { askClear.value = false; playlist.clearPlaylist() },
            onNo = { askClear.value = false },
        )
        AddUrlsModal(open = urlsOpen, onDismiss = { urlsOpen = false })
    }

    /** A header glyph that shows which strip is open: accent when unfolded. */
    @Composable
    private fun HeaderKey(icon: ImageVector, name: String, open: Boolean, onClick: () -> Unit) {
        GlyphButton(icon, name = name, target = Space.row, tint = if (open) palette.accent else palette.ink, onClick = onClick)
    }

    /** One option in the strip: a 36dp row with its glyph and word, like any list row. */
    @Composable
    private fun Chip(icon: ImageVector, label: String, onClick: () -> Unit) {
        val p = palette
        ListRow(onClick = onClick, minHeight = Space.rowCompact) {
            Icon(icon, contentDescription = null, tint = p.inkDim, modifier = Modifier.size(Space.glyph))
            RowGap()
            Text(label, style = Type.value, color = p.ink, maxLines = 1)
        }
    }

    @Composable
    private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
        ListRow(onClick = onClick) {
            Icon(icon, contentDescription = null, tint = palette.inkDim, modifier = Modifier.size(Space.glyph))
            RowGap()
            RowLabel(label)
        }
    }

    /** URLs for the playlist, one per line, with paste. */
    @Composable
    private fun AddUrlsModal(open: Boolean, onDismiss: () -> Unit) {
        if (!open) return
        val playlist = LocalRoomViewmodel.current.playlistManager
        val clipboard = LocalClipboard.current
        val scope = rememberCoroutineScope()
        var urls by remember { mutableStateOf("") }

        Modal(
            open = true,
            onDismiss = onDismiss,
            title = strings.roomSharedPlaylistAddUrl,
            size = ModalSize.Ask,
            actions = {
                SecondaryAction(strings.cancel, onClick = onDismiss)
                AccentAction(strings.done, onClick = {
                    onDismiss()
                    playlist.addURLs(urls.split("\n"))
                }, enabled = urls.isNotBlank())
            },
        ) {
            Text(strings.roomSharedPlaylistAddUrlSubtext(appName), style = Type.note, color = palette.inkDim)
            Row(Modifier.fillMaxWidth().padding(top = Space.gap), verticalAlignment = Alignment.CenterVertically) {
                Field(
                    value = urls,
                    onValueChange = { urls = it },
                    modifier = Modifier.weight(1f),
                    placeholder = strings.roomSharedPlaylistUrls,
                    keyboardType = KeyboardType.Uri,
                    singleLine = false,
                    name = strings.roomSharedPlaylistUrls,
                )
                GlyphButton(Icons.Filled.ContentPaste, name = strings.roomLinkPaste) {
                    scope.launch { clipboard.getClipEntry()?.getText()?.let { urls = it } }
                }
            }
        }
    }
}
