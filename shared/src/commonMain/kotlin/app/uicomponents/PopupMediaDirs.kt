package app.uicomponents

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import app.i18n.mediaFolderFiles
import app.i18n.strings
import app.uicomponents.controls.Icon
import app.uicomponents.controls.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import app.preferences.Preferences
import app.preferences.set
import app.preferences.settings.AskModal
import app.preferences.value
import app.preferences.watchPref
import app.room.sharedplaylist.MediaAccessRegistry
import app.room.sharedplaylist.MediaAccessRegistry.FolderState
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.controls.AccentAction
import app.uicomponents.controls.CloseGlyph
import app.uicomponents.controls.GlyphButton
import app.uicomponents.controls.ListRow
import app.uicomponents.controls.RowGap
import app.uicomponents.controls.SecondaryAction
import app.uicomponents.frames.Modal
import app.uicomponents.frames.ModalSize
import app.utils.appName
import app.utils.ioDispatcher
import com.eygraber.uri.Uri
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object PopupMediaDirs {

    /**
     * The editor of the media folders, where the app looks for the files of the shared playlist
     * (the file list that everyone in a room follows). Settings and the shared playlist panel open
     * it. It shows one row per remembered folder with its name, its media file count and its path,
     * an empty state, and the add and clear actions. A folder that the app can no longer open says
     * so, and a tap on it asks for access again. Removing a folder updates the preference and
     * [MediaAccessRegistry] together.
     */
    @Composable
    fun MediaDirsPopup(visibilityState: MutableState<Boolean>) {
        val p = palette
        val scope = rememberCoroutineScope { ioDispatcher }
        val dirs by Preferences.MEDIA_DIRECTORIES.watchPref()
        val askClear = remember { mutableStateOf(false) }
        // Each row counts its files again when this changes, after access is granted again.
        var recount by remember { mutableIntStateOf(0) }
        // The lost folder that the open picker replaces, or null when the picker adds a folder.
        var replacing by remember { mutableStateOf<String?>(null) }

        val directoryPicker = rememberDirectoryPickerLauncher { directory ->
            val lost = replacing
            replacing = null
            directory ?: return@rememberDirectoryPickerLauncher
            scope.launch {
                MediaAccessRegistry.rememberDirectory(directory)
                // A different folder picked for a lost one takes its place in the list.
                if (lost != null && lost != directory.path) removeFolder(lost)
                recount++
            }
        }

        Modal(
            open = visibilityState.value,
            onDismiss = { visibilityState.value = false },
            title = strings.mediaFolders,
            size = ModalSize.Panel,
            inset = false,
            actions = {
                SecondaryAction(strings.mediaDirectoriesClearAll, onClick = { askClear.value = true }, enabled = dirs.isNotEmpty())
                AccentAction(strings.mediaDirectoriesAddFolder, onClick = { directoryPicker.launch() })
            },
        ) {
            Text(
                text = strings.mediaFoldersBrief(appName),
                style = Type.note,
                color = p.inkDim,
                modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.gap),
            )
            if (dirs.isEmpty()) {
                Text(
                    text = strings.mediaFoldersEmpty,
                    style = Type.note,
                    color = p.inkFaint,
                    modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.gap),
                )
            }
            dirs.forEach { item ->
                // Android document ids carry a storage prefix before the path; only the folder
                // name matters here.
                val name = (Uri.parseOrNull(item)?.pathSegments?.lastOrNull() ?: item)
                    .substringAfter("primary:").substringAfter("secondary:").substringAfterLast("/")
                // Walking a large folder takes seconds on a SAF tree, so the count arrives later.
                val state by produceState<FolderState>(FolderState.Checking, item, recount) {
                    value = withContext(ioDispatcher) { MediaAccessRegistry.folderState(item) }
                }
                val lost = state == FolderState.Lost
                ListRow(
                    minHeight = Space.rowTall,
                    onClick = if (lost) ({ replacing = item; directoryPicker.launch() }) else null,
                ) {
                    Icon(Icons.Filled.Folder, contentDescription = null, tint = if (lost) p.warn else p.inkDim, modifier = Modifier.size(Space.glyph))
                    RowGap()
                    Column(Modifier.weight(1f).padding(vertical = Space.gapTight)) {
                        Text(name, style = Type.label, color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        when (val s = state) {
                            FolderState.Checking -> Text(strings.mediaFolderCounting, style = Type.note, color = p.inkDim)
                            FolderState.Lost -> Text(strings.mediaFolderLost, style = Type.note, color = p.warn)
                            is FolderState.Open -> Text(strings.mediaFolderFiles(s.mediaFiles), style = Type.note, color = p.inkDim)
                        }
                        Text(item, style = Type.note, color = p.inkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (lost) {
                        RowGap()
                        Text(strings.mediaFolderGrant, style = Type.label, color = p.accent, maxLines = 1)
                    }
                    GlyphButton(CloseGlyph, name = strings.mediaDirectoriesDelete, tint = p.inkDim) {
                        scope.launch { removeFolder(item) }
                    }
                }
            }
        }

        AskModal(
            open = askClear,
            title = strings.mediaDirectoriesClearAll,
            text = strings.mediaDirectoriesClearAllConfirm,
            destructive = true,
            onYes = {
                scope.launch {
                    Preferences.MEDIA_DIRECTORIES.set(emptySet())
                    MediaAccessRegistry.clear()
                }
            },
        )
    }

    private suspend fun removeFolder(dirId: String) {
        val paths = Preferences.MEDIA_DIRECTORIES.value().toMutableSet()
        if (paths.remove(dirId)) {
            Preferences.MEDIA_DIRECTORIES.set(paths)
            MediaAccessRegistry.forgetDirectory(dirId)
        }
    }
}
