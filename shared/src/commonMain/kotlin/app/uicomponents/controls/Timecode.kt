package app.uicomponents.controls

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.theme.Type
import app.theme.palette
import app.utils.timestampFromMillis

/** `mm:ss` under an hour, else `hh:mm:ss`; `--:--` when the time is unknown. */
fun formatTimecode(ms: Long?): String = if (ms == null || ms < 0L) "--:--" else timestampFromMillis(ms)

/**
 * The app's one timecode: `value` type with tabular digits, so the digits never jitter. [dim]
 * dims it, for a total.
 */
@Composable
fun Timecode(ms: Long?, modifier: Modifier = Modifier, dim: Boolean = false, color: Color? = null) {
    val p = palette
    Text(
        text = formatTimecode(ms),
        style = Type.value,
        color = color ?: if (dim) p.inkDim else p.ink,
        maxLines = 1,
        modifier = modifier,
    )
}
