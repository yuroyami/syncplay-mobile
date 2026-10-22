package app.preferences.settings

import app.uicomponents.LocalIsTelevision
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import app.i18n.strings
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.controls.RowGap
import app.uicomponents.controls.SecondaryAction
import app.uicomponents.controls.Swatch
import app.uicomponents.controls.Text
import app.uicomponents.controls.hex
import com.kborowy.colorpicker.KolorPicker
import com.kborowy.colorpicker.config.PickerConfig
import com.kborowy.colorpicker.config.TrackConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

private data class ColorEdit(val color: Color, val save: Boolean = false)

/**
 * A colour editor for a limited height. It reserves the footer first, then fits the picker's
 * width and height into the space that is left.
 */
@Composable
internal fun InlineColorPage(
    summary: String,
    initial: Color,
    onColor: (Color) -> Unit,
    onReset: () -> Unit,
    resetColor: Color,
) {
    var edit by remember { mutableStateOf(ColorEdit(initial)) }
    var generation by remember { mutableIntStateOf(0) }
    val saveColor by rememberUpdatedState(onColor)
    LaunchedEffect(Unit) {
        snapshotFlow { edit }.collectLatest { next ->
            if (next.save) {
                delay(50)
                saveColor(next.color)
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            // A Back press can arrive inside the debounce window. The host's preference writer
            // outlives this page, so the last pick still reaches storage when the user leaves.
            if (edit.save) saveColor(edit.color)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(Space.gap)) {
        // The title already names the colour. In a short room panel, the explanation gives way to
        // the picker, so the controls are not squeezed and no second scroll area appears.
        val showSummary = maxHeight >= 300.dp * LocalDensity.current.fontScale
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Space.gapTight)) {
            if (showSummary && summary.isNotBlank()) {
                Text(summary, style = Type.note, color = palette.inkDim)
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                // KolorPicker's width weights are 8 : .25 : 1 : .25 : 1. This ratio makes its main
                // field a square, with the hue and alpha tracks beside it.
                val ratio = 10.5f / 8f
                val height = minOf(maxHeight, maxWidth / ratio, 260.dp)
                if (height >= 8.dp) {
                    // The library's default 4dp track padding does not fit in very short panels.
                    // Scale its handles and padding with the picker as well.
                    val scale = (height / 120.dp).coerceAtMost(1f)
                    val track = TrackConfig.Default.let {
                        it.copy(
                            trackPadding = it.trackPadding * scale,
                            trackBorderRadius = it.trackBorderRadius * scale,
                            thumbSize = DpSize(it.thumbSize.width * scale, it.thumbSize.height * scale),
                            thumbCornerRadius = it.thumbCornerRadius * scale,
                            thumbBorderSize = it.thumbBorderSize * scale,
                        )
                    }
                    val picker = PickerConfig.Default.let {
                        it.copy(thumbSize = it.thumbSize * scale, thumbRadius = it.thumbRadius * scale,
                            thumbBorderSize = it.thumbBorderSize * scale, pickerRadius = it.pickerRadius * scale)
                    }
                    key(generation, height) {
                        val start = remember { edit.color }
                        KolorPicker(
                            initialColor = start.copy(alpha = edit.color.alpha),
                            onColorSelected = { color ->
                                // The library emits its initial colour after its first layout.
                                // Opening or resetting must not store that colour as a pick.
                                if (color.toArgb() != edit.color.toArgb()) edit = ColorEdit(color, save = true)
                            },
                            pickerConfig = picker,
                            alphaTrackConfig = track,
                            hueTrackConfig = track,
                            modifier = Modifier.size(height * ratio, height).testTag("inline-color-picker"),
                        )
                    }
                }
            }
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(Space.gapTight),
            ) {
                Box(Modifier.align(Alignment.CenterVertically)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Swatch(edit.color)
                        RowGap(Space.gapTight)
                        Text(edit.color.hex(), style = Type.value, color = palette.inkDim)
                    }
                }
                SecondaryAction(strings.resetDefault, onClick = {
                    edit = ColorEdit(resetColor)
                    generation++
                    onReset()
                })
            }
            // The picker reacts only to a finger or a mouse. A TV remote uses these sliders.
            if (LocalIsTelevision.current) {
                ColorSliders(edit.color, onColor = { edit = ColorEdit(it, save = true); generation++ })
            }
        }
    }
}
