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
 * The add key in the bottom bar. With a file playing, it opens the add-media side panel. Before a
 * file loads, it is the main control of the room (the group of people watching together). A tap
 * then grows the key in place into the card of routes (the ways to add media), from the key's own
 * corner. The card folds back once a route has run.
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

    // Before a file loads, this is the room's main control, so it takes the initial D-pad focus.
    val initialFocus = LocalRoomInitialFocus.current
    LaunchedEffect(open) { if (!open) linkMode = false }
    val expanded = !hasVideo && open
    /* One block is both the key and the card. A single progress value drives the width, the
     * height and the alpha of both contents in the same frame. Both contents are measured up
     * front, so the block knows its target size at once. It grows out of the key's corner in one
     * straight tween, while the key's label fades and the card's rows come in. The brand gradient
     * stays on the block at full strength, and the rows use dark ink, like the key's label. */
    // A plain standard curve. The emphasized decelerate curve used elsewhere in the app looks
    // like a spring on a block this size.
    val t by animateFloatAsState(if (expanded) 1f else 0f, tween(Motion.moveMs, easing = FastOutSlowInEasing), label = "addMorph")
    /* The block is the key until it opens into the card, and both are one layout. So a remote
     * needs a focus handover: into the card's first route when it opens, and back to the key when
     * it closes. */
    val cardFocus = remember { FocusRequester() }
    val keyFocus = remember { FocusRequester() }
    val remoteOrKeyboard = LocalIsTelevision.current || LocalInputModeManager.current.inputMode == InputMode.Keyboard
    var seenExpanded by remember { mutableStateOf(expanded) }
    // After the link form closes, the routes are new again and need focus of their own.
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
    // A white focus ring on the block. A gradient ring would vanish into the block, and a ring in
    // the block's dark ink would barely show.
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
                                // The card needs the side of the room to itself.
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
 * The palette for content on the brand block. The block is painted with the gradient, so the ink
 * is dark, and an accent fill must be dark too, or it would match the block. Labels on a filled
 * control come from [Palette.inkOn], which sees this dark accent and returns a light color.
 */
internal fun Palette.onBrandBlock(): Palette = copy(
    ink = ground,
    inkDim = ground.copy(alpha = 0.72f),
    inkFaint = ground.copy(alpha = 0.45f),
    rule = ground.copy(alpha = 0.25f),
    accent = ground,
)

private val MorphWidth = 340.dp

/** An icon button once a file plays, and the main action button of the room before that. */
@Composable
fun AddVideoButton(modifier: Modifier, expanded: Boolean, onClick: () -> Unit) {
    if (!expanded) {
        GlyphButton(Icons.Filled.AddToQueue, name = strings.roomButtonDescAdd, modifier = modifier, size = Space.glyphLarge, onClick = onClick)
    } else {
        PrimaryAction(strings.roomButtonDescAdd, onClick = onClick, modifier = modifier.width(180.dp))
    }
}
