package app.room.ui.bottombar

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import kotlin.math.roundToInt
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.core.animateFloatAsState
import app.theme.LocalPalette
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import syncplaymobile.shared.generated.resources.room_route_link
import syncplaymobile.shared.generated.resources.action_close
import syncplaymobile.shared.generated.resources.action_back
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
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_button_desc_add

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
    var open by remember { mutableStateOf(false) }
    var linkMode by remember { mutableStateOf(false) }
    LaunchedEffect(hasVideo) { if (hasVideo) { open = false; linkMode = false } }

    // Before a file loads this is the room's primary control, so it claims the initial D-pad focus.
    val initialFocus = LocalRoomInitialFocus.current
    val expanded = !hasVideo && open
    /* One block that is the key and the card. A single progress value drives its width, its
     * height and both contents' alpha from the same frame: both contents are measured up front,
     * so the block knows its target size at once and grows out of the key's corner in one
     * straight tween, the key's label fading as the card's rows come in. The brand gradient
     * stays on it at full strength; the rows take dark ink, the way the key's label does. */
    // A plain standard curve: the emphasized decelerate the rest of the app uses reads as a spring on a block this size.
    val t by animateFloatAsState(if (expanded) 1f else 0f, tween(Motion.moveMs, easing = FastOutSlowInEasing), label = "addMorph")
    val onBrand = p.copy(
        ink = p.ground,
        inkDim = p.ground.copy(alpha = 0.72f),
        inkFaint = p.ground.copy(alpha = 0.45f),
        rule = p.ground.copy(alpha = 0.25f),
        accent = p.ground,
    )
    Layout(
        modifier = Modifier
            .padding(Space.gapTight)
            .clip(Radius.panelShape)
            .background(Brush.horizontalGradient(p.brandField)),
        content = {
            // The key stays composed until the card is fully in, and the card until the key is.
            if (t < 1f || !expanded) {
                Box(Modifier.layoutId("key").alpha(1f - t)) {
                    AddVideoButton(
                        modifier = Modifier.then(if (!hasVideo && initialFocus != null) Modifier.focusRequester(initialFocus) else Modifier),
                        expanded = !hasVideo,
                        onClick = {
                            if (hasVideo) {
                                ui.toggleAddMedia()
                            } else {
                                // The card needs the room's right side to itself.
                                ui.closeSidePanels()
                                open = true
                            }
                        },
                    )
                }
            }
            if (t > 0f || expanded) {
                Box(Modifier.layoutId("card").alpha(t).width(MorphWidth)) {
                    CompositionLocalProvider(LocalPalette provides onBrand) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(Space.row).padding(start = Space.gapTight, end = Space.gapTight),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (linkMode) GlyphButton(BackGlyph, name = stringResource(Res.string.action_back)) { linkMode = false }
                                else Spacer(Modifier.width(Space.touchMin))
                                Text(
                                    text = stringResource(if (linkMode) Res.string.room_route_link else Res.string.room_button_desc_add),
                                    style = Type.label,
                                    color = onBrand.ink,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f),
                                )
                                GlyphButton(CloseGlyph, name = stringResource(Res.string.action_close)) { open = false; linkMode = false }
                            }
                            Rule()
                            CardAddMedia.AddMediaBody(linkMode = linkMode, onLinkMode = { linkMode = it }, onClose = { open = false; linkMode = false })
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

private val MorphWidth = 340.dp

/** Collapsed to a glyph once a file plays; the primary action of the room before that. */
@Composable
fun AddVideoButton(modifier: Modifier, expanded: Boolean, onClick: () -> Unit) {
    if (!expanded) {
        GlyphButton(Icons.Filled.AddToQueue, name = stringResource(Res.string.room_button_desc_add), modifier = modifier, size = Space.glyphLarge, onClick = onClick)
    } else {
        PrimaryAction(stringResource(Res.string.room_button_desc_add), onClick = onClick, modifier = modifier.width(180.dp))
    }
}
