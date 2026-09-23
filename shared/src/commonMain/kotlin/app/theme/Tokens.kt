package app.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

/*
 * The design tokens: the sizes, radii, durations, text styles and colors that surfaces read. App
 * code should take these values from here. A lint test in desktopTest (DesignLint) enforces this
 * for text sizes only.
 */

/** Spacing and sizes, mostly in steps of 6dp. A row is 42dp on purpose, so lists stay dense. */
object Space {
    val u = 6.dp
    val rowCompact = 36.dp
    val row = 42.dp
    val rowTall = 54.dp
    val bar = 54.dp
    val hero = 60.dp
    val gutter = 18.dp
    val gap = 12.dp
    val gapTight = 6.dp
    val valueCol = 90.dp
    val valueMax = 160.dp
    val groupHead = 30.dp
    val glyph = 20.dp
    val glyphLarge = 24.dp
    val hair = 1.dp
    val touchMin = 48.dp
    /** The maximum width of a notice and of the chat over the video, so lines stay short. */
    val noticeWidth = 420.dp
}

/** Corner radii. They are small, so shapes stay nearly square and are never pill-shaped. */
object Radius {
    val none = 0.dp
    val tight = 2.dp
    val control = 3.dp
    val panel = 8.dp

    val tightShape = RoundedCornerShape(tight)
    val controlShape = RoundedCornerShape(control)
    val panelShape = RoundedCornerShape(panel)
}

/** Animation timing: two durations, one easing curve and one spring. */
object Motion {
    const val quickMs = 120
    const val moveMs = 220

    val easing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /**
     * True when the platform setting or the Reduce motion switch is on. AdamScreen, the app root,
     * sets it. [quick] and [move] then take 0 ms.
     */
    @kotlin.concurrent.Volatile
    var reduced: Boolean = false

    fun <T> quick(): TweenSpec<T> = tween(if (reduced) 0 else quickMs, easing = easing)
    fun <T> move(): TweenSpec<T> = tween(if (reduced) 0 else moveMs, easing = easing)
    fun <T> drag(): SpringSpec<T> = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)
}

/** The five text roles for one font family, built once per family and provided by [LocalType]. */
@Immutable
class TypeRoles(
    val display: TextStyle,
    val label: TextStyle,
    val value: TextStyle,
    val group: TextStyle,
    val note: TextStyle,
) {
    companion object {
        fun from(family: FontFamily): TypeRoles {
            fun role(size: Int, weight: FontWeight, tracking: Float, line: Int, features: String? = null) = TextStyle(
                fontFamily = family,
                fontSize = size.sp,
                fontWeight = weight,
                letterSpacing = tracking.sp,
                lineHeight = line.sp,
                fontFeatureSettings = features,
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
            )
            return TypeRoles(
                display = role(24, FontWeight.Bold, -0.5f, 28),
                label = role(15, FontWeight.Medium, -0.1f, 19),
                value = role(13, FontWeight.Medium, 0.3f, 16, features = "tnum"),
                group = role(11, FontWeight.SemiBold, 1.5f, 14),
                note = role(13, FontWeight.Normal, 0f, 19),
            )
        }
    }
}

/**
 * The limits for auto-sizing a label that does not fit its width.
 *
 * A control rarely chooses its own width. For example, an engine badge (an engine is one of the
 * video players that the app can drive) gets a third of the engine picker, and a segment gets its
 * share of the row. English words are short there, but other languages need more room, so the
 * size range must be wide. A label shrinks down to [floor] before it is cut off, and it never
 * gets smaller than [floor].
 */
object AutoSize {
    val floor = 5.sp
    /** Auto-sizing moves in 0.5sp steps, so fitted sizes stay on a regular scale. */
    val step = 0.5.sp
}

/** Defaults to the system font family, so a composable drawn outside AdamScreen still lays out. */
val LocalType = staticCompositionLocalOf { TypeRoles.from(FontFamily.Default) }

/** Reads a text role: `Type.label`. GroupHeading uppercases its text, not the [group] style. */
object Type {
    val display: TextStyle @Composable @ReadOnlyComposable get() = LocalType.current.display
    val label: TextStyle @Composable @ReadOnlyComposable get() = LocalType.current.label
    val value: TextStyle @Composable @ReadOnlyComposable get() = LocalType.current.value
    val group: TextStyle @Composable @ReadOnlyComposable get() = LocalType.current.group
    val note: TextStyle @Composable @ReadOnlyComposable get() = LocalType.current.note
}

/**
 * The semantic palette: the colors that surfaces read, never the Material scheme roles. Over the
 * video, the room screen (where the group watches together) provides [overVideo] instead. That
 * palette fixes the grounds dark and the inks white, and keeps only the theme's accent, gradient
 * and status colors.
 */
@Immutable
data class Palette(
    val ground: Color,
    val panel: Color,
    val ink: Color,
    val inkDim: Color,
    val inkFaint: Color,
    val rule: Color,
    val trackOff: Color,
    val accent: Color,
    val brandField: List<Color>,
    val ok: Color,
    /**
     * The readiness green for text. [ok] is the same green for a filled shape.
     *
     * [ok] is fixed on purpose and fills a readiness square on its own ground. Text sits on the
     * theme's own surface. On the light theme, that surface is so pale that the bright [ok] green
     * has a contrast of roughly 1:1 against it. This color stays readable there.
     */
    val okText: Color,
    val warn: Color,
    val bad: Color,
    val disabled: Color,
    val isDark: Boolean,
) {
    /**
     * The ink color for text on a filled control, computed from [fill] instead of stored.
     *
     * A stored color goes stale when a palette repurposes [accent]. The add-media button does
     * that: it paints itself with the brand gradient and turns [accent] into the dark ink that
     * reads on the gradient. A label that used a stored token would then match its own button.
     * The 0.19 cut is where white and near-black give the same contrast against the fill.
     */
    fun inkOn(fill: Color): Color = if (fill.luminance() > 0.19f) VideoGround else Color.White

    fun overVideo(): Palette = copy(
        ground = VideoGround,
        panel = VideoPanel,
        ink = Color.White,
        inkDim = Color.White.copy(alpha = 0.62f),
        inkFaint = Color.White.copy(alpha = 0.42f),
        rule = Color.White.copy(alpha = 0.10f),
        trackOff = Color.White.copy(alpha = 0.12f),
        disabled = Color.White.copy(alpha = 0.38f),
        isDark = true,
    )

    companion object {
        /** The readiness green, fixed like [Bad] so no theme can make "not ready" look ready. */
        val Ok = Color(0xFF6ECB5A)

        /** A dark green for text on a light ground: about 4.6:1 against the Daylight surface. */
        val OkOnLight = Color(0xFF14532D)
        val Bad = Color(0xFFE85455)
        val VideoGround = Color(0xFF0E0E12)
        val VideoPanel = Color(0xFF1B1B21)

        fun from(scheme: ColorScheme, theme: SaveableTheme): Palette {
            val seeds = listOf(
                Color(theme.primaryColor),
                theme.secondaryColor?.let(::Color) ?: Theming.NeoSP2,
                theme.tertiaryColor?.let(::Color) ?: Theming.NeoSP3,
            )
            return Palette(
                ground = scheme.background,
                panel = scheme.surfaceContainerHigh,
                ink = scheme.onSurface,
                inkDim = scheme.onSurface.copy(alpha = 0.62f),
                inkFaint = scheme.onSurface.copy(alpha = 0.42f),
                rule = scheme.outlineVariant,
                trackOff = scheme.onSurface.copy(alpha = 0.12f),
                accent = seeds[0],
                brandField = seeds,
                ok = Ok,
                okText = if (theme.isDark) Ok else OkOnLight,
                warn = seeds[2],
                bad = Bad,
                disabled = scheme.onSurface.copy(alpha = 0.38f),
                isDark = theme.isDark,
            )
        }
    }
}

/** Defaults to the brand theme ([TRINITY]), so previews need no provider. */
val LocalPalette = staticCompositionLocalOf { Palette.from(TRINITY.dynamicScheme, TRINITY) }

/**
 * The palette of the surface that a screen draws on. A dialog switches back to this palette.
 *
 * [LocalPalette] is not always the screen's palette: a control that fills itself with the brand
 * gradient provides a palette whose ink reads on that gradient. A dialog opened from inside such a
 * control is its own surface. Without this palette, the dialog would draw that control's dark ink
 * on its own dark panel. AdamScreen and the room screen provide it next to [LocalPalette].
 */
val LocalSurfacePalette = staticCompositionLocalOf { Palette.from(TRINITY.dynamicScheme, TRINITY) }

val palette: Palette @Composable @ReadOnlyComposable get() = LocalPalette.current

/** Which kind of surface something is. The treatment per tier is in GlassSurface.kt. */
enum class Tier { Flat, Panel, Chrome, Scrim }
