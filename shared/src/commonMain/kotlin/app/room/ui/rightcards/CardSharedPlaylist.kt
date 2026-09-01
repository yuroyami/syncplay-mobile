package app.room.ui.rightcards

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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import app.theme.Radius
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.PopupMediaDirs.MediaDirsPopup
import app.uicomponents.controls.AccentAction
import app.uicomponents.controls.AddGlyph
import app.uicomponents.controls.Field
import app.uicomponents.controls.GlyphButton
import app.uicomponents.controls.ListRow
import app.uicomponents.controls.MoreGlyph
import app.uicomponents.controls.PlayGlyph
import app.uicomponents.controls.RowGap
import app.uicomponents.controls.RowLabel
import app.uicomponents.controls.SecondaryAction
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.cancel
import syncplaymobile.shared.generated.resources.delete
import syncplaymobile.shared.generated.resources.done
import syncplaymobile.shared.generated.resources.play
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
import syncplaymobile.shared.generated.resources.room_shared_playlist_empty
import syncplaymobile.shared.generated.resources.room_shared_playlist_more
import syncplaymobile.shared.generated.resources.room_shared_playlist_playlist_is_empty
import syncplaymobile.shared.generated.resources.room_shared_playlist_urls
import syncplaymobile.shared.generated.resources.room_link_paste
import kotlin.time.Clock

object CardSharedPlaylist {

    /**
     * The shared playlist panel: rows with a play mark on the current entry, and three glyphs in
     * the header (add, shuffle, more) that open short action lists. Every picker launches only
     * after its list has dismissed, the iOS rule.
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
        var addOpen by remember { mutableStateOf(false) }
        var shuffleOpen by remember { mutableStateOf(false) }
        var moreOpen by remember { mutableStateOf(false) }
        var itemActions by remember { mutableStateOf<Int?>(null) }
        var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
        LaunchedEffect(addOpen, moreOpen, pending) {
            val action = pending ?: return@LaunchedEffect
            if (addOpen || moreOpen) return@LaunchedEffect
            pending = null
            delay(50)
            action()
        }

        val items = viewmodel.session.sharedPlaylist
        val current by remember { viewmodel.session.spIndex }

        PanelFrame(
            title = stringResource(Res.string.room_shared_playlist),
            modifier = Modifier.fillMaxSize(),
            shape = shape,
            scrollable = false,
            actions = {
                GlyphButton(AddGlyph, name = stringResource(Res.string.room_shared_playlist_add), target = Space.row) { addOpen = true }
                GlyphButton(Icons.Filled.Shuffle, name = stringResource(Res.string.room_shared_playlist_button_shuffle), target = Space.row) { shuffleOpen = true }
                GlyphButton(MoreGlyph, name = stringResource(Res.string.room_shared_playlist_more), target = Space.row) { moreOpen = true }
            },
        ) {
            if (items.isEmpty()) {
                Text(
                    text = stringResource(Res.string.room_shared_playlist_empty),
                    style = Type.note,
                    color = p.inkDim,
                    modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.gap),
                )
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                itemsIndexed(items) { index, item ->
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

        Modal(open = addOpen, onDismiss = { addOpen = false }, title = stringResource(Res.string.room_shared_playlist_add), size = ModalSize.Ask, inset = false) {
            ActionRow(Icons.AutoMirrored.Filled.NoteAdd, stringResource(Res.string.room_shared_playlist_button_add_file)) { pending = { mediaFilePicker.launch() }; addOpen = false }
            ActionRow(Icons.Filled.CreateNewFolder, stringResource(Res.string.room_shared_playlist_button_add_folder)) { pending = { mediaDirectoryPicker.launch() }; addOpen = false }
            ActionRow(Icons.Filled.AddLink, stringResource(Res.string.room_shared_playlist_button_add_url)) { addOpen = false; urlsOpen = true }
        }

        Modal(open = shuffleOpen, onDismiss = { shuffleOpen = false }, title = stringResource(Res.string.room_shared_playlist_button_shuffle), size = ModalSize.Ask, inset = false) {
            ActionRow(Icons.Filled.Shuffle, stringResource(Res.string.room_shared_playlist_button_shuffle)) { shuffleOpen = false; scope.launch { playlist.shuffle(false) } }
            ActionRow(Icons.Filled.Shuffle, stringResource(Res.string.room_shared_playlist_button_shuffle_rest)) { shuffleOpen = false; scope.launch { playlist.shuffle(true) } }
        }

        Modal(open = moreOpen, onDismiss = { moreOpen = false }, title = stringResource(Res.string.room_shared_playlist_more), size = ModalSize.Ask, inset = false) {
            ActionRow(Icons.Filled.Download, stringResource(Res.string.room_shared_playlist_button_playlist_import)) { pending = { playlistLoadPicker.launch() }; moreOpen = false }
            ActionRow(Icons.Filled.Download, stringResource(Res.string.room_shared_playlist_button_playlist_import_n_shuffle)) {
                // The flag is read by the picker's callback, so it is set before the launch.
                shouldShuffle = true
                pending = { playlistLoadPicker.launch() }
                moreOpen = false
            }
            ActionRow(Icons.Filled.Save, stringResource(Res.string.room_shared_playlist_button_playlist_export)) {
                moreOpen = false
                if (items.isEmpty()) {
                    viewmodel.dispatchOSD { getString(Res.string.room_shared_playlist_playlist_is_empty) }
                } else {
                    val suggestedName = "SharedPlaylist_${Clock.System.now()}"
                    pending = { playlistSaver.launch(suggestedName = suggestedName, extension = "txt") }
                }
            }
            ActionRow(Icons.Filled.Folder, stringResource(Res.string.room_shared_playlist_button_set_media_directories)) { moreOpen = false; mediaDirsOpen.value = true }
            ActionRow(Icons.Filled.ClearAll, stringResource(Res.string.room_shared_playlist_clear_playlist)) { moreOpen = false; playlist.clearPlaylist() }
        }

        MediaDirsPopup(mediaDirsOpen)
        AddUrlsModal(open = urlsOpen, onDismiss = { urlsOpen = false })
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
