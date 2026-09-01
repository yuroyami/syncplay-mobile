package app.theme

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush

/**
 * A miniature of the app in a theme: the ground, a panel edge, two chat lines and a transport
 * bar with the scrub fill in the theme's gradient. Static, no glass, no state; the scheme is
 * resolved once per theme value. Draw it at 72 x 40dp in a list or at a pane's full size.
 */
@Composable
fun ThemeMiniature(theme: SaveableTheme, modifier: Modifier = Modifier) {
    val pal = remember(theme) { Palette.from(theme.dynamicScheme, theme) }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val u = h / 40f
        val r = CornerRadius(u)

        drawRect(pal.ground)

        // The side panel with its rule edge.
        val panelX = w * 0.68f
        drawRect(pal.panel, Offset(panelX, 0f), Size(w - panelX, h))
        drawRect(pal.rule, Offset(panelX, 0f), Size(maxOf(1f, u * 0.5f), h))
        drawRoundRect(pal.inkDim, Offset(panelX + 4 * u, 5 * u), Size((w - panelX) * 0.6f, 2.5f * u), r)
        drawRoundRect(pal.inkFaint, Offset(panelX + 4 * u, 11 * u), Size((w - panelX) * 0.45f, 2 * u), r)

        // Two chat lines: name in the accent, text in ink.
        drawRoundRect(pal.accent, Offset(4 * u, 6 * u), Size(w * 0.16f, 2.5f * u), r)
        drawRoundRect(pal.ink, Offset(4 * u, 11 * u), Size(w * 0.42f, 2.5f * u), r)
        drawRoundRect(pal.accent, Offset(4 * u, 17 * u), Size(w * 0.12f, 2.5f * u), r)
        drawRoundRect(pal.ink, Offset(4 * u, 22 * u), Size(w * 0.30f, 2.5f * u), r)

        // The transport: track, gradient fill, playhead, and the play key beside it.
        val trackY = h - 8 * u
        val trackX = 12 * u
        val trackW = panelX - trackX - 4 * u
        drawRoundRect(pal.ink, Offset(4 * u, trackY - 2 * u), Size(5 * u, 5 * u), r)
        drawRoundRect(pal.trackOff, Offset(trackX, trackY), Size(trackW, 1.5f * u), r)
        drawRoundRect(Brush.horizontalGradient(pal.brandField, trackX, trackX + trackW), Offset(trackX, trackY), Size(trackW * 0.42f, 1.5f * u), r)
        drawRoundRect(pal.ink, Offset(trackX + trackW * 0.42f - u * 0.5f, trackY - 2.5f * u), Size(u, 6.5f * u), r)
    }
}
