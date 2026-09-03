package app.room.ui.bottombar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import app.uicomponents.controls.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.LocalRoomViewmodel
import app.LocalRoomUiState
import app.player.models.Chapter
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
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_seekbar_name
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * The transport track: elapsed timecode, the drawn scrub track with its buffered band and
 * chapter marks, and the total. Preview while dragging, one seek on release through the
 * dispatcher's single seek path, with the origin captured on the first drag event.
 */
@Composable
fun RoomSeekbar(modifier: Modifier) {
    val viewmodel = LocalRoomViewmodel.current
    val p = palette
    val density = LocalDensity.current
    val scope = rememberCoroutineScope { Dispatchers.Main }
    /* Collected only while the HUD shows. The bar stays composed at alpha 0 when hidden, so a
     * plain collect recomposed it four times a second behind an invisible layer, forever. */
    val hudVisible by LocalRoomUiState.current.visibleHUD.collectAsState()
    val positionState = remember { mutableLongStateOf(viewmodel.playerManager.timeCurrentMillis.value) }
    LaunchedEffect(hudVisible) {
        if (hudVisible) viewmodel.playerManager.timeCurrentMillis.collect { positionState.longValue = it }
    }
    val positionMs = positionState.longValue
    val durationMs by viewmodel.playerManager.timeFullMillis.collectAsState()

    /* The one caller of analyzeChapters: engines clear the list first, so a second caller
     * would blank the marks mid-frame. It runs again once the duration lands, because most
     * engines know the chapters only after the container is parsed, and the snapshot below
     * is taken after each run rather than once per file name. */
    var chapterListVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(viewmodel.media?.fileName, durationMs > 0L) {
        viewmodel.player.analyzeChapters(viewmodel.media ?: return@LaunchedEffect)
        chapterListVersion++
    }
    val chapters = remember(viewmodel.media?.fileName, chapterListVersion) { viewmodel.media?.chapters?.toList() ?: emptyList() }
    val bufferedMs by viewmodel.playerManager.timeBufferedMillis.collectAsState()
    val showMarks by SHOW_CHAPTER_DOTS.watchPref()
    val marksClickable by CHAPTER_DOTS_CLICKABLE.watchPref()

    var dragging by remember { mutableStateOf(false) }
    var preview by remember { mutableFloatStateOf(0f) }
    var dragFromMs by remember { mutableLongStateOf(0L) }
    var trackWidthPx by remember { mutableIntStateOf(0) }
    var showChapters by remember { mutableStateOf(false) }
    val hasChapterList = viewmodel.player.supportsChapters && chapters.isNotEmpty()

    val known = durationMs > 0L
    val fraction = when {
        dragging -> preview
        known -> (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        else -> 0f
    }
    val shownMs = if (dragging) (preview * durationMs).roundToLong() else positionMs

    /* Marks skip anything in the first second, as the dots did: the chapter-0-at-zero marker
     * carries no information. */
    val marks: List<Pair<Chapter, Float>> = remember(chapters, durationMs) {
        if (!known) emptyList()
        else chapters.filter { it.timeOffsetMillis / 1000 != 0L }
            .map { it to (it.timeOffsetMillis.toFloat() / durationMs).coerceIn(0f, 1f) }
    }
    val activeMark = marks.indexOfLast { it.second <= fraction }
    val chapterUnderPlayhead = marks.getOrNull(activeMark)?.first

    /* D-pad LEFT/RIGHT perform the configured jump and announce it; the track's own key step
     * defers to this. UP/DOWN fall through to focus traversal, and key up is ignored or the
     * seek fires twice. */
    val keys = Modifier.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionLeft -> { viewmodel.dispatcher.seekBckwd(); true }
            Key.DirectionRight -> { viewmodel.dispatcher.seekFrwrd(); true }
            else -> false
        }
    }

    /* Both timecodes get the width of the widest string their format can take, so the track
     * does not shrink (and the thumb jump) when the elapsed time crosses an hour. */
    val measurer = rememberTextMeasurer()
    // timestampFromMillis pads to mm:ss under an hour and to hh:mm:ss from there.
    val widest = if (!known || durationMs >= 3_600_000L) "00:00:00" else "00:00"
    val timeWidth = with(density) { measurer.measure(widest, Type.value).size.width.toDp() }

    Row(modifier.then(keys), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(timeWidth), contentAlignment = Alignment.CenterEnd) { Timecode(shownMs) }
        Box(Modifier.weight(1f).padding(horizontal = Space.gap)) {
            ScrubTrack(
                value = fraction,
                enabled = known,
                modifier = Modifier.onSizeChanged { trackWidthPx = it.width },
                ticks = if (showMarks) marks.map { it.second } else emptyList(),
                activeTick = if (showMarks) activeMark else -1,
                buffered = if (known && bufferedMs > 0L) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else null,
                keyStep = 0f,
                describe = { f -> formatTimecode((f * durationMs).roundToLong()) },
                name = stringResource(Res.string.room_seekbar_name),
                onLongPress = if (hasChapterList) ({ showChapters = true }) else null,
                onValueChange = { f ->
                    if (!dragging) {
                        dragging = true
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

            if (dragging && known && trackWidthPx > 0) {
                ScrubBubble(
                    text = formatTimecode(shownMs) + (chapterUnderPlayhead?.name?.let { "  $it" } ?: ""),
                    fraction = preview,
                    trackWidthPx = trackWidthPx,
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }
        }
        Box(Modifier.width(timeWidth), contentAlignment = Alignment.CenterStart) { Timecode(if (known) durationMs else null, dim = true) }
    }

    ChaptersModal(open = showChapters, onDismiss = { showChapters = false })
}

/** The target timecode above the finger, on the chrome tier, clamped inside the track. */
@Composable
private fun ScrubBubble(text: String, fraction: Float, trackWidthPx: Int, modifier: Modifier = Modifier) {
    var bubbleWidthPx by remember { mutableIntStateOf(0) }
    val x = (fraction * trackWidthPx - bubbleWidthPx / 2f).coerceIn(0f, (trackWidthPx - bubbleWidthPx).toFloat().coerceAtLeast(0f))
    Box(
        modifier = modifier
            .offset { IntOffset(x.toInt(), -34.dp.roundToPx()) }
            .onSizeChanged { bubbleWidthPx = it.width }
            .chromeSurface(Radius.panelShape)
            .padding(horizontal = Space.gap, vertical = Space.gapTight),
    ) {
        Text(text, style = Type.value, color = palette.ink, maxLines = 1)
    }
}
