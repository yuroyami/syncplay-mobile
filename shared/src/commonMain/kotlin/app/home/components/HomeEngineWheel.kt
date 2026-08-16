package app.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.player.PlayerEngine
import app.uicomponents.tvFocusable
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.connect_engine_badge_default
import syncplaymobile.shared.generated.resources.connect_engine_badge_experimental
import syncplaymobile.shared.generated.resources.connect_engine_badge_system
import syncplaymobile.shared.generated.resources.connect_engine_badge_unavailable
import kotlin.math.abs

/**
 * The engine chooser as a horizontal wheel, in place.
 *
 * It replaced a segmented button group that split one row evenly between every engine. That shape
 * only worked while there were three: each engine's share shrinks as engines are added, and by
 * five the icons were touching and the selected engine's own name clipped mid-word. A wheel is
 * flat in the number of engines, so a sixth costs nothing, and nothing has to open to use it.
 *
 * Whatever settles in the center IS the selection; there is no separate confirm. The chrome says
 * so without a single decoration that is not doing work:
 *
 * - A fixed capsule sits behind the center slot. Items scroll through it; whatever it frames is
 *   the selection. It marks the SLOT, so it never moves and never lags a fling.
 * - Items turn in depth as they leave the center (a barrel seen from outside), shrink and fade
 *   with real distance rather than with a selected flag, so the emphasis tracks the finger
 *   continuously instead of snapping between two states.
 * - Both edges dissolve into transparency; the tilted, half-faded neighbours are themselves the
 *   sign that more engines sit that way. The earlier design said it twice more, with a gradient
 *   outline and animated chevrons, and both read as noise. They are gone.
 * - Each engine that crosses the center clicks, the physics a wheel has in hardware.
 */
@Composable
fun HomeEngineWheel(
    engines: List<PlayerEngine>,
    selectedEngine: String,
    onSelectEngine: (PlayerEngine) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (engines.isEmpty()) return

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    // Set around every scroll this composable issues itself, so the settle collector can tell a
    // user's gesture (which selects) from a correction (which must not re-select).
    var programmatic by remember { mutableStateOf(false) }

    // Bumped on every settle so the centering effect below re-runs even when the preference did
    // NOT change. That is the unavailable-engine path: the caller refuses the selection, the
    // pref keeps its old value, and without this tick nothing would pull the wheel back.
    var settleTick by remember { mutableIntStateOf(0) }

    val currentEngines by rememberUpdatedState(engines)
    val currentSelected by rememberUpdatedState(selectedEngine)
    val currentOnSelect by rememberUpdatedState(onSelectEngine)

    LaunchedEffect(listState) {
        // Only a real settle counts: a scroll that ended. snapshotFlow emits its current value
        // immediately, so reacting to "not scrolling" alone would fire once on first composition,
        // before the wheel has been centered, and silently rewrite the saved engine to whichever
        // one happens to sit at index 0.
        var wasScrolling = false
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            val settled = wasScrolling && !scrolling
            wasScrolling = scrolling
            if (!settled || programmatic) return@collect
            val landed = currentEngines.getOrNull(listState.centeredIndex()) ?: return@collect
            if (landed.name != currentSelected) currentOnSelect(landed)
            settleTick++
        }
    }

    LaunchedEffect(listState) {
        // The detent click. Every index that crosses the center during a scroll ticks once,
        // including the spring-back after a refused selection: a hardware wheel clicks on the
        // way back too. The initial emission is swallowed so composition itself never buzzes.
        var last = -1
        snapshotFlow { listState.centeredIndex() }.collect { index ->
            val first = last < 0
            if (!first && index != last && listState.isScrollInProgress) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            }
            last = index
        }
    }

    LaunchedEffect(selectedEngine, engines, settleTick) {
        val target = engines.indexOfFirst { it.name == selectedEngine }
        if (target < 0 || listState.centeredIndex() == target) return@LaunchedEffect
        programmatic = true
        try {
            // Instant on the very first pass so the wheel never renders off-target, animated
            // afterwards so a refused selection visibly springs back rather than teleporting.
            if (settleTick == 0) listState.scrollToItem(target) else listState.animateScrollToItem(target)
        } finally {
            programmatic = false
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(WheelHeight)
            .tvFocusable(shape = WheelShape)
            .onPreviewKeyEvent { event ->
                // One D-pad stop for the whole wheel: left and right step the selection instead
                // of walking into individual items, which is how a wheel is expected to behave.
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val step = when (event.key) {
                    Key.DirectionLeft -> -1
                    Key.DirectionRight -> 1
                    else -> return@onPreviewKeyEvent false
                }
                val next = (listState.centeredIndex() + step).coerceIn(0, engines.lastIndex)
                scope.launch { listState.animateScrollToItem(next) }
                true
            },
    ) {
        val sidePadding = ((maxWidth - ItemWidth) / 2).coerceAtLeast(0.dp)
        val fadePx = with(LocalDensity.current) { FadeWidth.toPx() }

        // The selection slot. Behind the items on purpose: the wheel scrolls THROUGH it, and a
        // plain surface role keeps it chrome, not content. Its sides dissolve to nothing, so it
        // reads as light on the slot rather than a box drawn around one item.
        val capsule = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(CapsuleWidth)
                .height(CapsuleHeight)
                .background(
                    brush = Brush.horizontalGradient(
                        0f to capsule.copy(alpha = 0f),
                        0.30f to capsule,
                        0.70f to capsule,
                        1f to capsule.copy(alpha = 0f),
                    ),
                    shape = CapsuleShape,
                ),
        )

        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                // Offscreen compositing is what lets the mask below erase pixels: without it the
                // DstIn blend has no isolated layer to punch through.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val stop = (fadePx / size.width).coerceIn(0.01f, 0.49f)
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to Color.Transparent,
                            stop to Color.Black,
                            1f - stop to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
            contentPadding = PaddingValues(horizontal = sidePadding),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically,
            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
        ) {
            itemsIndexed(engines) { index, engine ->
                EngineWheelItem(
                    engine = engine,
                    modifier = Modifier
                        .width(ItemWidth)
                        .graphicsLayer {
                            // Draw-phase read of layoutInfo: a new scroll offset invalidates
                            // drawing alone, never composition, so dragging costs no recomposition.
                            val signed = listState.signedDistanceFromCenter(index)
                                .coerceIn(-MaxDistance, MaxDistance)
                            val distance = abs(signed).coerceAtMost(1f)
                            val shrink = lerp(1f, NeighbourScale, distance)
                            scaleX = shrink
                            scaleY = shrink
                            alpha = lerp(1f, NeighbourAlpha, distance)
                            // The barrel: an item's outer edge falls away as it leaves the
                            // center, as if the strip wrapped a cylinder seen from outside.
                            // The inward pull closes the gap the turn opens at the item's
                            // near edge, so the strip stays a drum, not separated cards.
                            rotationY = signed * MaxTiltDegrees
                            translationX = -signed * size.width * InwardPull
                            cameraDistance = CameraDistanceDp * density
                        }
                        .clickable(interactionSource = null, indication = null) {
                            scope.launch { listState.animateScrollToItem(index) }
                        },
                )
            }
        }
    }
}

@Composable
private fun EngineWheelItem(engine: PlayerEngine, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Image(
            painter = painterResource(engine.img),
            modifier = Modifier.size(IconSize),
            contentDescription = null,
        )
        Text(
            text = engine.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        // One badge only: a wheel item is too narrow for a row of them, so the most important
        // thing to know before committing wins. Unavailable (cannot be used at all) beats
        // experimental (can be used, may misbehave) beats default (what you get if you never
        // choose) beats system (the platform's own player). The ladder only ever matters for an
        // engine that is two things at once, e.g. ExoPlayer in the exoOnly flavor, which is both
        // the system player and the default; "Default" is the more useful of the two there.
        val badge: Pair<String, Color>? = when {
            !engine.isAvailable ->
                stringResource(Res.string.connect_engine_badge_unavailable) to MaterialTheme.colorScheme.error
            engine.isExperimental ->
                stringResource(Res.string.connect_engine_badge_experimental) to MaterialTheme.colorScheme.error
            engine.isDefault ->
                stringResource(Res.string.connect_engine_badge_default) to MaterialTheme.colorScheme.primary
            engine.isSystem ->
                stringResource(Res.string.connect_engine_badge_system) to MaterialTheme.colorScheme.tertiary
            else -> null
        }
        if (badge != null) {
            Text(
                text = badge.first,
                style = MaterialTheme.typography.labelSmall,
                color = badge.second,
                maxLines = 1,
                modifier = Modifier
                    .background(color = badge.second.copy(alpha = 0.12f), shape = BadgeShape)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}

/** Index of the item whose center is nearest the viewport center. */
private fun LazyListState.centeredIndex(): Int {
    val info = layoutInfo
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
    return info.visibleItemsInfo.minByOrNull { abs((it.offset + it.size / 2f) - center) }?.index
        ?: firstVisibleItemIndex
}

/**
 * How far [index] sits from the viewport center, in item widths. 0 at the center, negative on
 * the left side, positive on the right. Items not laid out count as a full slot away.
 */
private fun LazyListState.signedDistanceFromCenter(index: Int): Float {
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return 1f
    if (item.size == 0) return 1f
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
    return ((item.offset + item.size / 2f) - center) / item.size
}

private val WheelShape = RoundedCornerShape(24.dp)
private val BadgeShape = RoundedCornerShape(6.dp)
private val CapsuleShape = RoundedCornerShape(22.dp)
private val ItemWidth = 112.dp
private val WheelHeight = 96.dp
private val CapsuleWidth = 128.dp
private val CapsuleHeight = 84.dp
private val IconSize = 36.dp
private val FadeWidth = 36.dp
private const val NeighbourScale = 0.74f
private const val NeighbourAlpha = 0.32f
private const val MaxDistance = 1.25f
private const val MaxTiltDegrees = 42f
private const val InwardPull = 0.08f
private const val CameraDistanceDp = 9f
