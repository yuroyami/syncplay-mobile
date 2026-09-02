package app.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import app.uicomponents.controls.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.player.PlayerEngine
import app.theme.Motion
import app.theme.Radius
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.controls.Feedback
import app.uicomponents.controls.HelpTip
import app.uicomponents.controls.Tone
import app.uicomponents.controls.VerticalRule
import app.uicomponents.controls.controlStates
import app.uicomponents.controls.pressFeedback
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.connect_engine_badge_default
import syncplaymobile.shared.generated.resources.connect_engine_badge_experimental
import syncplaymobile.shared.generated.resources.connect_engine_badge_system
import syncplaymobile.shared.generated.resources.connect_engine_badge_unavailable
import syncplaymobile.shared.generated.resources.connect_engine_note_default
import syncplaymobile.shared.generated.resources.connect_engine_note_experimental
import syncplaymobile.shared.generated.resources.connect_engine_note_system
import syncplaymobile.shared.generated.resources.connect_engine_note_unavailable
import syncplaymobile.shared.generated.resources.engine_info_avplayer
import syncplaymobile.shared.generated.resources.engine_info_exoplayer
import syncplaymobile.shared.generated.resources.engine_info_kiteplayer
import syncplaymobile.shared.generated.resources.engine_info_mpv
import syncplaymobile.shared.generated.resources.engine_info_vlckit

/**
 * Every engine at once: one hairline frame, one cell per engine with its mark and name, the
 * active cell filled with the accent and carrying the bottom edge, the same drawing as the
 * segmented control. The note under the frame says in words what the selected engine is, and
 * the tip beside it tells its story. Nothing scrolls and nothing tilts, so the third engine is
 * never off screen and a cell in a list never overlaps its neighbour.
 */
@Composable
fun HomeEnginePicker(
    engines: List<PlayerEngine>,
    selectedEngine: String,
    onSelectEngine: (PlayerEngine) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (engines.isEmpty()) return
    val p = palette
    val selectedIndex = engines.indexOfFirst { it.name == selectedEngine }

    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(CellHeight)
                .clip(Radius.controlShape)
                .border(Space.hair, p.rule, Radius.controlShape)
                .selectableGroup(),
        ) {
            engines.forEachIndexed { i, engine ->
                if (i > 0) VerticalRule()
                EngineCell(
                    engine = engine,
                    active = i == selectedIndex,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = {
                        if (i == selectedIndex) return@EngineCell
                        Feedback.tick()
                        onSelectEngine(engine)
                    },
                )
            }
        }

        // The note follows the selection with a crossfade, which reduced motion turns into a cut;
        // the tip beside it holds the long story of the engine.
        val selected = engines.getOrNull(selectedIndex)
        val badge = selected?.let(::badgeOf)
        val info = selected?.let(::infoOf)
        Row(Modifier.fillMaxWidth().padding(top = Space.gapTight), verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(
                targetState = badge,
                transitionSpec = { fadeIn(Motion.quick()) togetherWith fadeOut(Motion.quick()) },
                label = "engineNote",
                modifier = Modifier.weight(1f),
            ) { shown ->
                Text(
                    text = shown?.let { stringResource(it.note) } ?: "",
                    style = Type.note,
                    color = if (shown?.tone == Tone.Bad) p.bad else p.inkDim,
                    minLines = 1,
                    maxLines = 2,
                )
            }
            if (info != null) HelpTip(stringResource(info))
        }
    }
}

private val CellHeight = 104.dp
private val MarkSize = 40.dp

/** What a badge says, in a word for the cell and a line for the note. */
private class EngineBadge(val label: StringResource, val note: StringResource, val tone: Tone)

/**
 * One badge per engine, the most important thing to know first: missing beats experimental
 * beats default beats the platform's own player.
 */
/** The long story of an engine, by name, for the tip. */
private fun infoOf(engine: PlayerEngine): StringResource? = when (engine.name.lowercase()) {
    "exoplayer" -> Res.string.engine_info_exoplayer
    "mpv" -> Res.string.engine_info_mpv
    "kiteplayer" -> Res.string.engine_info_kiteplayer
    "avplayer" -> Res.string.engine_info_avplayer
    "vlckit" -> Res.string.engine_info_vlckit
    else -> null
}

private fun badgeOf(engine: PlayerEngine): EngineBadge? = when {
    !engine.isAvailable -> EngineBadge(Res.string.connect_engine_badge_unavailable, Res.string.connect_engine_note_unavailable, Tone.Bad)
    engine.isExperimental -> EngineBadge(Res.string.connect_engine_badge_experimental, Res.string.connect_engine_note_experimental, Tone.Warn)
    engine.isDefault -> EngineBadge(Res.string.connect_engine_badge_default, Res.string.connect_engine_note_default, Tone.Accent)
    engine.isSystem -> EngineBadge(Res.string.connect_engine_badge_system, Res.string.connect_engine_note_system, Tone.Neutral)
    else -> null
}

@Composable
private fun EngineCell(engine: PlayerEngine, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = palette
    val source = remember { MutableInteractionSource() }
    val available = engine.isAvailable
    val badge = badgeOf(engine)
    val fill by animateColorAsState(if (active) p.accent.copy(alpha = 0.16f) else p.accent.copy(alpha = 0f), Motion.quick(), label = "fill")
    val edge by animateColorAsState(if (active) p.accent else p.accent.copy(alpha = 0f), Motion.quick(), label = "edge")
    // The spoken name carries the badge word, so the square never has to.
    val spoken = engine.name + (badge?.let { ", " + stringResource(it.label) } ?: "")

    Column(
        modifier = modifier
            // Always selectable: tapping an engine this build lacks lets the caller say so.
            .selectable(selected = active, role = Role.RadioButton, interactionSource = source, indication = null, onClick = onClick)
            .hoverable(source)
            .semantics { contentDescription = spoken }
            .drawBehind {
                drawRect(fill)
                val w = 2.dp.toPx()
                drawRect(edge, Offset(0f, size.height - w), Size(size.width, w))
            }
            .controlStates(source, Radius.controlShape)
            .pointerHoverIcon(PointerIcon.Hand)
            .pressFeedback(source)
            .padding(horizontal = 2.dp, vertical = Space.gap),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(engine.img),
            contentDescription = null,
            modifier = Modifier.size(MarkSize),
            // An engine that cannot be picked loses its colour, not only its word.
            colorFilter = if (available) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
            alpha = if (available) 1f else 0.5f,
        )
        Spacer(Modifier.height(Space.gapTight))
        Text(
            text = engine.name,
            style = Type.label,
            color = when {
                !available -> p.disabled
                active -> p.ink
                else -> p.inkDim
            },
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
