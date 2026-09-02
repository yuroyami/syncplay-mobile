package app.room.ui.bottombar

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.runtime.Composable
import app.theme.LocalPalette
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.AnimatedContent
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
    /* One block that is the key and the card: the brand gradient stays on it at full strength,
     * its size animates between the two, and the contents crossfade inside it, so the violet
     * key becomes a violet card. The card's rows take dark ink, the way the key's label does. */
    val onBrand = p.copy(
        ink = p.ground,
        inkDim = p.ground.copy(alpha = 0.72f),
        inkFaint = p.ground.copy(alpha = 0.45f),
        rule = p.ground.copy(alpha = 0.25f),
        accent = p.ground,
    )
    AnimatedContent(
        targetState = expanded,
        modifier = Modifier
            .padding(Space.gapTight)
            .clip(Radius.panelShape)
            .background(Brush.horizontalGradient(p.brandField)),
        transitionSpec = {
            (fadeIn(Motion.move()) togetherWith fadeOut(Motion.quick()))
                .using(SizeTransform(clip = true) { _, _ -> Motion.move() })
        },
        label = "addKey",
    ) { showCard ->
        if (showCard) {
            CompositionLocalProvider(LocalPalette provides onBrand) {
                Column(Modifier.width(MorphWidth)) {
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
        } else {
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
