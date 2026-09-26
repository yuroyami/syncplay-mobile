package app.room.ui.rightcards

import app.uicomponents.LocalIsTelevision
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalClipboard
import app.i18n.AppStrings
import app.i18n.strings
import app.player.resolver.ResolvedMedia
import app.player.resolver.extractYtId
import app.player.resolver.mediaResolver
import app.player.resolver.urlLooksLikeDirectMedia
import app.utils.getText
import app.utils.platformFileAt
import app.utils.playlistExs
import app.utils.mediaFileKitType
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.viewModelScope
import app.LocalRoomUiState
import app.LocalRoomViewmodel
import app.preferences.Preferences.MEDIA_RESOLVER_ENABLED
import app.preferences.watchPref
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.controls.AccentAction
import app.uicomponents.controls.BackGlyph
import app.uicomponents.controls.CloseGlyph
import app.uicomponents.controls.Field
import app.uicomponents.controls.GlyphButton
import app.uicomponents.controls.Icon
import app.uicomponents.controls.ListRow
import app.uicomponents.controls.ProgressBar
import app.uicomponents.controls.RowGap
import app.uicomponents.controls.SecondaryAction
import app.uicomponents.controls.Text
import app.uicomponents.controls.FontSizeRange
import app.uicomponents.frames.PanelFrame
import app.utils.Platform
import app.utils.platform
import app.utils.platformCallback
import app.utils.timestampFromMillis
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import syncplaymobile.shared.generated.resources.cancel
import syncplaymobile.shared.generated.resources.done

/**
 * The add-media side panel. It lists the routes that media can come in by, each as a 54dp row.
 * The link route swaps the rows for the link form in the same panel. The FileKit pickers start at
 * once, because the modal race of FileKit #575 does not apply to a panel. The panel stays open
 * until a picker returns.
 */
object CardAddMedia {

    @Composable
    fun AddMediaPanel(shape: Shape) {
        val ui = LocalRoomUiState.current
        var linkMode by remember { mutableStateOf(false) }

        fun close() {
            linkMode = false
            ui.toggleAddMedia(false)
        }

        PanelFrame(
            title = if (linkMode) strings.roomRouteLink else strings.roomButtonDescAdd,
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            centerTitle = true,
            actions = {
                if (linkMode) GlyphButton(BackGlyph, name = strings.actionBack) { linkMode = false }
                GlyphButton(CloseGlyph, name = strings.actionClose, onClick = ::close)
            },
        ) {
            AddMediaBody(linkMode = linkMode, onLinkMode = { linkMode = it }, onClose = ::close)
        }
    }

    /**
     * Shows the routes, or the link form when [linkMode] is true. The side panel uses it, and so
     * does the add key of [app.room.ui.bottombar.RoomMediaAddButton] before a file loads. The host
     * must stay composed while a picker is open. Closing the host first drops the launcher, and
     * the picked file with it.
     */
    @Composable
    fun AddMediaBody(linkMode: Boolean, onLinkMode: (Boolean) -> Unit, onClose: () -> Unit) {
        val viewmodel = LocalRoomViewmodel.current
        val sharedPlaylists by viewmodel.protocol.supportsSharedPlaylists.collectAsState()
        fun close() = onClose()

        val mediaPicker = rememberFilePickerLauncher(type = mediaFileKitType) { file ->
            close()
            file ?: return@rememberFilePickerLauncher
            viewmodel.viewModelScope.launch { viewmodel.player.injectVideoFile(file) }
        }
        // A television has no file picker app, so there the app reads the video library (#163).
        val tvPicker = rememberTvVideoPicker { file ->
            close()
            viewmodel.viewModelScope.launch { viewmodel.player.injectVideoFile(file) }
        }
        val playlistPicker = rememberFilePickerLauncher(type = FileKitType.File(extensions = playlistExs)) { file ->
            close()
            file ?: return@rememberFilePickerLauncher
            viewmodel.playlistManager.loadPlaylistLocally(file, alsoShuffle = false)
        }

        if (linkMode) {
            LinkForm(onCancel = { onLinkMode(false) }, onPlayed = ::close)
        } else {
            Column {
                RouteRow(Icons.Filled.FolderOpen, strings.roomRouteDevice, strings.roomRouteDeviceNote) {
                    if (tvPicker != null) tvPicker() else mediaPicker.launch()
                }
                RouteRow(Icons.Filled.Link, strings.roomRouteLink, supportedSites(strings)) {
                    onLinkMode(true)
                }
                if (platform == Platform.Android) {
                    RouteRow(Icons.Filled.Cloud, strings.roomRouteShare, strings.roomRouteShareNote) {
                        close()
                        platformCallback.launchSystemFilePicker { uri ->
                            uri ?: return@launchSystemFilePicker
                            viewmodel.viewModelScope.launch { viewmodel.player.injectVideoFile(platformFileAt(uri)) }
                        }
                    }
                }
                if (!viewmodel.isSoloMode && sharedPlaylists) {
                    RouteRow(Icons.AutoMirrored.Filled.PlaylistAdd, strings.roomRoutePlaylist, strings.roomRoutePlaylistNote) {
                        playlistPicker.launch()
                    }
                }
            }
        }
    }

    /** A route row, 54dp tall: the glyph, the name, and one line of note, never more. */
    @Composable
    private fun RouteRow(icon: ImageVector, label: String, note: String, onClick: () -> Unit) {
        val p = palette
        ListRow(onClick = onClick, minHeight = Space.rowTall) {
            Icon(icon, contentDescription = null, tint = p.inkDim, modifier = Modifier.size(Space.glyph))
            RowGap()
            Column(Modifier.weight(1f).padding(vertical = Space.gapTight)) {
                // One line each: a large text size shrinks them before anything is cut.
                Text(label, style = Type.label, color = p.ink, maxLines = 1, autoSize = FontSizeRange(Type.label.fontSize))
                Text(note, style = Type.note, color = p.inkDim, maxLines = 1, overflow = TextOverflow.Ellipsis, autoSize = FontSizeRange(Type.note.fontSize))
            }
        }
    }

    /**
     * The link form: the link field with a paste button, and a note that says what the link is.
     * A resolvable link is one that the media resolver can turn into a stream. For such a link,
     * the note shows the title and the duration before the user confirms. When the resolve fails,
     * the note says so, and the form offers to play the link as it is. The link then does not
     * fail quietly later.
     */
    @Composable
    private fun LinkForm(onCancel: () -> Unit, onPlayed: () -> Unit) {
        val p = palette
        val viewmodel = LocalRoomViewmodel.current
        val clipboard = LocalClipboard.current
        val scope = rememberCoroutineScope()
        val resolverOn by MEDIA_RESOLVER_ENABLED.watchPref()
        var url by remember { mutableStateOf("") }
        /* With a remote or a keyboard, the one field takes focus, because the user came here to
         * type in it. The panel swaps the routes for this form, so the element that had focus has
         * just left the tree. */
        val urlFocus = remember { FocusRequester() }
        val remoteOrKeyboard = LocalIsTelevision.current || LocalInputModeManager.current.inputMode == InputMode.Keyboard
        LaunchedEffect(remoteOrKeyboard) {
            if (!remoteOrKeyboard) return@LaunchedEffect
            repeat(8) {
                delay(60)
                if (runCatching { urlFocus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            }
        }
        var preview by remember { mutableStateOf<ResolvedMedia?>(null) }
        var resolving by remember { mutableStateOf(false) }
        var failed by remember { mutableStateOf(false) }
        val trimmed = url.trim()
        val kind = remember(trimmed, resolverOn) { recognise(trimmed, resolverOn) }

        LaunchedEffect(trimmed, kind) {
            preview = null
            failed = false
            resolving = false
            if (kind != LinkKind.Resolvable) return@LaunchedEffect
            delay(500)
            resolving = true
            preview = runCatching { mediaResolver.resolve(trimmed) }.getOrNull()
            failed = preview == null
            resolving = false
        }

        fun play() {
            onPlayed()
            if (trimmed.isNotBlank()) viewmodel.viewModelScope.launch { viewmodel.player.injectVideoURL(trimmed) }
        }

        Column(Modifier.fillMaxWidth().padding(Space.gutter)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field(
                    value = url,
                    onValueChange = { url = it },
                    focusRequester = urlFocus,
                    modifier = Modifier.weight(1f),
                    placeholder = strings.roomAddmediaOnlineUrl,
                    leading = Icons.Filled.Link,
                    keyboardType = KeyboardType.Uri,
                    onImeAction = { if (trimmed.isNotBlank() && !resolving) play() },
                    name = strings.roomAddmediaOnlineUrl,
                )
                GlyphButton(Icons.Filled.ContentPaste, name = strings.roomLinkPaste) {
                    scope.launch { clipboard.getClipEntry()?.getText()?.let { url = it } }
                }
            }
            val note = when {
                resolving -> strings.roomLinkResolving
                preview != null -> listOfNotNull(
                    preview?.title,
                    preview?.durationSec?.let { timestampFromMillis((it * 1000).toLong()) },
                ).joinToString("  ")
                failed -> strings.roomLinkFailed
                kind == LinkKind.Empty -> supportedSites(strings)
                kind == LinkKind.Direct -> strings.roomLinkDirect
                kind == LinkKind.ResolverOff -> strings.roomLinkResolverOff
                kind == LinkKind.Resolvable -> supportedSites(strings)
                else -> strings.roomLinkUnknown
            }
            Text(
                text = note,
                style = Type.note,
                color = if (failed || kind == LinkKind.ResolverOff) p.warn else p.inkDim,
                modifier = Modifier.padding(top = Space.gapTight),
            )
            if (resolving) ProgressBar(null, Modifier.fillMaxWidth().padding(top = Space.gapTight))
            Spacer(Modifier.height(Space.gutter))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SecondaryAction(strings.cancel, modifier = Modifier.weight(1f), onClick = onCancel)
                Spacer(Modifier.padding(horizontal = Space.gapTight))
                AccentAction(
                    text = if (failed) strings.roomLinkPlayAnyway else strings.done,
                    modifier = Modifier.weight(1f),
                    enabled = trimmed.isNotBlank() && !resolving,
                    onClick = { play() },
                )
            }
        }
    }

    private fun supportedSites(s: AppStrings) = if (platform == Platform.IOS) s.roomLinkSitesYt else s.roomLinkSitesFull

    /** The kind of a pasted link, as far as the app can tell before the user confirms it. */
    private enum class LinkKind { Empty, Direct, Resolvable, ResolverOff, Unknown }

    private fun recognise(url: String, resolverOn: Boolean): LinkKind {
        if (url.isBlank()) return LinkKind.Empty
        if (urlLooksLikeDirectMedia(url)) return LinkKind.Direct
        val host = url.substringAfter("://").substringBefore('/').lowercase()
        val resolvable = extractYtId(url) != null ||
            (platform != Platform.IOS && listOf("soundcloud.com", "bandcamp.com", "media.ccc.de").any { host.endsWith(it) })
        return when {
            resolvable && resolverOn -> LinkKind.Resolvable
            resolvable -> LinkKind.ResolverOff
            else -> LinkKind.Unknown
        }
    }
}
