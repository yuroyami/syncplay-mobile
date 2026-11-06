package app.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The brand gradient system:
 * - buildBrandRamp turns a theme's seeds (the three colours a theme is made from) into the brand
 *   ramp, which Palette.brandField holds.
 * - brandBrush draws that ramp in a shape. Every brand shape uses it, so the ramp has one scale.
 * - easedShade gives the stops of the shades that darken video under the room controls.
 */

/** The WCAG contrast ratio of two opaque colours, from 1 to 21. */
fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
}

/** A colour in OkLCh: lightness from 0 to 1, chroma, and hue in degrees. */
internal data class Lch(val l: Float, val c: Float, val h: Float)

internal fun Color.toLch(): Lch {
    val lab = convert(ColorSpaces.Oklab)
    var h = atan2(lab.blue, lab.green) * 180f / PI.toFloat()
    if (h < 0f) h += 360f
    return Lch(lab.red, sqrt(lab.green * lab.green + lab.blue * lab.blue), h)
}

private fun Lch.oklab(): Color {
    val r = h * PI.toFloat() / 180f
    return Color(l.coerceIn(0f, 1f), (c * cos(r)).coerceIn(-0.5f, 0.5f), (c * sin(r)).coerceIn(-0.5f, 0.5f), 1f, ColorSpaces.Oklab)
}

private fun Color.fitsSrgb(): Boolean {
    val lin = convert(ColorSpaces.LinearExtendedSrgb)
    val e = 0.0005f
    return lin.red in -e..1f + e && lin.green in -e..1f + e && lin.blue in -e..1f + e
}

/** OkLCh to sRGB. Lightness and hue stay, and chroma gives way until the colour fits in sRGB. */
internal fun Lch.toColor(): Color {
    if (oklab().fitsSrgb()) return oklab().convert(ColorSpaces.Srgb)
    var lo = 0f
    var hi = c
    repeat(GAMUT_STEPS) {
        val mid = (lo + hi) / 2f
        if (copy(c = mid).oklab().fitsSrgb()) lo = mid else hi = mid
    }
    return copy(c = lo).oklab().convert(ColorSpaces.Srgb)
}

/** The colour moved in lightness only as far as it must go to read at [target] against [ink]. */
private fun Lch.fitted(ink: Color, target: Float): Lch {
    if (contrastRatio(toColor(), ink) >= target) return this
    // White passes against dark ink and black against light ink, so the search always ends.
    var lo = l
    var hi = if (ink.luminance() < 0.5f) 1f else 0f
    repeat(GAMUT_STEPS) {
        val mid = (lo + hi) / 2f
        if (contrastRatio(copy(l = mid).toColor(), ink) >= target) hi = mid else lo = mid
    }
    return copy(l = hi)
}

private fun hueDelta(from: Float, to: Float): Float {
    var d = (to - from) % 360f
    if (d > 180f) d -= 360f
    if (d < -180f) d += 360f
    return d
}

/** [color] moved in lightness only as far as it must go to read at 4.5:1 on [ground]. */
fun readableOn(color: Color, ground: Color): Color =
    if (contrastRatio(color, ground) >= TEXT_CONTRAST) color else color.toLch().fitted(ground, TEXT_CONTRAST).toColor()

/**
 * The brand ramp of a theme: [RAMP_STOPS] colours built from its [seeds].
 *
 * - The ramp starts at the first seed that has colour. Grey seeds, and seeds more than 90 degrees
 *   of hue away from that one, stay out.
 * - With one usable seed, the ramp is a short walk in hue and lightness from it.
 * - Each colour moves in lightness until the ink on it reads at 4.6:1. The ink is white when
 *   [lightInk] is true, and [Palette.VideoGround] when it is false.
 * - The colours between seeds walk in OkLCh, the short way round the hue circle, so the middle
 *   of the ramp never turns brown or grey. Brushes blend in sRGB, which is why the stops are close.
 */
fun buildBrandRamp(seeds: List<Color>, lightInk: Boolean): List<Color> {
    val ink = if (lightInk) Color.White else Palette.VideoGround
    val all = seeds.map { it.toLch() }
    val first = all.indexOfFirst { it.c >= GREY_CHROMA }.coerceAtLeast(0)
    val anchor = all[first]
    var keys = listOf(anchor)
    if (anchor.c >= GREY_CHROMA) {
        keys = keys + all.filterIndexed { i, seed -> i > first && seed.c >= GREY_CHROMA && abs(hueDelta(anchor.h, seed.h)) <= MAX_HUE_SPREAD }
    }
    if (keys.size == 1) {
        keys = listOf(anchor.copy(l = anchor.l + 0.04f, h = anchor.h + 10f), anchor, anchor.copy(l = anchor.l - 0.04f, c = anchor.c * 1.1f, h = anchor.h - 22f))
    }
    // The seeds are fitted first, so the walk between them keeps their fitted lightness.
    keys = keys.map { it.fitted(ink, RAMP_CONTRAST) }
    return List(RAMP_STOPS) { i ->
        val x = i / (RAMP_STOPS - 1f) * (keys.size - 1)
        val k = min(x.toInt(), keys.size - 2)
        val f = x - k
        val a = keys[k]
        val b = keys[k + 1]
        Lch(a.l + (b.l - a.l) * f, a.c + (b.c - a.c) * f, a.h + hueDelta(a.h, b.h) * f).fitted(ink, RAMP_CONTRAST).toColor()
    }
}

/**
 * The narrowest span of the brand ramp. A shape narrower than this shows the middle of a ramp this
 * wide, so a small key reads as one colour and a full-width key shows the whole ramp.
 */
val BrandSpan = 320.dp

/**
 * The brand ramp across a shape [width] pixels wide, from start to end. The ramp spans [minSpan]
 * pixels or the whole width, whichever is wider, centred on the shape.
 */
fun brandBrush(stops: List<Color>, width: Float, minSpan: Float): Brush {
    val span = max(width, minSpan)
    val start = (width - span) / 2f
    return Brush.horizontalGradient(stops, startX = start, endX = start + span)
}

/**
 * The stops of a shade that fades from [maxAlpha] of [color] to clear on a smoothstep curve.
 * [darkAtStart] puts the dark end at the start of the gradient. A straight alpha ramp leaves a
 * visible line where it ends on a bright frame, and this curve does not.
 */
fun easedShade(maxAlpha: Float, darkAtStart: Boolean, color: Color = Color.Black): Array<Pair<Float, Color>> =
    Array(SHADE_STOPS + 1) { i ->
        val x = i / SHADE_STOPS.toFloat()
        val e = if (darkAtStart) 1f - x else x
        x to color.copy(alpha = maxAlpha * e * e * (3f - 2f * e))
    }

/** How many colours the brand ramp has. */
const val RAMP_STOPS = 13

/** The contrast that the ink on the brand ramp keeps. It is just above the 4.5:1 text floor. */
private const val RAMP_CONTRAST = 4.6f

/** The WCAG floor for text at the app's sizes. */
private const val TEXT_CONTRAST = 4.5f

/** Seeds with less chroma than this are grey, and stay out of the ramp. */
private const val GREY_CHROMA = 0.035f

/** Seeds further than this from the first seed with colour, in degrees of hue, stay out of the ramp. */
private const val MAX_HUE_SPREAD = 90f

private const val GAMUT_STEPS = 20
private const val SHADE_STOPS = 12
