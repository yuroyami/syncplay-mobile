package app.preferences.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.i18n.strings
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.controls.ScrubTrack
import app.uicomponents.controls.Text
import kotlin.math.roundToInt

/**
 * Hue, saturation, brightness and opacity as sliders a remote can move with Left and Right. The
 * picker above them only answers a finger or a mouse, so on a television these are how a colour
 * is chosen. The hue is kept apart from [color], because a grey has none to read back.
 */
@Composable
internal fun ColorSliders(color: Color, onColor: (Color) -> Unit, modifier: Modifier = Modifier) {
    val hsv = color.toHsv()
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    if (hsv[1] > 0f && hsv[2] > 0f && hsv[0] != hue) hue = hsv[0]

    fun emit(h: Float = hue, s: Float = hsv[1], v: Float = hsv[2], a: Float = color.alpha) {
        onColor(Color.hsv(h.coerceIn(0f, 360f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f), a.coerceIn(0f, 1f)))
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.gapTight)) {
        Slider(strings.colorHue, hue / 360f, "${hue.roundToInt()}°", step = 1f / 72f) { hue = it * 360f; emit(h = hue) }
        Slider(strings.colorSaturation, hsv[1], percent(hsv[1])) { emit(s = it) }
        Slider(strings.colorBrightness, hsv[2], percent(hsv[2])) { emit(v = it) }
        Slider(strings.colorOpacity, color.alpha, percent(color.alpha)) { emit(a = it) }
    }
}

@Composable
private fun Slider(label: String, value: Float, spoken: String, step: Float = 0.05f, onValue: (Float) -> Unit) {
    Column {
        Text("$label  $spoken", style = Type.value, color = palette.inkDim)
        ScrubTrack(value = value, onValueChange = onValue, keyStep = step, describe = { spoken }, name = label)
    }
}

private fun percent(value: Float) = "${(value * 100).roundToInt()}%"

/** Hue in degrees, then saturation and value from 0 to 1. */
internal fun Color.toHsv(): FloatArray {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val delta = max - min
    val hue = when {
        delta == 0f -> 0f
        max == red -> 60f * (((green - blue) / delta) % 6f)
        max == green -> 60f * (((blue - red) / delta) + 2f)
        else -> 60f * (((red - green) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    val saturation = if (max == 0f) 0f else delta / max
    return floatArrayOf(hue, saturation, max)
}
