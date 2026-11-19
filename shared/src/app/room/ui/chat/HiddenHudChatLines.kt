package app.room.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import app.LocalChatPalette
import app.LocalRoomViewmodel
import app.preferences.Preferences.MSG_BG_OPACITY
import app.preferences.Preferences.MSG_FADING_DURATION
import app.preferences.Preferences.MSG_FONTSIZE
import app.preferences.Preferences.MSG_MAXCOUNT
import app.preferences.Preferences.MSG_OUTLINE_THICKNESS
import app.preferences.Preferences.MSG_SHADOW_ACTIVATE
import app.preferences.watchPref
import app.room.models.Message
import app.room.models.fadingMessages
import app.theme.Motion
import app.theme.Radius
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

/**
 * Where the lines of the landscape chat list sit on screen, in root pixels. The chat list writes
 * the places, and [HiddenHudChatLines] reads them to show a new line in its own place while the
 * HUD (the controls over the video) is hidden.
 */
@Stable
class ChatLinePlaces {
    /** The bounds of each composed line, by message id. */
    internal val lines = mutableStateMapOf<Long, Rect>()

    /** The bounds of the list's visible area. A line shows only inside it. */
    internal var viewport by mutableStateOf<Rect?>(null)

    /** How many landscape chat lists are composed. A list counts itself in and out. */
    internal var lists by mutableIntStateOf(0)

    /** True while a landscape chat list is composed. The GIF drawer, for one, takes its place. */
    val listShown: Boolean get() = lists > 0
}

/** The chat line places of the room screen. It is null outside a room. */
val LocalChatLinePlaces = staticCompositionLocalOf<ChatLinePlaces?> { null }

/** Tells [places] where the line of [id] is, for as long as the line is composed. */
internal fun Modifier.reportsChatLine(places: ChatLinePlaces, id: Long): Modifier =
    onGloballyPositioned { places.lines[id] = Rect(it.positionInRoot(), Size(it.size.width.toFloat(), it.size.height.toFloat())) }

/**
 * The new chat lines from other people while the HUD is hidden, each at the exact place where the
 * chat list shows it. The rest of the chat stays hidden. When the HUD comes back, the list fades in
 * under these lines, so no line moves. The fading count preference sets how many lines show, and
 * the fading duration sets how long each one stays.
 *
 * [hudAlpha] is the HUD's current fade. The lines stay until the HUD is fully back, because until
 * then the list under them is not fully visible. The list holds the same lines, so a screen reader
 * and key focus skip these copies.
 */
@Composable
fun HiddenHudChatLines(hudAlpha: () -> Float) {
    val viewmodel = LocalRoomViewmodel.current
    val places = LocalChatLinePlaces.current ?: return
    val hudFullyBack by remember { derivedStateOf { hudAlpha() >= 1f } }
    val isHUDVisible by viewmodel.uiState.visibleHUD.collectAsState()

    val holdSeconds by MSG_FADING_DURATION.watchPref()
    val maxCount by MSG_MAXCOUNT.watchPref()
    val allMessages by viewmodel.session.messageSequence.collectAsState()
    val muted = viewmodel.uiState.mutedUsers.toSet()
    // The lines that show now, and every line shown since the HUD hid, so a line can fade out.
    var showing by remember { mutableStateOf(emptySet<Long>()) }
    val shownSinceHide = remember { mutableStateListOf<Message>() }
    LaunchedEffect(allMessages, holdSeconds, maxCount, muted, isHUDVisible) {
        // While the HUD comes back, the lines keep their state. They go when the HUD is fully back.
        if (isHUDVisible) return@LaunchedEffect
        val hold = holdSeconds.coerceAtLeast(0).seconds
        while (true) {
            val recent = fadingMessages(allMessages, hold, maxCount, muted)
            showing = recent.mapTo(mutableSetOf()) { it.id }
            recent.forEach { line -> if (shownSinceHide.none { it.id == line.id }) shownSinceHide.add(line) }
            if (recent.isEmpty()) break
            val nextExpiry = recent.minOf { (hold - it.receivedAt.elapsedNow()).inWholeMilliseconds }
            delay(nextExpiry.coerceAtLeast(1) + 1)
        }
    }
    LaunchedEffect(hudFullyBack) {
        if (hudFullyBack) {
            shownSinceHide.clear()
            showing = emptySet()
        }
    }
    if (hudFullyBack || shownSinceHide.isEmpty() || !places.listShown) return
    val viewport = places.viewport ?: return

    // The same list as the chat list, so each line finds the line above it and groups the same way.
    val messages = remember(allMessages, muted) { allMessages.filter { it.sender == null || it.sender !in muted } }
    val chatPalette = LocalChatPalette.current
    val outlineThickness by MSG_OUTLINE_THICKNESS.watchPref()
    val shadowOn by MSG_SHADOW_ACTIVATE.watchPref()
    val fontSize by MSG_FONTSIZE.watchPref()
    val bgOpacity by MSG_BG_OPACITY.watchPref()
    val style = MessageStyle(fontSize, outlineThickness.toFloat().takeIf { it > 0f }, shadowOn, chatPalette.includeTimestamp)
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .clearAndSetSemantics {}
            .focusProperties { canFocus = false },
    ) {
        val area = viewport.translate(-origin)
        Box(
            Modifier
                .offset { IntOffset(area.left.roundToInt(), area.top.roundToInt()) }
                .size(with(density) { area.width.toDp() }, with(density) { area.height.toDp() })
                .clipToBounds(),
        ) {
            // A part of the chat at its place in the list, in the coordinates of this box.
            fun Rect.inArea(): Rect = translate(-origin).translate(-area.topLeft)

            // A grouped line has no name of its own: the first line of its group shows the name.
            // While that first line does not show here, its name shows alone at its place.
            val groups = LinkedHashMap<Int, MutableList<Message>>()
            for (line in shownSinceHide) {
                val index = messages.indexOfFirst { it.id == line.id }
                if (index < 0) continue
                val place = places.lines[line.id] ?: continue
                key("line", line.id) {
                    ChatPart(visible = line.id in showing, place = place.inArea()) {
                        MessageRow(
                            message = line,
                            previous = messages.getOrNull(index - 1),
                            chatPalette = chatPalette,
                            style = style,
                            // The list draws one panel behind all lines. Here each line brings its own.
                            modifier = if (bgOpacity > 0) Modifier.background(Color(50, 50, 50, bgOpacity), Radius.controlShape) else Modifier,
                        )
                    }
                }
                val first = groupStart(messages, index)
                if (first != index) groups.getOrPut(first) { mutableListOf() }.add(line)
            }
            for ((first, lines) in groups) {
                val head = messages[first]
                val place = places.lines[head.id] ?: continue
                key("name", head.id) {
                    ChatPart(visible = head.id !in showing && lines.any { it.id in showing }, place = place.inArea()) {
                        MessageRow(head, messages.getOrNull(first - 1), chatPalette, style, onlyName = true)
                    }
                }
            }
        }
    }
}

/** The index of the first line of the group that holds the line at [index]. That first line shows the name. */
private fun groupStart(messages: List<Message>, index: Int): Int {
    var first = index
    while (first > 0 && grouped(messages[first], messages[first - 1])) first--
    return first
}

/**
 * One part of the chat at [place], with the width of its line in the list. Each part has a fade of
 * its own: a fade draws its content in a layer that cuts off anything outside the part. A part
 * that is visible from the start still fades in.
 */
@Composable
private fun ChatPart(visible: Boolean, place: Rect, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val state = remember { MutableTransitionState(false) }
    state.targetState = visible
    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn(Motion.quick()),
        exit = fadeOut(Motion.move()),
        modifier = Modifier
            .offset { IntOffset(place.left.roundToInt(), place.top.roundToInt()) }
            .width(with(density) { place.width.toDp() }),
    ) { content() }
}
