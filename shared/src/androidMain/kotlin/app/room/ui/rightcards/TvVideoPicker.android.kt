package app.room.ui.rightcards

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.i18n.strings
import app.theme.Radius
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.LocalIsTelevision
import app.uicomponents.controls.PrimaryAction
import app.uicomponents.controls.ProgressBar
import app.uicomponents.controls.SecondaryAction
import app.uicomponents.controls.Text
import app.uicomponents.controls.controlStates
import app.uicomponents.frames.Modal
import app.uicomponents.frames.ModalSize
import app.utils.platformFileAt
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The television answer to picking a local file. It reads the device's own video library rather
 * than asking another app to show a picker.
 *
 * A television does have a system picker (the photo picker answers `PICK_IMAGES` and
 * `GET_CONTENT`), but a remote cannot work it: focus reaches its tabs and its banner and never
 * the grid of videos. The documents picker, which is what the app asks for elsewhere, is a stub
 * that only says no app can do this. Pull request #163 reported that and wrote the library query
 * and the permission flow below; the list is drawn with this app's own controls and thumbnails.
 */
@Composable
internal actual fun rememberTvVideoPicker(onPicked: (PlatformFile) -> Unit): (() -> Unit)? {
    if (!LocalIsTelevision.current) return null

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var refused by remember { mutableStateOf(false) }
    var videos by remember { mutableStateOf(emptyList<LocalVideo>()) }
    /* The list arrives after the dialog opens, so the frame's own entry focus has already settled
     * on Close by then. The first video asks for focus itself once it exists. */
    val firstCell = remember { FocusRequester() }
    LaunchedEffect(videos) {
        if (videos.isEmpty()) return@LaunchedEffect
        repeat(8) {
            delay(60)
            if (runCatching { firstCell.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        }
    }

    fun load() {
        scope.launch {
            loading = true
            refused = false
            open = true
            videos = withContext(Dispatchers.IO) { context.localVideos() }
            loading = false
        }
    }

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            load()
        } else {
            refused = true
            loading = false
            open = true
        }
    }

    Modal(
        open = open,
        onDismiss = { open = false },
        title = strings.roomTvVideosTitle,
        size = ModalSize.Full,
        inset = false,
        actions = { SecondaryAction(strings.actionClose, onClick = { open = false }) },
    ) {
        when {
            loading -> Note(strings.roomTvVideosLoading) { ProgressBar(null, Modifier.fillMaxWidth(0.4f)) }
            refused -> Note(strings.roomTvVideosPermission) {
                PrimaryAction(strings.roomTvVideosAllow, onClick = { ask.launch(permission) })
            }
            videos.isEmpty() -> Note(strings.roomTvVideosEmpty, hint = strings.roomTvVideosEmptyHint)
            /* Bounded on purpose: the modal's body scrolls, so a lazy grid inside it would be
             * measured with no height at all and draw nothing. */
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = TILE_MIN),
                modifier = Modifier.fillMaxWidth().heightIn(max = GRID_MAX),
                horizontalArrangement = Arrangement.spacedBy(Space.gap),
                verticalArrangement = Arrangement.spacedBy(Space.gap),
                contentPadding = PaddingValues(Space.gutter),
            ) {
                itemsIndexed(videos, key = { _, video -> video.uri }) { index, video ->
                    VideoTile(
                        video = video,
                        modifier = if (index == 0) Modifier.focusRequester(firstCell) else Modifier,
                        onPick = {
                            open = false
                            onPicked(platformFileAt(video.uri))
                        },
                    )
                }
            }
        }
    }

    return {
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) load() else ask.launch(permission)
    }
}

/** One video: its own picture where the library has one, its length over the corner, its name under it. */
@Composable
private fun VideoTile(video: LocalVideo, modifier: Modifier, onPick: () -> Unit) {
    val context = LocalContext.current
    val source = remember { MutableInteractionSource() }
    val p = palette
    val spoken = "${video.name}, ${video.details}"
    // Only the tiles on screen are composed, so only those ask the library for a picture.
    val thumbnail by produceState<ImageBitmap?>(null, video.uri) {
        value = withContext(Dispatchers.IO) { context.videoThumbnail(video)?.asImageBitmap() }
    }

    Column(
        modifier = modifier
            .clip(Radius.panelShape)
            .clickable(interactionSource = source, indication = null, onClick = onPick)
            .controlStates(source, Radius.panelShape)
            .padding(Space.gapTight)
            .semantics { contentDescription = spoken },
        verticalArrangement = Arrangement.spacedBy(Space.gapTight),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(Radius.controlShape).background(p.panel),
            contentAlignment = Alignment.Center,
        ) {
            val picture = thumbnail
            if (picture != null) {
                Image(
                    bitmap = picture,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                Icon(Icons.Filled.Movie, contentDescription = null, tint = p.inkDim, modifier = Modifier.size(Space.glyphLarge))
            }
            Text(
                text = video.duration,
                style = Type.value,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Space.gapTight)
                    .background(Color.Black.copy(alpha = 0.6f), Radius.tightShape)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        Text(video.name, style = Type.label, color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(video.size, style = Type.note, color = p.inkDim, maxLines = 1)
    }
}

/** Anything the list cannot show: a wait, a refusal, an empty library. Readable over a picture. */
@Composable
private fun Note(text: String, hint: String? = null, action: @Composable (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.gap),
    ) {
        Icon(Icons.Filled.VideoLibrary, contentDescription = null, tint = palette.inkDim, modifier = Modifier.size(Space.hero))
        Text(text, style = Type.label, color = palette.ink, textAlign = TextAlign.Center)
        if (hint != null) Text(hint, style = Type.note, color = palette.inkDim, textAlign = TextAlign.Center)
        // An action here answers one line of text, so it takes a button's width, not the dialog's.
        if (action != null) Box(Modifier.widthIn(max = 260.dp)) { action() }
    }
}

/** A tile no narrower than this, and a grid no taller, so the dialog keeps its own scroll. */
private val TILE_MIN = 170.dp
private val GRID_MAX = 330.dp

private data class LocalVideo(
    val id: Long,
    val uri: String,
    val name: String,
    val durationMs: Long,
    val sizeBytes: Long,
) {
    val duration: String get() = duration(durationMs)
    val size: String get() = megabytes(sizeBytes)
    val details: String get() = "$duration  $size"
}

/** Newest first, which is what someone looking for the film they just copied over wants. */
private fun Context.localVideos(): List<LocalVideo> {
    val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    val columns = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.SIZE,
    )
    return runCatching {
        contentResolver.query(collection, columns, null, null, "${MediaStore.Video.Media.DATE_MODIFIED} DESC")?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val name = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val duration = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val size = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            buildList {
                while (cursor.moveToNext()) {
                    val rowId = cursor.getLong(id)
                    add(
                        LocalVideo(
                            id = rowId,
                            uri = ContentUris.withAppendedId(collection, rowId).toString(),
                            name = cursor.getString(name).orEmpty(),
                            durationMs = cursor.getLong(duration),
                            sizeBytes = cursor.getLong(size),
                        ),
                    )
                }
            }
        }.orEmpty()
    }.getOrDefault(emptyList())
}

/**
 * The library's own picture for a video. It is kept by the system, so this costs a read rather
 * than a decode. A library with none (a file just copied over) answers null and the tile keeps
 * its glyph.
 */
private fun Context.videoThumbnail(video: LocalVideo): Bitmap? = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        contentResolver.loadThumbnail(Uri.parse(video.uri), Size(512, 288), null)
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Video.Thumbnails.getThumbnail(contentResolver, video.id, MediaStore.Video.Thumbnails.MINI_KIND, null)
    }
}.getOrNull()

private fun duration(ms: Long): String {
    val seconds = ms.coerceAtLeast(0L) / 1000L
    val minutes = seconds / 60L
    return "$minutes:${(seconds % 60L).toString().padStart(2, '0')}"
}

private fun megabytes(bytes: Long): String = "${(bytes.coerceAtLeast(0L) / (1024.0 * 1024.0) * 10).toLong() / 10.0} MB"
