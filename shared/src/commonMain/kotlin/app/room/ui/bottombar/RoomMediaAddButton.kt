package app.room.ui.bottombar

import kotlinx.coroutines.delay
import app.uicomponents.LocalIsTelevision
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.foundation.focusGroup
import app.uicomponents.controls.LocalFocusRing
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import app.i18n.strings
import kotlin.math.roundToInt
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.core.animateFloatAsState
import app.theme.LocalPalette
import app.theme.Palette
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import app.uicomponents.controls.Text
import app.uicomponents.controls.Rule
import app.uicomponents.controls.CloseGlyph
import app.uicomponents.controls.BackGlyph
import app.theme.palette
import app.theme.Type
import app.theme.Radius
import app.theme.Motion
import app.room.ui.rightcards.CardAddMedia
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import app.LocalRoomUiState
import app.LocalRoomViewmodel
import app.room.LocalRoomInitialFocus
import app.theme.Space
import app.uicomponents.controls.GlyphButton
import app.uicomponents.controls.PrimaryAction

/**
 * The add key in the transport. With a file playing it opens the add-media side panel. Before
 * one loads it is the room's primary control, and a tap morphs the key itself into the routes
 * card in place, growing from its own corner; the card folds back once a route has run.
 */
@Composable
fun RoomMediaAddButton() {
    val viewmodel = LocalRoomViewmodel.current
    val ui = LocalRoomUiState.current
    val p = palette
    val hasVideo by viewmodel.hasVideo.collectAsState()
    val open by ui.mediaAddExpanded.collectAsState()
    var linkMode by remember { mutableStateOf(false) }
    LaunchedEffect(hasVideo) { if (hasVideo) { ui.collapseMediaAdd(); linkMode = false } }

    // Before a file loads this is the room's primary control, so it claims the initial D-pad focus.
    val initialFocus = LocalRoomInitialFocus.current
    LaunchedEffect(open) { if (!open) linkMode = false }
    val expanded = !hasVideo && open
    /* One block that is the key and the card. A single progress value drives its width, its
     * height and both contents' alpha from the same frame: both contents are measured up front,
     * so the block knows its target size at once and grows out of the key's corner in one
     * straight tween, the key's label fading as the card's rows come in. The brand gradient
     * stays on it at full strength; the rows take dark ink, the way the key's label does. */
    // A plain standard curve: the emphasized decelerate the rest of the app uses reads as a spring on a block this size.
    val t by animateFloatAsState(if (expanded) 1f else 0f, tween(Motion.moveMs, easing = FastOutSlowInEasing), label = "addMorph")
    /* The block is the key until it opens into the card, and both are one layout, so a remote
     * needs the handover: into the card's first route when it opens, back to the key when it closes. */
    val cardFocus = remember { FocusRequester() }
    val keyFocus = remember { FocusRequester() }
    val remoteOrKeyboard = LocalIsTelevision.current || LocalInputModeManager.current.inputMode == InputMode.Keyboard
    var seenExpanded by remember { mutableStateOf(expanded) }
    // Coming back from the link form, the routes are new again and need focus of their own.
    LaunchedEffect(linkMode) {
        if (!linkMode && expanded && remoteOrKeyboard) {
            repeat(8) {
                delay(60)
                if (runCatching { cardFocus.requestFocus(FocusDirection.Enter) }.getOrDefault(false)) return@LaunchedEffect
            }
        }
    }
    LaunchedEffect(expanded) {
        if (expanded != seenExpanded && remoteOrKeyboard) {
            repeat(8) {
                delay(60)
                val target = if (expanded) cardFocus else keyFocus
                val direction = if (expanded) FocusDirection.Enter else FocusDirection.Exit
                if (runCatching { target.requestFocus(direction) }.getOrDefault(false)) return@repeat
            }
        }
        seenExpanded = expanded
    }
    val onBrand = p.onBrandBlock()
    // A white ring on the block: its own gradient would vanish into it, and its dark ink barely shows.
    val onBrandRing = remember(p.ink) { SolidColor(p.ink) }
    Layout(
        modifier = Modifier
            .padding(Space.gapTight)
            .clip(Radius.panelShape)
            .background(Brush.horizontalGradient(p.brandField)),
        content = {
            // The key stays composed until the card is fully in, and the card until the key is.
            if (t < 1f || !expanded) {
                Box(Modifier.layoutId("key").alpha(1f - t)) {
                    CompositionLocalProvider(LocalFocusRing provides onBrandRing) {
                    AddVideoButton(
                        modifier = Modifier
                            .focusRequester(keyFocus)
                            .then(if (!hasVideo && initialFocus != null) Modifier.focusRequester(initialFocus) else Modifier),
                        expanded = !hasVideo,
                        onClick = {
                            if (hasVideo) {
                                ui.toggleAddMedia()
                            } else {
                                // The card needs the room's right side to itself.
                                ui.expandMediaAdd()
                            }
                        },
                    )
                    }
                }
            }
            if (t > 0f || expanded) {
                Box(Modifier.layoutId("card").alpha(t).width(MorphWidth).focusRequester(cardFocus).focusGroup()) {
                    CompositionLocalProvider(LocalPalette provides onBrand, LocalFocusRing provides onBrandRing) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(Space.row).padding(start = Space.gapTight, end = Space.gapTight),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (linkMode) GlyphButton(BackGlyph, name = strings.actionBack) { linkMode = false }
                                else Spacer(Modifier.width(Space.touchMin))
                                Text(
                                    text = if (linkMode) strings.roomRouteLink else strings.roomButtonDescAdd,
                                    style = Type.label,
                                    color = onBrand.ink,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f),
                                )
                                GlyphButton(CloseGlyph, name = strings.actionClose) { ui.collapseMediaAdd(); linkMode = false }
                            }
                            Rule()
                            CardAddMedia.AddMediaBody(linkMode = linkMode, onLinkMode = { linkMode = it }, onClose = { ui.collapseMediaAdd(); linkMode = false })
                        }
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val key = measurables.firstOrNull { it.layoutId == "key" }?.measure(loose)
        val card = measurables.firstOrNull { it.layoutId == "card" }?.measure(loose)
        val fromW = key?.width ?: card!!.width
        val fromH = key?.height ?: card!!.height
        val toW = card?.width ?: fromW
        val toH = card?.height ?: fromH
        val w = (fromW + (toW - fromW) * t).roundToInt()
        val h = (fromH + (toH - fromH) * t).roundToInt()
        layout(w, h) {
            // Both sit on the block's bottom-end corner, the corner the key lives in.
            key?.placeRelative(w - key.width, h - key.height)
            card?.placeRelative(w - card.width, h - card.height)
        }
    }
}

/**
 * The palette for what sits ON the brand block. The block is painted with the gradient, so ink
 * goes dark and an accent fill has to be dark too, or it would be the block's own colour. Labels
 * on a filled control come from [Palette.inkOn], which reads this dark accent and answers light.
 */
internal fun Palette.onBrandBlock(): Palette = copy(
    ink = ground,
    inkDim = ground.copy(alpha = 0.72f),
    inkFaint = ground.copy(alpha = 0.45f),
    rule = ground.copy(alpha = 0.25f),
    accent = ground,
)

private val MorphWidth = 340.dp

/** Collapsed to a glyph once a file plays; the primary action of the room before that. */
@Composable
fun AddVideoButton(modifier: Modifier, expanded: Boolean, onClick: () -> Unit) {
    if (!expanded) {
        GlyphButton(Icons.Filled.AddToQueue, name = strings.roomButtonDescAdd, modifier = modifier, size = Space.glyphLarge, onClick = onClick)
    } else {
        PrimaryAction(strings.roomButtonDescAdd, onClick = onClick, modifier = modifier.width(180.dp))
    }
}
