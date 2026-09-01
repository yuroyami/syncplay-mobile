package app.preferences

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * DESIGN PROTOTYPE, not production code.
 *
 * A settings design system for Synkplay that borrows nothing from Material 3: its own spatial
 * ladder, its own type scale, and every control drawn rather than adopted. The reference world
 * is the app's own: transport rows, scrub tracks, playheads, an inspector panel.
 */
class SettingsConsolePrototype {

    private val outDir = File(
        System.getenv("SETTINGS_RENDER_OUT")
            ?: (System.getProperty("java.io.tmpdir") + "/settings-render")
    )

    /* ═══ The system ═════════════════════════════════════════════════════════════════════════
     * Unit: 6dp. Not 8dp. The whole point of the redesign is density, and a 6dp unit gives a
     * finer ladder than Material's, so nothing lands on 48 / 56 / 64 by accident.
     */
    private object U {
        val base = 6
        val row = (base * 7).dp          // 42dp  standard row
        val rowTall = (base * 9).dp      // 54dp  row carrying a scrub track
        val gutter = (base * 3).dp       // 18dp  page gutter
        val valueCol = (base * 15).dp    // 90dp  the aligned value column
        val groupHead = (base * 5).dp    // 30dp
        val gap = (base * 2).dp          // 12dp
        val hair = 1.dp
    }

    /* Type: two sizes on a row, so a row reads as ONE line of information. Values are tabular
     * and tracked out, because they live in a column that must scan vertically. */
    private object T {
        val label = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.1).sp, lineHeight = 19.sp)
        val value = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp, lineHeight = 16.sp)
        val group = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp, lineHeight = 14.sp)
        val note = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 19.sp)
    }

    /* Palette: sourced from the app's own brand field, not from a Material scheme. */
    private object C {
        val ground = Color(0xFF121017)
        val ink = Color(0xFFEDE9F2)
        val inkDim = Color(0xFF9C93AC)
        val inkFaint = Color(0xFF6A6279)
        val rule = Color(0xFF262030)
        val accent = Color(0xFF9879EF)
        val accent2 = Color(0xFFC331D8)
        val accent3 = Color(0xFFD86B75)
        val trackOff = Color(0xFF2A2434)
        val brand = Brush.horizontalGradient(listOf(accent, accent2, accent3))
    }

    /* ═══ Controls, each one drawn ═══════════════════════════════════════════════════════════ */

    /** A hard edged rocker. Not a pill: pills are Material's, and the value column already
     *  spells the state, so the control only has to show which side it is sitting on. */
    @Composable
    private fun Rocker(on: Boolean) {
        Box(
            Modifier.size(width = 38.dp, height = 20.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (on) C.accent.copy(alpha = 0.28f) else Color.Transparent)
                .border(U.hair, if (on) C.accent else C.rule, RoundedCornerShape(3.dp))
                .padding(2.dp),
            contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier.size(width = 15.dp, height = 14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (on) C.accent else C.inkFaint)
            )
        }
    }

    /** A scrub track with a playhead, which is the app's own seekbar vocabulary. The fill uses
     *  the brand gradient, the handle is a thin vertical bar, and ticks mark real stops. */
    @Composable
    private fun ScrubTrack(fraction: Float, ticks: Int = 0) {
        Canvas(Modifier.fillMaxWidth().height(18.dp)) {
            val h = 4.dp.toPx()
            val y = size.height / 2 - h / 2
            val r = 1.dp.toPx()
            drawRoundRect(
                color = C.trackOff, topLeft = Offset(0f, y), size = Size(size.width, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
            )
            val fw = size.width * fraction.coerceIn(0f, 1f)
            if (fw > 0f) {
                drawRoundRect(
                    brush = C.brand, topLeft = Offset(0f, y), size = Size(fw, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
                )
            }
            if (ticks > 1) {
                for (i in 0..ticks) {
                    val x = size.width * (i / ticks.toFloat())
                    drawRect(
                        color = C.rule,
                        topLeft = Offset(x.coerceAtMost(size.width - 1.dp.toPx()), y + h + 3.dp.toPx()),
                        size = Size(1.dp.toPx(), 3.dp.toPx())
                    )
                }
            }
            // Playhead: a bar, not a knob.
            val px = (fw - 1.5.dp.toPx()).coerceIn(0f, size.width - 3.dp.toPx())
            drawRoundRect(
                color = C.ink, topLeft = Offset(px, y - 6.dp.toPx()),
                size = Size(3.dp.toPx(), h + 12.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
            )
        }
    }

    /** Short option sets step in place, so changing a choice never opens anything. */
    @Composable
    private fun Stepper(value: String) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(U.valueCol)) {
            Chevron(left = true)
            Text(
                text = value, style = T.value, color = C.accent, textAlign = TextAlign.Center,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            Chevron(left = false)
        }
    }

    @Composable
    private fun Chevron(left: Boolean, dim: Boolean = false) {
        Canvas(Modifier.size(14.dp)) {
            val s = 4.dp.toPx()
            val cx = size.width / 2; val cy = size.height / 2
            val w = 1.4.dp.toPx()
            val col = if (dim) C.inkFaint else C.inkDim
            val dir = if (left) -1 else 1
            drawLine(col, Offset(cx - dir * s / 2, cy - s), Offset(cx + dir * s / 2, cy), w)
            drawLine(col, Offset(cx + dir * s / 2, cy), Offset(cx - dir * s / 2, cy + s), w)
        }
    }

    /** A colour is a swatch, so it is a square with a hairline. Circles are chip language. */
    @Composable
    private fun Swatch(c: Color) {
        Box(
            Modifier.size(width = 22.dp, height = 16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(c)
                .border(U.hair, C.rule, RoundedCornerShape(2.dp))
        )
    }

    /* ═══ Rows ═══════════════════════════════════════════════════════════════════════════════ */

    @Composable
    private fun Label(text: String, modifier: Modifier = Modifier) =
        Text(text, style = T.label, color = C.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)

    @Composable
    private fun ValueText(text: String, accent: Boolean = false) =
        Text(
            text, style = T.value, color = if (accent) C.accent else C.inkDim,
            textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(U.valueCol)
        )

    @Composable
    private fun ToggleRow(title: String, on: Boolean) {
        Row(
            Modifier.fillMaxWidth().height(U.row).padding(horizontal = U.gutter),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Label(title, Modifier.weight(1f))
            Spacer(Modifier.width(U.gap))
            Text(
                if (on) "ON" else "OFF", style = T.value,
                color = if (on) C.accent else C.inkFaint, textAlign = TextAlign.End,
                modifier = Modifier.width((U.base * 6).dp)
            )
            Spacer(Modifier.width(U.gap))
            Rocker(on)
        }
    }

    @Composable
    private fun StepRow(title: String, value: String) {
        Row(
            Modifier.fillMaxWidth().height(U.row).padding(start = U.gutter, end = (U.base * 2).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Label(title, Modifier.weight(1f))
            Spacer(Modifier.width(U.gap))
            Stepper(value)
        }
    }

    @Composable
    private fun OpenRow(title: String, value: String) {
        Row(
            Modifier.fillMaxWidth().height(U.row).padding(start = U.gutter, end = (U.base * 2).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Label(title, Modifier.weight(1f))
            Spacer(Modifier.width(U.gap))
            ValueText(value)
            Spacer(Modifier.width((U.base).dp))
            Chevron(left = false)
        }
    }

    @Composable
    private fun ColorRow(title: String, value: String, c: Color) {
        Row(
            Modifier.fillMaxWidth().height(U.row).padding(horizontal = U.gutter),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Label(title, Modifier.weight(1f))
            Spacer(Modifier.width(U.gap))
            Text(value, style = T.value, color = C.inkDim, textAlign = TextAlign.End,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(U.valueCol))
            Spacer(Modifier.width(U.gap))
            Swatch(c)
        }
    }

    /** The scrub row: label and value on one line, the track underneath at full width. The
     *  value sits WITH the control it belongs to, not stranded across the row from it. */
    @Composable
    private fun ScrubRow(title: String, value: String, fraction: Float, ticks: Int = 0) {
        Column(Modifier.fillMaxWidth().height(U.rowTall).padding(horizontal = U.gutter)) {
            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Label(title, Modifier.weight(1f))
                Spacer(Modifier.width(U.gap))
                Text(value, style = T.value, color = C.accent, textAlign = TextAlign.End)
            }
            ScrubTrack(fraction, ticks)
        }
    }

    @Composable
    private fun ActionRow(title: String, destructive: Boolean = false) {
        Row(
            Modifier.fillMaxWidth().height(U.row).padding(start = U.gutter, end = (U.base * 2).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = T.label, color = if (destructive) C.accent3 else C.ink,
                maxLines = 1, modifier = Modifier.weight(1f))
            Chevron(left = false)
        }
    }

    @Composable
    private fun GroupHead(text: String) {
        Box(Modifier.fillMaxWidth().height(U.groupHead).padding(horizontal = U.gutter), Alignment.BottomStart) {
            Text(text.uppercase(), style = T.group, color = C.accent)
        }
    }

    @Composable
    private fun Rule() = Box(Modifier.fillMaxWidth().height(U.hair).background(C.rule))

    /* ═══ The same two categories, in the console system ══════════════════════════════════════ */

    @Composable
    private fun Network() {
        Column(Modifier.fillMaxWidth().widthIn(max = 560.dp)) {
            GroupHead("Connection")
            ToggleRow("Secure connection (TLS)", true)
            StepRow("Network engine", "Netty")
            Rule()
            GroupHead("Links")
            ToggleRow("Resolve streaming URLs", true)
            OpenRow("Trusted domains", "None")
        }
    }

    @Composable
    private fun Player() {
        Column(Modifier.fillMaxWidth().widthIn(max = 560.dp)) {
            GroupHead("Seeking")
            ScrubRow("Forward jump", "10s", 0.08f, 0)
            ScrubRow("Backward jump", "10s", 0.08f, 0)
            ToggleRow("Custom skip button", true)
            ScrubRow("Custom skip duration", "90s", 0.15f, 0)
            Rule()
            GroupHead("Subtitles")
            ScrubRow("Subtitle size", "16", 0.15f, 0)
            StepRow("Subtitle language", "English")
            StepRow("Audio language", "English")
            Rule()
            GroupHead("Chapters")
            ToggleRow("Show chapter dots", true)
            ToggleRow("Chapter dots clickable", false)
            Rule()
            GroupHead("Picture")
            ColorRow("Video background", "#000000", Color.Black)
        }
    }

    /* ═══ Render ══════════════════════════════════════════════════════════════════════════════ */

    private fun render(name: String, widthDp: Int, heightDp: Int, body: @Composable () -> Unit) {
        val d = Density(2f)
        val scene = ImageComposeScene(
            width = (widthDp * d.density).toInt(),
            height = (heightDp * d.density).toInt(),
            density = d,
        ) { Box(Modifier.fillMaxSize().background(C.ground)) { Box(Modifier.width(widthDp.dp)) { body() } } }
        try {
            var img = scene.render(0L)
            repeat(12) { i -> img = scene.render((i + 1) * 16_000_000L) }
            outDir.mkdirs()
            val f = File(outDir, "$name-${widthDp}dp.png")
            img.encodeToData(EncodedImageFormat.PNG)?.bytes?.let { f.writeBytes(it) }
            println("CONSOLE $name @${widthDp}dp -> ${f.absolutePath}")
        } finally {
            scene.close()
        }
    }

    @Test
    fun renderConsoleSettings() {
        render("console-network", 360, 500) { Network() }
        render("console-player", 360, 900) { Player() }
    }
}
