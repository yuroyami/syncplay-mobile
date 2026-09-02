package app.room.ui.rightcards

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
import androidx.compose.ui.platform.LocalClipboard
import app.player.resolver.ResolvedMedia
import app.player.resolver.extractYoutubeId
import app.player.resolver.mediaResolver
import app.player.resolver.urlLooksLikeDirectMedia
import app.utils.getText
import app.utils.playlistExs
import app.utils.videoFileKitType
import io.github.vinceglb.filekit.PlatformFile
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
import app.uicomponents.controls.Feedback
import app.uicomponents.controls.Field
import app.uicomponents.controls.GlyphButton
import app.uicomponents.controls.Icon
import app.uicomponents.controls.ListRow
import app.uicomponents.controls.ProgressBar
import app.uicomponents.controls.RowGap
import app.uicomponents.controls.SecondaryAction
import app.uicomponents.controls.Text
import app.uicomponents.frames.PanelFrame
import app.utils.Platform
import app.utils.platform
import app.utils.platformCallback
import app.utils.timestampFromMillis
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.action_back
import syncplaymobile.shared.generated.resources.action_close
import syncplaymobile.shared.generated.resources.cancel
import syncplaymobile.shared.generated.resources.done
import syncplaymobile.shared.generated.resources.room_addmedia_online_url
import syncplaymobile.shared.generated.resources.room_button_desc_add
import syncplaymobile.shared.generated.resources.room_link_direct
import syncplaymobile.shared.generated.resources.room_link_failed
import syncplaymobile.shared.generated.resources.room_link_paste
import syncplaymobile.shared.generated.resources.room_link_play_anyway
import syncplaymobile.shared.generated.resources.room_link_resolver_off
import syncplaymobile.shared.generated.resources.room_link_resolving
import syncplaymobile.shared.generated.resources.room_link_sites_full
import syncplaymobile.shared.generated.resources.room_link_sites_youtube
import syncplaymobile.shared.generated.resources.room_link_unknown
import syncplaymobile.shared.generated.resources.room_route_device
import syncplaymobile.shared.generated.resources.room_route_device_note
import syncplaymobile.shared.generated.resources.room_route_link
import syncplaymobile.shared.generated.resources.room_route_playlist
import syncplaymobile.shared.generated.resources.room_route_playlist_note
import syncplaymobile.shared.generated.resources.room_route_share
import syncplaymobile.shared.generated.resources.room_route_share_note

/**
 * Adding media, as a side panel: the routes a file can come in by, each a 54dp row. The link
 * route swaps the rows for the link form in the same panel. Pickers launch straight away: the
 * modal race (FileKit #575) does not apply to a panel.
 */
object CardAddMedia {

    @Composable
    fun AddMediaPanel(shape: Shape) {
        val viewmodel = LocalRoomViewmodel.current
        val ui = LocalRoomUiState.current
        var linkMode by remember { mutableStateOf(false) }

        val videoPicker = rememberFilePickerLauncher(type = videoFileKitType) { file ->
            file ?: return@rememberFilePickerLauncher
            viewmodel.viewModelScope.launch { viewmodel.player.injectVideoFile(file) }
        }
        val playlistPicker = rememberFilePickerLauncher(type = FileKitType.File(extensions = playlistExs)) { file ->
            file ?: return@rememberFilePickerLauncher
            viewmodel.playlistManager.loadPlaylistLocally(file, alsoShuffle = false)
        }

        fun close() {
            linkMode = false
            ui.toggleAddMedia(false)
        }

        PanelFrame(
            title = stringResource(if (linkMode) Res.string.room_route_link else Res.string.room_button_desc_add),
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            centerTitle = true,
            actions = {
                if (linkMode) GlyphButton(BackGlyph, name = stringResource(Res.string.action_back)) { linkMode = false }
                GlyphButton(CloseGlyph, name = stringResource(Res.string.action_close), onClick = ::close)
            },
        ) {
            if (linkMode) {
                LinkForm(onCancel = { linkMode = false }, onPlayed = ::close)
            } else {
                RouteRow(Icons.Filled.FolderOpen, stringResource(Res.string.room_route_device), stringResource(Res.string.room_route_device_note)) {
                    Feedback.tick(); close(); videoPicker.launch()
                }
                RouteRow(Icons.Filled.Link, stringResource(Res.string.room_route_link), stringResource(supportedSites())) {
                    Feedback.tick(); linkMode = true
                }
                if (platform == Platform.Android) {
                    RouteRow(Icons.Filled.Cloud, stringResource(Res.string.room_route_share), stringResource(Res.string.room_route_share_note)) {
                        Feedback.tick(); close()
                        platformCallback.launchSystemFilePicker { uri ->
                            uri ?: return@launchSystemFilePicker
                            viewmodel.viewModelScope.launch { viewmodel.player.injectVideoFile(PlatformFile(uri)) }
                        }
                    }
                }
                if (!viewmodel.isSoloMode) {
                    RouteRow(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(Res.string.room_route_playlist), stringResource(Res.string.room_route_playlist_note)) {
                        Feedback.tick(); close(); playlistPicker.launch()
                    }
                }
            }
        }
    }

    /** A 54dp route: glyph, name, one note line. */
    @Composable
    private fun RouteRow(icon: ImageVector, label: String, note: String, onClick: () -> Unit) {
        val p = palette
        ListRow(onClick = onClick, minHeight = Space.rowTall) {
            Icon(icon, contentDescription = null, tint = p.inkDim, modifier = Modifier.size(Space.glyph))
            RowGap()
            Column(Modifier.weight(1f).padding(vertical = Space.gapTight)) {
                Text(label, style = Type.label, color = p.ink, maxLines = 1)
                Text(note, style = Type.note, color = p.inkDim, maxLines = 2)
            }
        }
    }

    /**
     * The link form: the hairline field with paste, a note that says what the link is, and for
     * a resolvable link the title and duration before confirming. A resolve that fails says so
     * and offers to play the link as it is instead of failing quietly later.
     */
    @Composable
    private fun LinkForm(onCancel: () -> Unit, onPlayed: () -> Unit) {
        val p = palette
        val viewmodel = LocalRoomViewmodel.current
        val clipboard = LocalClipboard.current
        val scope = rememberCoroutineScope()
        val resolverOn by MEDIA_RESOLVER_ENABLED.watchPref()
        var url by remember { mutableStateOf("") }
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
                    modifier = Modifier.weight(1f),
                    placeholder = stringResource(Res.string.room_addmedia_online_url),
                    leading = Icons.Filled.Link,
                    keyboardType = KeyboardType.Uri,
                    onImeAction = { if (trimmed.isNotBlank() && !resolving) play() },
                    name = stringResource(Res.string.room_addmedia_online_url),
                )
                GlyphButton(Icons.Filled.ContentPaste, name = stringResource(Res.string.room_link_paste)) {
                    scope.launch { clipboard.getClipEntry()?.getText()?.let { url = it } }
                }
            }
            val note = when {
                resolving -> stringResource(Res.string.room_link_resolving)
                preview != null -> listOfNotNull(
                    preview?.title,
                    preview?.durationSec?.let { timestampFromMillis((it * 1000).toLong()) },
                ).joinToString("  ")
                failed -> stringResource(Res.string.room_link_failed)
                kind == LinkKind.Empty -> stringResource(supportedSites())
                kind == LinkKind.Direct -> stringResource(Res.string.room_link_direct)
                kind == LinkKind.ResolverOff -> stringResource(Res.string.room_link_resolver_off)
                kind == LinkKind.Resolvable -> stringResource(supportedSites())
                else -> stringResource(Res.string.room_link_unknown)
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
                SecondaryAction(stringResource(Res.string.cancel), modifier = Modifier.weight(1f), onClick = onCancel)
                Spacer(Modifier.padding(horizontal = Space.gapTight))
                AccentAction(
                    text = stringResource(if (failed) Res.string.room_link_play_anyway else Res.string.done),
                    modifier = Modifier.weight(1f),
                    enabled = trimmed.isNotBlank() && !resolving,
                    onClick = { play() },
                )
            }
        }
    }

    private fun supportedSites() = if (platform == Platform.IOS) Res.string.room_link_sites_youtube else Res.string.room_link_sites_full

    /** What the app can make of a pasted link before it is confirmed. */
    private enum class LinkKind { Empty, Direct, Resolvable, ResolverOff, Unknown }

    private fun recognise(url: String, resolverOn: Boolean): LinkKind {
        if (url.isBlank()) return LinkKind.Empty
        if (urlLooksLikeDirectMedia(url)) return LinkKind.Direct
        val host = url.substringAfter("://").substringBefore('/').lowercase()
        val resolvable = extractYoutubeId(url) != null ||
            (platform != Platform.IOS && listOf("soundcloud.com", "bandcamp.com", "media.ccc.de").any { host.endsWith(it) })
        return when {
            resolvable && resolverOn -> LinkKind.Resolvable
            resolvable -> LinkKind.ResolverOff
            else -> LinkKind.Unknown
        }
    }
}
