package app.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import app.i18n.AppStrings
import app.i18n.strings
import app.player.PlayerEngine
import app.theme.Motion
import app.theme.Radius
import app.theme.Space
import app.theme.Type
import app.theme.palette
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainWidth
import app.uicomponents.controls.CheckGlyph
import app.uicomponents.controls.CloseGlyph
import app.uicomponents.controls.DashGlyph
import app.uicomponents.controls.Feedback
import app.uicomponents.controls.FontSizeRange
import app.uicomponents.controls.Icon
import app.uicomponents.controls.Tag
import app.uicomponents.controls.Text
import app.uicomponents.controls.Tone
import app.uicomponents.controls.VerticalRule
import app.uicomponents.controls.controlStates
import app.uicomponents.controls.pressFeedback
import app.utils.Platform
import app.utils.platform
import org.jetbrains.compose.resources.painterResource

/**
 * Every engine at once: one hairline frame, one cell per engine with its mark, name and badge,
 * the active cell filled with the accent and carrying the bottom edge. Under the frame a "?"
 * square sits below the selected cell; a tap morphs it into a full-width card with the
 * engine's story, so there is never a doubt which engine the words are about. [compact] is the
 * short-window size: smaller marks and tighter padding, twenty points less per cell.
 */
@Composable
fun HomeEnginePicker(
    engines: List<PlayerEngine>,
    selectedEngine: String,
    onSelectEngine: (PlayerEngine) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    if (engines.isEmpty()) return
    val p = palette
    val selectedIndex = engines.indexOfFirst { it.name == selectedEngine }

    Column(modifier) {
        /* The row takes the height of its tallest cell rather than a fixed one, so bigger
         * system text grows the cells instead of clipping the badge out of them. */
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clip(Radius.controlShape)
                .border(Space.hair, p.rule, Radius.controlShape)
                .selectableGroup(),
        ) {
            engines.forEachIndexed { i, engine ->
                if (i > 0) VerticalRule()
                EngineCell(
                    engine = engine,
                    active = i == selectedIndex,
                    compact = compact,
                    modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = if (compact) CompactCellHeight else CellHeight),
                    onClick = {
                        if (i == selectedIndex) return@EngineCell
                        Feedback.tick()
                        onSelectEngine(engine)
                    },
                )
            }
        }
        EngineInfoMorph(
            selectedIndex = selectedIndex.coerceAtLeast(0),
            count = engines.size,
            story = engines.getOrNull(selectedIndex)?.let { storyOf(it, strings) },
        )
    }
}

private val CellHeight = 118.dp
private val CompactCellHeight = 98.dp
private val MarkSize = 40.dp
private val CompactMarkSize = 32.dp
private val TipSize = 18.dp

/**
 * The "?" square under the selected cell, which glides to the start edge and widens into the
 * story card on a tap; a second tap folds it back. The card follows the selection while open.
 */
@Composable
private fun EngineInfoMorph(selectedIndex: Int, count: Int, story: EngineStory?) {
    val p = palette
    var open by remember { mutableStateOf(false) }
    val source = remember { MutableInteractionSource() }
    val name = strings.helpTip

    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = Space.gapTight)) {
        val cellWidth = maxWidth / count.coerceAtLeast(1)
        val underCell = cellWidth * selectedIndex + (cellWidth - TipSize) / 2
        val cardWidth = maxWidth
        val density = LocalDensity.current
        /* The card reports its own height, measured at its final width, so the height animates
         * in lockstep with the width toward one known target. animateContentSize could not do
         * this: it animates one size, and a width that moves every frame kept re-aiming it, so
         * the height crawled and then finished in a second visible curve. */
        var cardHeight by remember { mutableStateOf(0.dp) }
        val start by animateDpAsState(if (open) 0.dp else underCell, Motion.move(), label = "tipStart")
        val width by animateDpAsState(if (open) cardWidth else TipSize, Motion.move(), label = "tipWidth")
        val height by animateDpAsState(if (open && cardHeight > 0.dp) cardHeight else TipSize, Motion.move(), label = "tipHeight")
        val edge by animateColorAsState(if (open) p.accent else p.rule, Motion.quick(), label = "tipEdge")

        Box(
            modifier = Modifier
                .padding(start = start)
                .width(width)
                .height(height)
                .clip(Radius.tightShape)
                .border(Space.hair, edge, Radius.tightShape)
                .clickable(interactionSource = source, indication = null, role = Role.Button, enabled = story != null) { Feedback.tick(); open = !open }
                .hoverable(source)
                .semantics { contentDescription = name }
                .controlStates(source, Radius.tightShape)
                .pointerHoverIcon(PointerIcon.Hand)
                .pressFeedback(source),
        ) {
            if (open && story != null) {
                AnimatedContent(
                    targetState = story,
                    // No size transform of its own: the box above animates the size, alone.
                    transitionSpec = { ContentTransform(fadeIn(Motion.quick()), fadeOut(Motion.quick()), sizeTransform = null) },
                    label = "engineStory",
                ) { current ->
                    /* Laid out at the card's final width from the first frame, whatever width the
                     * box is animating through, so the height has one target. Measured against
                     * the growing width, the words re-wrapped every frame: the card overshot its
                     * final height, then shrank back. */
                    EngineCard(
                        current,
                        Modifier.laidOutAt(cardWidth).onSizeChanged { cardHeight = with(density) { it.height.toDp() } },
                    )
                }
            } else {
                Box(Modifier.size(TipSize), contentAlignment = Alignment.Center) {
                    Text("?", style = Type.group, color = p.inkDim)
                }
            }
        }
    }
}

/** What a badge says, and in what tone. */
private class EngineBadge(val label: String, val tone: Tone)

/** Whether an engine can do one thing: outright, not at all, or only when the device's chip can. */
private enum class Can { Yes, No, Device }

/** The card's content: what the engine is, then one line per ability with its verdict. */
private data class EngineStory(val description: String, val rows: List<Pair<String, Can>>)

/**
 * The facts per engine. The verdicts are what the bundled libraries can do, not what a given
 * file needs: mpv, KitePlayer and VLCKit carry the full FFmpeg with dav1d and libass, so they
 * share one list of formats. ExoPlayer and AVPlayer decode video on the device's own chips and
 * draw ASS as plain text. Picture in picture is the one ability that depends on the platform,
 * not the engine: KitePlayer has it on Android only.
 */
private fun storyOf(engine: PlayerEngine, s: AppStrings): EngineStory? {
    val yes = Can.Yes
    val no = Can.No
    val device = Can.Device
    val ffmpeg = listOf(
        s.engineCanChapters to yes,
        s.engineCanStyledSubs to yes,
        s.engineCanSubtitleFiles to yes,
        s.engineCanAllMedia to yes,
    )
    return when (engine.name.lowercase()) {
        "exoplayer" -> EngineStory(
            s.engineDescExoplayer,
            listOf(
                s.engineCanChapters to no,
                s.engineCanStyledSubs to no,
                s.engineCanSubtitleFilesBoth to yes,
                s.engineCanAllAudio to yes,
                s.engineCanVideoDevice to device,
                s.engineCanAv1Device to device,
                s.engineCanPip to yes,
            ),
        )
        "mpv" -> EngineStory(s.engineDescMpv, ffmpeg + (s.engineCanPip to yes) + (s.engineCanInterpolation to yes))
        "kiteplayer" -> EngineStory(
            s.engineDescKiteplayer,
            ffmpeg + (s.engineCanPip to if (platform == Platform.Android) yes else no) + (s.engineCanVisualizer to yes),
        )
        "avplayer" -> EngineStory(
            s.engineDescAvplayer,
            listOf(
                s.engineCanChapters to no,
                s.engineCanStyledSubs to no,
                s.engineCanSubtitleFiles to no,
                s.engineCanMkv to no,
                s.engineCanAv1Device to device,
                s.engineCanPip to yes,
            ),
        )
        "vlckit" -> EngineStory(s.engineDescVlckit, ffmpeg + (s.engineCanPip to yes))
        else -> null
    }
}

/** The description, then the ability lines, each led by its mark: green check, red cross, or a dim dash. */
@Composable
private fun EngineCard(story: EngineStory, modifier: Modifier = Modifier) {
    val p = palette
    val s = strings
    Column(modifier.padding(Space.gap), verticalArrangement = Arrangement.spacedBy(Space.gapTight)) {
        Text(story.description, style = Type.note, color = p.ink)
        story.rows.forEach { (label, can) ->
            val (glyph, tint, spoken) = when (can) {
                Can.Yes -> Triple(CheckGlyph, p.okText, s.yes)
                Can.No -> Triple(CloseGlyph, p.bad, s.no)
                Can.Device -> Triple(DashGlyph, p.inkDim, s.engineCanDevice)
            }
            Row(
                // One spoken line per ability, so a screen reader gets the verdict the mark shows.
                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $spoken" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(glyph, contentDescription = null, tint = tint, modifier = Modifier.size(Space.glyph))
                Spacer(Modifier.width(Space.gapTight))
                Text(label, style = Type.note, color = if (can == Can.Yes) p.ink else p.inkDim)
            }
        }
    }
}

/**
 * Measures the content at exactly [width], whatever the incoming constraints allow, and reports
 * a width that fits them, anchored at the start. The overflow is for the caller's clip.
 */
private fun Modifier.laidOutAt(width: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(Constraints.fixedWidth(width.roundToPx()))
    layout(constraints.constrainWidth(placeable.width), placeable.height) { placeable.placeRelative(0, 0) }
}

/**
 * One badge per engine, the most important thing to know first: missing beats experimental
 * beats default beats the platform's own player.
 */
private fun badgeOf(engine: PlayerEngine, s: AppStrings): EngineBadge? = when {
    !engine.isAvailable -> EngineBadge(s.connectEngineBadgeUnavailable, Tone.Bad)
    engine.isExperimental -> EngineBadge(s.connectEngineBadgeExperimental, Tone.Warn)
    engine.isDefault -> EngineBadge(s.connectEngineBadgeDefault, Tone.Accent)
    engine.isSystem -> EngineBadge(s.connectEngineBadgeSystem, Tone.Neutral)
    else -> null
}

@Composable
private fun EngineCell(engine: PlayerEngine, active: Boolean, compact: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = palette
    val source = remember { MutableInteractionSource() }
    val available = engine.isAvailable
    val badge = badgeOf(engine, strings)
    val fill by animateColorAsState(if (active) p.accent.copy(alpha = 0.16f) else p.accent.copy(alpha = 0f), Motion.quick(), label = "fill")
    val edge by animateColorAsState(if (active) p.accent else p.accent.copy(alpha = 0f), Motion.quick(), label = "edge")
    val spoken = engine.name + (badge?.let { ", " + it.label } ?: "")

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
            .padding(horizontal = 2.dp, vertical = if (compact) Space.gapTight else Space.gap),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(engine.img),
            contentDescription = null,
            modifier = Modifier.size(if (compact) CompactMarkSize else MarkSize),
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
            overflow = TextOverflow.Ellipsis,
            // A name wider than its cell steps down toward the group size before it is cut.
            autoSize = FontSizeRange(Type.label.fontSize),
        )
        if (badge != null) {
            Spacer(Modifier.height(Space.gapTight))
            Tag(badge.label, tone = badge.tone, filled = active && badge.tone != Tone.Neutral, autoSize = true)
        }
    }
}
