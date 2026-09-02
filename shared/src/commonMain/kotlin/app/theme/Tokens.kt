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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

/*
 * The design tokens every surface reads. Values and reasons live in DESIGN/FOUNDATION; this file
 * only states them. Nothing in app code chooses a size, a radius, a duration or a colour outside
 * of these objects, and the lint test in desktopTest enforces that for text sizes.
 */

/** The 6dp ladder. A row is 42dp on purpose: density is the whole point. */
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
}

/** Near square. Nothing is a capsule. */
object Radius {
    val none = 0.dp
    val tight = 2.dp
    val control = 3.dp
    val panel = 8.dp

    val tightShape = RoundedCornerShape(tight)
    val controlShape = RoundedCornerShape(control)
    val panelShape = RoundedCornerShape(panel)
}

/** Two durations, one easing, one spring. Holds are named once, here. */
object Motion {
    const val quickMs = 120
    const val moveMs = 220
    const val hudIdleMs = 3200L

    val easing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Set at the root from the platform setting or the Reduce motion switch; tweens collapse to instant. */
    @kotlin.concurrent.Volatile
    var reduced: Boolean = false

    fun <T> quick(): TweenSpec<T> = tween(if (reduced) 0 else quickMs, easing = easing)
    fun <T> move(): TweenSpec<T> = tween(if (reduced) 0 else moveMs, easing = easing)
    fun <T> drag(): SpringSpec<T> = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)
}

/** The five roles, bound to one family. Built once per family and provided through [LocalType]. */
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

/** Defaults to the system family so a composable rendered outside AdamScreen still lays out. */
val LocalType = staticCompositionLocalOf { TypeRoles.from(FontFamily.Default) }

/** Read a role: `Type.label`. Group headings are drawn uppercase by their composable, not here. */
object Type {
    val display: TextStyle @Composable @ReadOnlyComposable get() = LocalType.current.display
    val label: TextStyle @Composable @ReadOnlyComposable get() = LocalType.current.label
    val value: TextStyle @Composable @ReadOnlyComposable get() = LocalType.current.value
    val group: TextStyle @Composable @ReadOnlyComposable get() = LocalType.current.group
    val note: TextStyle @Composable @ReadOnlyComposable get() = LocalType.current.note
}

/**
 * The semantic palette. Surfaces read this, never Material role names. Over video the room
 * re-provides [overVideo], which pins the grounds and inks dark and keeps only the theme's
 * accent, gradient and status colours.
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
    val warn: Color,
    val bad: Color,
    val disabled: Color,
    val isDark: Boolean,
) {
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
        /** Readiness green and error red are fixed so no theme can make "not ready" look ready. */
        val Ok = Color(0xFF6ECB5A)
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
                warn = seeds[2],
                bad = Bad,
                disabled = scheme.onSurface.copy(alpha = 0.38f),
                isDark = theme.isDark,
            )
        }
    }
}

/** Defaults to the brand theme so previews and the render harness need no provider. */
val LocalPalette = staticCompositionLocalOf { Palette.from(TRINITY.dynamicScheme, TRINITY) }

val palette: Palette @Composable @ReadOnlyComposable get() = LocalPalette.current

/** Which kind of surface something is. The treatment per tier is in GlassSurface.kt. */
enum class Tier { Flat, Panel, Chrome, Scrim }
