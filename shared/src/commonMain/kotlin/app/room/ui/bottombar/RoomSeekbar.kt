package app.room.ui.bottombar

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import app.player.chapterStills
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import app.i18n.strings
import app.uicomponents.controls.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.LocalRoomViewmodel
import app.LocalRoomUiState
import app.player.models.Chapter
import app.player.models.MediaFile
import app.preferences.Preferences.CHAPTER_DOTS_CLICKABLE
import app.preferences.Preferences.SHOW_CHAPTER_DOTS
import app.preferences.watchPref
import app.theme.Radius
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.chromeSurface
import app.uicomponents.controls.ScrubTrack
import app.uicomponents.controls.Timecode
import app.uicomponents.controls.formatTimecode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * The seek bar of the room (the group of people watching together): the elapsed time, the scrub
 * track with its buffered band and chapter marks, and the total time. Dragging shows a preview.
 * The release sends one seek through the dispatcher's single seek path, from the position
 * captured on the first drag event.
 */
@Composable
fun RoomSeekbar(modifier: Modifier) {
    val viewmodel = LocalRoomViewmodel.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope { Dispatchers.Main }
    /* Collected only while the HUD shows. The bar stays composed at alpha 0 while hidden, so a
     * plain collect would recompose it four times a second behind an invisible layer. */
    val hudVisible by LocalRoomUiState.current.visibleHUD.collectAsState()
    val positionState = remember { mutableLongStateOf(viewmodel.playerManager.timeCurrentMillis.value) }
    LaunchedEffect(hudVisible) {
        if (hudVisible) viewmodel.playerManager.timeCurrentMillis.collect { positionState.longValue = it }
    }
    val positionMs = positionState.longValue
    val durationMs by viewmodel.playerManager.timeFullMillis.collectAsState()
    /* Collected, not read from the viewmodel's plain getter. That getter reads a StateFlow value,
     * which Compose does not observe, so a new file would keep the chapter marks of the old one. */
    val media by viewmodel.playerManager.media.collectAsState()

    /* The only caller of analyzeChapters. Engines clear the list first, so a second caller would
     * blank the marks mid-frame. It runs again once the duration is known, because most engines
     * know the chapters only after they parse the container. The snapshot below is taken after
     * each run, not once per file name. */
    var chapterListVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(media?.location, durationMs > 0L) {
        viewmodel.player.analyzeChapters(media ?: return@LaunchedEffect)
        chapterListVersion++
    }
    val chapters = remember(media?.location, chapterListVersion) { media?.chapters?.toList() ?: emptyList() }
    /* Gated like the position, for the same reason. ExoPlayer is the only engine that reports a
     * buffered position, and it reports it from the same loop. A plain collect would recompose the
     * whole bar twice a second behind a hidden HUD. */
    val bufferedState = remember { mutableLongStateOf(viewmodel.playerManager.timeBufferedMillis.value) }
    LaunchedEffect(hudVisible) {
        if (hudVisible) viewmodel.playerManager.timeBufferedMillis.collect { bufferedState.longValue = it }
    }
    val bufferedMs = bufferedState.longValue
    val showMarks by SHOW_CHAPTER_DOTS.watchPref()
    val marksClickable by CHAPTER_DOTS_CLICKABLE.watchPref()

    var dragging by remember(media?.location) { mutableStateOf(false) }
    var preview by remember(media?.location) { mutableFloatStateOf(0f) }
    var dragFromMs by remember(media?.location) { mutableLongStateOf(0L) }
    var dragMedia by remember(media?.location) { mutableStateOf<MediaFile?>(null) }
    var trackWidthPx by remember { mutableIntStateOf(0) }
    var showChapters by remember(media?.location) { mutableStateOf(false) }
    DisposableEffect(media?.location) {
        onDispose { viewmodel.uiState.scrubbing.value = false }
    }
    val hasChapterList = viewmodel.player.supportsChapters && chapters.isNotEmpty()

    val known = durationMs > 0L
    val fraction = when {
        dragging -> preview
        known -> (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        else -> 0f
    }
    val shownMs = if (dragging) (preview * durationMs).roundToLong() else positionMs

    /* Marks skip anything in the first second, because a chapter mark at zero carries no
     * information. */
    val marks: List<Pair<Chapter, Float>> = remember(chapters, durationMs) {
        if (!known) emptyList()
        else chapters.filter { it.timeOffsetMillis / 1000 != 0L }
            .map { it to (it.timeOffsetMillis.toFloat() / durationMs).coerceIn(0f, 1f) }
    }
    /* The tick positions, remembered outside the per-tick body. ScrubTrack takes a plain List,
     * and a new list on every playhead move would force the whole track (semantics, gesture and
     * key modifiers included) to rebuild two to four times a second. */
    val tickFractions: List<Float> = remember(marks, showMarks) {
        if (showMarks) marks.map { it.second } else emptyList()
    }
    val activeMark = marks.indexOfLast { it.second <= fraction }
    val chapterUnderPlayhead = marks.getOrNull(activeMark)?.first

    /* D-pad Left and Right do the configured jump and announce it, and the track's own key step
     * (keyStep = 0f) leaves those keys to this handler. Up and Down fall through to focus
     * traversal. Key up is ignored, or the seek would fire twice. */
    val keys = Modifier.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionLeft -> { viewmodel.dispatcher.seekBckwd(); true }
            Key.DirectionRight -> { viewmodel.dispatcher.seekFrwrd(); true }
            else -> false
        }
    }

    /* Both timecodes get the width of the widest string that their format can take. So the track
     * does not shrink, and the thumb does not jump, when the elapsed time passes an hour. */
    val measurer = rememberTextMeasurer()
    // timestampFromMillis pads to mm:ss under an hour and to hh:mm:ss from there.
    val widest = if (!known || durationMs >= 3_600_000L) "00:00:00" else "00:00"
    // Remembered on its real inputs: the string, the type role and the density. The string is one
    // of two constants, so measuring it again on every position tick would gain nothing.
    val timeStyle = Type.value
    val timeWidth = remember(widest, density, measurer, timeStyle) {
        with(density) { measurer.measure(widest, timeStyle).size.width.toDp() }
    }

    Row(modifier.then(keys), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(timeWidth), contentAlignment = Alignment.CenterEnd) { Timecode(shownMs) }
        Box(Modifier.weight(1f).padding(horizontal = Space.gap)) {
            // A file replacement cancels the old pointer gesture, including same-name files.
            key(media?.location) {
                ScrubTrack(
                    value = fraction,
                    enabled = known,
                    /* The bar owns Left and Right (they jump), so a remote can leave it only up or
                     * down. Down is set here. Without it, the keys beside the bar on the same row
                     * could be reached only from the rail. */
                    modifier = Modifier
                        .onSizeChanged { trackWidthPx = it.width }
                        .focusProperties { down = viewmodel.uiState.controlsFocus },
                    ticks = tickFractions,
                    activeTick = if (showMarks) activeMark else -1,
                    buffered = if (known && bufferedMs > 0L) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else null,
                    keyStep = 0f,
                    describe = { f -> formatTimecode((f * durationMs).roundToLong()) },
                    name = strings.roomSeekbarName,
                    onLongPress = if (hasChapterList) ({ showChapters = true }) else null,
                    onValueChange = { f ->
                        if (media == null || viewmodel.playerManager.media.value !== media) return@ScrubTrack
                        if (!dragging) {
                            dragging = true
                            dragMedia = media
                            viewmodel.uiState.scrubbing.value = true
                            // The origin is captured before the engine moves, on the first drag event.
                            dragFromMs = viewmodel.player.currentPositionMs()
                        }
                        preview = f
                    },
                    onValueChangeFinished = {
                        if (!dragging) return@ScrubTrack
                        dragging = false
                        viewmodel.uiState.scrubbing.value = false
                        if (dragMedia == null || viewmodel.playerManager.media.value !== dragMedia) return@ScrubTrack
                        dragMedia = null
                        val targetMs = (preview * durationMs).roundToLong()
                        // A release on a chapter mark jumps to it: a 20dp target around a 1dp mark.
                        val hitRadius = with(density) { 10.dp.toPx() }
                        val hit = if (showMarks && marksClickable && trackWidthPx > 0) {
                            marks.firstOrNull { abs(it.second - preview) * trackWidthPx <= hitRadius }
                        } else null
                        if (hit != null) {
                            scope.launch(Dispatchers.Main.immediate) { viewmodel.player.jumpToChapter(hit.first) }
                        } else {
                            viewmodel.dispatcher.seek(targetMs, fromMs = dragFromMs)
                        }
                    },
                )
            }

            if (dragging && known && trackWidthPx > 0) {
                // The still of the chapter under the finger, from the file itself (see ChapterStills).
                val stills = chapterStills
                var still by remember { mutableStateOf<ImageBitmap?>(null) }
                LaunchedEffect(chapterUnderPlayhead, media?.location) {
                    val shown = media
                    val chapter = chapterUnderPlayhead
                    still = if (stills == null || shown == null || chapter == null) null
                    else stills.still(shown, chapter, marks.getOrNull(activeMark + 1)?.first?.timeOffsetMillis ?: durationMs)
                }
                ScrubBubble(
                    text = formatTimecode(shownMs) + (chapterUnderPlayhead?.name?.let { "  $it" } ?: ""),
                    fraction = preview,
                    trackWidthPx = trackWidthPx,
                    still = if (chapterUnderPlayhead != null) still else null,
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }
        }
        Box(Modifier.width(timeWidth), contentAlignment = Alignment.CenterStart) { Timecode(if (known) durationMs else null, dim = true) }
    }

    ChaptersModal(open = showChapters, onDismiss = { showChapters = false })
}

/**
 * The target time above the finger, on a chromeSurface panel, kept inside the track. With a
 * [still], the chapter's frame sits above the time. The bubble grows upward, so its foot stays
 * 4dp above the track.
 */
@Composable
private fun ScrubBubble(text: String, fraction: Float, trackWidthPx: Int, still: ImageBitmap?, modifier: Modifier = Modifier) {
    var bubbleSize by remember { mutableStateOf(IntSize.Zero) }
    val x = (fraction * trackWidthPx - bubbleSize.width / 2f).coerceIn(0f, (trackWidthPx - bubbleSize.width).toFloat().coerceAtLeast(0f))
    Column(
        modifier = modifier
            .offset { IntOffset(x.toInt(), -(bubbleSize.height + 4.dp.roundToPx())) }
            .onSizeChanged { bubbleSize = it }
            .chromeSurface(Radius.panelShape)
            .padding(horizontal = Space.gap, vertical = Space.gapTight),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (still != null) {
            Image(
                still,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.padding(bottom = Space.gapTight).size(128.dp, 72.dp).clip(Radius.tightShape),
            )
        }
        Text(text, style = Type.value, color = palette.ink, maxLines = 1)
    }
}
