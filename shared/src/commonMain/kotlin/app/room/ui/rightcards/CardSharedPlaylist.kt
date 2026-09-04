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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewModelScope
import app.LocalRoomViewmodel
import app.preferences.settings.AskModal
import app.theme.Motion
import app.theme.Radius
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.PopupMediaDirs.MediaDirsPopup
import app.uicomponents.controls.AccentAction
import app.uicomponents.controls.AddGlyph
import app.uicomponents.controls.Field
import app.uicomponents.controls.Feedback
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
import app.utils.playlistExs
import app.utils.videoFileKitType
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.cancel
import syncplaymobile.shared.generated.resources.delete
import syncplaymobile.shared.generated.resources.done
import syncplaymobile.shared.generated.resources.play
import syncplaymobile.shared.generated.resources.room_link_paste
import syncplaymobile.shared.generated.resources.room_shared_playlist
import syncplaymobile.shared.generated.resources.room_shared_playlist_add
import syncplaymobile.shared.generated.resources.room_shared_playlist_add_url
import syncplaymobile.shared.generated.resources.room_shared_playlist_add_url_subtext
import syncplaymobile.shared.generated.resources.room_shared_playlist_button_add_file
import syncplaymobile.shared.generated.resources.room_shared_playlist_button_add_folder
import syncplaymobile.shared.generated.resources.room_shared_playlist_button_add_url
import syncplaymobile.shared.generated.resources.room_shared_playlist_button_playlist_export
import syncplaymobile.shared.generated.resources.room_shared_playlist_button_playlist_import
import syncplaymobile.shared.generated.resources.room_shared_playlist_button_playlist_import_n_shuffle
import syncplaymobile.shared.generated.resources.room_shared_playlist_button_set_media_directories
import syncplaymobile.shared.generated.resources.room_shared_playlist_button_shuffle
import syncplaymobile.shared.generated.resources.room_shared_playlist_button_shuffle_rest
import syncplaymobile.shared.generated.resources.room_shared_playlist_clear_playlist
import syncplaymobile.shared.generated.resources.room_shared_playlist_clear_question
import syncplaymobile.shared.generated.resources.room_shared_playlist_empty
import syncplaymobile.shared.generated.resources.room_shared_playlist_more
import syncplaymobile.shared.generated.resources.room_shared_playlist_playlist_is_empty
import syncplaymobile.shared.generated.resources.room_shared_playlist_urls

/** Which header key is unfolded, if any. */
private enum class PlaylistGroup { Add, Shuffle, More }

object CardSharedPlaylist {

    /**
     * The shared playlist panel: rows with a play mark on the current entry, and three glyphs in
     * the header (add, shuffle, more). A tap on one unfolds its options in a strip under the
     * header as rows, part of the chrome, and a second tap or a choice folds it back. Pickers launch
     * straight from the strip: the panel stays composed, so their results always land.
     */
    @Composable
    fun SharedPlaylistCard(shape: Shape = Radius.panelShape) {
        val viewmodel = LocalRoomViewmodel.current
        val scope = rememberCoroutineScope { Dispatchers.IO }
        val playlist = viewmodel.playlistManager
        val p = palette

        val mediaFilePicker = rememberFilePickerLauncher(type = videoFileKitType, mode = FileKitMode.Multiple()) { files ->
            if (files.isNullOrEmpty()) return@rememberFilePickerLauncher
            viewmodel.viewModelScope.launch(Dispatchers.IO) { playlist.addFiles(files) }
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
        val playlistSaver = rememberFileSaverLauncher(dialogSettings = FileKitDialogSettings.createDefault()) { file ->
            file ?: return@rememberFileSaverLauncher
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

        fun toggle(g: PlaylistGroup) { Feedback.tick(); group = if (group == g) null else g }

        PanelFrame(
            title = stringResource(Res.string.room_shared_playlist),
            modifier = Modifier.fillMaxSize(),
            shape = shape,
            scrollable = false,
            actions = {
                HeaderKey(AddGlyph, stringResource(Res.string.room_shared_playlist_add), group == PlaylistGroup.Add) { toggle(PlaylistGroup.Add) }
                HeaderKey(Icons.Filled.Shuffle, stringResource(Res.string.room_shared_playlist_button_shuffle), group == PlaylistGroup.Shuffle) { toggle(PlaylistGroup.Shuffle) }
                HeaderKey(MoreGlyph, stringResource(Res.string.room_shared_playlist_more), group == PlaylistGroup.More) { toggle(PlaylistGroup.More) }
            },
        ) {
            // The strip: the header grown by one row, on the accent's faint ground.
            AnimatedVisibility(group != null, enter = expandVertically(Motion.move()) + fadeIn(Motion.quick()), exit = shrinkVertically(Motion.move()) + fadeOut(Motion.quick())) {
                Column(Modifier.fillMaxWidth().background(p.accent.copy(alpha = 0.06f))) {
                    Column(Modifier.fillMaxWidth().padding(vertical = Space.gapTight)) {
                        when (group) {
                            PlaylistGroup.Add -> {
                                Chip(Icons.AutoMirrored.Filled.NoteAdd, stringResource(Res.string.room_shared_playlist_button_add_file)) { group = null; mediaFilePicker.launch() }
                                Chip(Icons.Filled.CreateNewFolder, stringResource(Res.string.room_shared_playlist_button_add_folder)) { group = null; mediaDirectoryPicker.launch() }
                                Chip(Icons.Filled.AddLink, stringResource(Res.string.room_shared_playlist_button_add_url)) { group = null; urlsOpen = true }
                            }
                            PlaylistGroup.Shuffle -> {
                                Chip(Icons.Filled.Shuffle, stringResource(Res.string.room_shared_playlist_button_shuffle)) { group = null; scope.launch { playlist.shuffle(false) } }
                                Chip(Icons.Filled.Shuffle, stringResource(Res.string.room_shared_playlist_button_shuffle_rest)) { group = null; scope.launch { playlist.shuffle(true) } }
                            }
                            PlaylistGroup.More -> {
                                Chip(Icons.Filled.Download, stringResource(Res.string.room_shared_playlist_button_playlist_import)) { group = null; playlistLoadPicker.launch() }
                                Chip(Icons.Filled.Download, stringResource(Res.string.room_shared_playlist_button_playlist_import_n_shuffle)) {
                                    // The flag is read by the picker's callback, so it is set before the launch.
                                    group = null
                                    shouldShuffle = true
                                    playlistLoadPicker.launch()
                                }
                                Chip(Icons.Filled.Save, stringResource(Res.string.room_shared_playlist_button_playlist_export)) {
                                    group = null
                                    if (items.isEmpty()) {
                                        viewmodel.dispatchOSD { getString(Res.string.room_shared_playlist_playlist_is_empty) }
                                    } else {
                                        playlistSaver.launch(suggestedName = "SharedPlaylist_${Clock.System.now()}", extension = "txt")
                                    }
                                }
                                Chip(Icons.Filled.Folder, stringResource(Res.string.room_shared_playlist_button_set_media_directories)) { group = null; mediaDirsOpen.value = true }
                                Chip(Icons.Filled.ClearAll, stringResource(Res.string.room_shared_playlist_clear_playlist)) { group = null; askClear.value = true }
                            }
                            null -> Unit
                        }
                    }
                    Rule()
                }
            }
            if (items.isEmpty()) {
                Text(
                    text = stringResource(Res.string.room_shared_playlist_empty),
                    style = Type.note,
                    color = p.inkDim,
                    modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.gap),
                )
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                // A playlist may hold the same filename twice, so the position is part of the key.
                itemsIndexed(items, key = { index, item -> "$index:$item" }) { index, item ->
                    ListRow(onClick = { itemActions = index }, selected = index == current) {
                        if (index == current) {
                            Icon(PlayGlyph, contentDescription = null, tint = p.ok, modifier = Modifier.size(Space.glyph))
                        } else {
                            Spacer(Modifier.size(Space.glyph))
                        }
                        RowGap()
                        Text(item, style = Type.note, color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        val target = itemActions
        Modal(open = target != null, onDismiss = { itemActions = null }, title = target?.let { items.getOrNull(it) }, size = ModalSize.Ask, inset = false) {
            if (target != null) {
                ActionRow(PlayGlyph, stringResource(Res.string.play)) { itemActions = null; playlist.sendPlaylistSelection(target) }
                ActionRow(Icons.Filled.Delete, stringResource(Res.string.delete)) { itemActions = null; playlist.deleteItemFromPlaylist(target) }
            }
        }

        MediaDirsPopup(mediaDirsOpen)
        AskModal(
            open = askClear,
            title = stringResource(Res.string.room_shared_playlist_clear_playlist),
            text = stringResource(Res.string.room_shared_playlist_clear_question),
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
        ListRow(onClick = { Feedback.tick(); onClick() }, minHeight = Space.rowCompact) {
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
            title = stringResource(Res.string.room_shared_playlist_add_url),
            size = ModalSize.Ask,
            actions = {
                SecondaryAction(stringResource(Res.string.cancel), onClick = onDismiss)
                AccentAction(stringResource(Res.string.done), onClick = {
                    onDismiss()
                    playlist.addURLs(urls.split("\n"))
                }, enabled = urls.isNotBlank())
            },
        ) {
            Text(stringResource(Res.string.room_shared_playlist_add_url_subtext, appName), style = Type.note, color = palette.inkDim)
            Row(Modifier.fillMaxWidth().padding(top = Space.gap), verticalAlignment = Alignment.CenterVertically) {
                Field(
                    value = urls,
                    onValueChange = { urls = it },
                    modifier = Modifier.weight(1f),
                    placeholder = stringResource(Res.string.room_shared_playlist_urls),
                    keyboardType = KeyboardType.Uri,
                    singleLine = false,
                    name = stringResource(Res.string.room_shared_playlist_urls),
                )
                GlyphButton(Icons.Filled.ContentPaste, name = stringResource(Res.string.room_link_paste)) {
                    scope.launch { clipboard.getClipEntry()?.getText()?.let { urls = it } }
                }
            }
        }
    }
}
