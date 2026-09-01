package app.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * DESIGN PROTOTYPE, not production code. Renders the proposed settings row taxonomy with the
 * same real content as the current design so the two can be measured side by side.
 *
 * The proposal in one line: the row shows the setting's NAME and its CURRENT VALUE; the
 * explanation moves behind disclosure instead of being printed in full on every row.
 */
class SettingsRedesignPrototype {

    private val outDir = File(
        System.getenv("SETTINGS_RENDER_OUT")
            ?: (System.getProperty("java.io.tmpdir") + "/settings-render")
    )

    /* ── Density tokens: semantic choices derived from width, not magic font sizes ────────── */
    private data class Density2(
        val showRowIcons: Boolean,
        val horizontalPadding: Int,
        val contentMaxWidth: Int,
    )

    private fun densityFor(widthDp: Int) = when {
        widthDp < 480 -> Density2(showRowIcons = false, horizontalPadding = 20, contentMaxWidth = 560)
        widthDp < 840 -> Density2(showRowIcons = true, horizontalPadding = 24, contentMaxWidth = 560)
        else -> Density2(showRowIcons = true, horizontalPadding = 24, contentMaxWidth = 640)
    }

    /* ── Row taxonomy ─────────────────────────────────────────────────────────────────────── */

    @Composable
    private fun RowFrame(d: Density2, icon: (@Composable () -> Unit)?, content: @Composable RowScope.() -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = d.horizontalPadding.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (d.showRowIcons && icon != null) {
                icon()
                Spacer(Modifier.width(16.dp))
            }
            content()
        }
    }

    @Composable
    private fun TitleAndValue(title: String, value: String?, modifier: Modifier = Modifier) {
        Column(modifier) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!value.isNullOrBlank()) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    @Composable
    private fun ToggleRow(d: Density2, title: String, state: String?, checked: Boolean) {
        RowFrame(d, icon = { Icon(Icons.Filled.Done, null, Modifier.size(22.dp), MaterialTheme.colorScheme.primary) }) {
            TitleAndValue(title, state, Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = {})
        }
    }

    @Composable
    private fun ValueRow(d: Density2, title: String, value: String) {
        RowFrame(d, icon = { Icon(Icons.Filled.Done, null, Modifier.size(22.dp), MaterialTheme.colorScheme.primary) }) {
            TitleAndValue(title, value, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                Modifier.size(20.dp), MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    @Composable
    private fun ColorRow(d: Density2, title: String, value: String, swatch: Color) {
        RowFrame(d, icon = { Icon(Icons.Filled.Done, null, Modifier.size(22.dp), MaterialTheme.colorScheme.primary) }) {
            TitleAndValue(title, value, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(24.dp).background(swatch, CircleShape))
        }
    }

    /** Slider keeps its control inline, but the value sits WITH the slider, not across the row. */
    @Composable
    private fun SliderRow(d: Density2, title: String, value: Int, min: Int, max: Int, unit: String) {
        Column(
            Modifier.fillMaxWidth()
                .padding(start = d.horizontalPadding.dp, end = d.horizontalPadding.dp, top = 10.dp, bottom = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (d.showRowIcons) {
                    Icon(Icons.Filled.Done, null, Modifier.size(22.dp), MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "$value$unit",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            val src = remember { MutableInteractionSource() }
            Slider(
                value = value.toFloat(),
                onValueChange = {},
                valueRange = min.toFloat()..max.toFloat(),
                interactionSource = src,
                thumb = { SliderDefaults.Thumb(interactionSource = src, thumbSize = DpSize(4.dp, 22.dp)) },
                modifier = Modifier.fillMaxWidth().padding(start = if (d.showRowIcons) 38.dp else 0.dp),
            )
        }
    }

    @Composable
    private fun GroupHeader(d: Density2, text: String) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = d.horizontalPadding.dp, end = d.horizontalPadding.dp, top = 20.dp, bottom = 4.dp)
        )
    }

    /* ── The same two categories, rebuilt in the proposed taxonomy ────────────────────────── */

    @Composable
    private fun ProposedNetwork(d: Density2) {
        Column(Modifier.fillMaxWidth().widthIn(max = d.contentMaxWidth.dp)) {
            ToggleRow(d, "Use Secure Connection (TLS)", "On, when the server supports it", true)
            ValueRow(d, "Network Engine", "Netty (recommended)")
            ToggleRow(d, "Resolve streaming URLs", "On - YouTube, SoundCloud, PeerTube", true)
            ValueRow(d, "Trusted Domains", "None - no peer URL auto-loads")
        }
    }

    @Composable
    private fun ProposedPlayer(d: Density2) {
        Column(Modifier.fillMaxWidth().widthIn(max = d.contentMaxWidth.dp)) {
            GroupHeader(d, "Seeking")
            SliderRow(d, "Forward jump", 10, 1, 120, "s")
            SliderRow(d, "Backward jump", 10, 1, 120, "s")
            ToggleRow(d, "Show custom skip button", "On the main player screen", true)
            SliderRow(d, "Custom skip duration", 90, 1, 600, "s")
            GroupHeader(d, "Subtitles")
            SliderRow(d, "Subtitle size", 16, 8, 60, "")
            ValueRow(d, "Preferred subtitle language", "English")
            ValueRow(d, "Preferred audio language", "English")
            GroupHeader(d, "Chapters")
            ToggleRow(d, "Show chapter dots", "On the seekbar", true)
            ToggleRow(d, "Chapter dots are clickable", "Tap a dot to jump", false)
            GroupHeader(d, "Appearance")
            ColorRow(d, "Video background colour", "Black", Color.Black)
        }
    }

    /* ── Render ───────────────────────────────────────────────────────────────────────────── */

    @Composable
    private fun Harness(content: @Composable () -> Unit) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) { content() }
        }
    }

    private fun render(name: String, widthDp: Int, heightDp: Int = 1600, body: @Composable (Density2) -> Unit) {
        val density = Density(2f)
        val scene = ImageComposeScene(
            width = (widthDp * density.density).toInt(),
            height = (heightDp * density.density).toInt(),
            density = density,
        ) { Harness { Box(Modifier.width(widthDp.dp)) { body(densityFor(widthDp)) } } }
        try {
            var img = scene.render(0L)
            repeat(20) { i -> img = scene.render((i + 1) * 16_000_000L) }
            outDir.mkdirs()
            val f = File(outDir, "$name-${widthDp}dp.png")
            img.encodeToData(EncodedImageFormat.PNG)?.bytes?.let { f.writeBytes(it) }
            println("PROTO $name @${widthDp}dp -> ${f.absolutePath}")
        } finally {
            scene.close()
        }
    }

    @Test
    fun renderProposedSettings() {
        render("proposed-network", 360) { ProposedNetwork(it) }
        render("proposed-player", 360) { ProposedPlayer(it) }
        render("proposed-network-tablet", 720) { ProposedNetwork(it) }
    }
}
