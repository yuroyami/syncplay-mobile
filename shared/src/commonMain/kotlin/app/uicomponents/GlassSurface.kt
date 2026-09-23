package app.uicomponents

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import app.preferences.Preferences
import app.preferences.value
import app.preferences.watchPref
import app.theme.Radius
import app.theme.Tier
import app.theme.palette
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * The backdrop that glass surfaces sample. [GlassBackdrop] provides it at the root, and the room
 * provides its own for the video layer. It is null only for a surface drawn outside both, which
 * then gets a plain tonal panel.
 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/** Counts the glass surfaces on screen, so the backdrop captures only while one needs it. */
@Stable
class GlassDemand {
    var count: Int by mutableIntStateOf(0)
        private set

    fun acquire() { count++ }
    fun release() { count-- }
}

val LocalGlassDemand = staticCompositionLocalOf { GlassDemand() }

/**
 * True while the glass surfaces below are composed but invisible (the room's HUD, its overlay
 * controls, while hidden). They release their demand, so no backdrop is captured for panels that
 * nobody can see.
 */
val LocalGlassSuspended = staticCompositionLocalOf { false }

/** Whether frosted glass is on. When off, nothing is captured or blurred and panels are solid. */
@Composable
fun glassEnabled(): Boolean = !Preferences.DISABLE_FROSTED_GLASS.watchPref().value

/**
 * The same switch read outside composition, combined with [videoSurfaceSupportsGlass]. The Android
 * player views use it to choose a surface type.
 */
fun glassEnabledNow(): Boolean = !Preferences.DISABLE_FROSTED_GLASS.value() && videoSurfaceSupportsGlass()

/**
 * Whether this device can afford to draw video into the view hierarchy, where glass can sample it.
 * Android answers by OS version and by whether the device is low on RAM. Only the Android players
 * read the answer, so on other platforms it changes nothing.
 */
expect fun videoSurfaceSupportsGlass(): Boolean

/**
 * Marks [content] as the backdrop that glass surfaces blur, and provides it as [LocalHazeState].
 * It wraps the app once, above navigation. The capture is attached only while a glass surface is
 * on screen, because a constant full-screen capture would cost every device. Glass inside this
 * tree cannot sample this capture, which is why chrome inside the window needs
 * [glassBackdropLayer].
 */
@Composable
fun GlassBackdrop(content: @Composable () -> Unit) {
    val hazeState = rememberHazeState()
    val demand = remember { GlassDemand() }
    val enabled = glassEnabled()
    CompositionLocalProvider(LocalHazeState provides hazeState, LocalGlassDemand provides demand) {
        Box(Modifier.fillMaxSize().then(if (enabled && demand.count > 0) Modifier.hazeSource(hazeState) else Modifier)) { content() }
    }
}

/** Marks this element as the backdrop for a scoped [LocalHazeState], such as the room's video layer. */
@Composable
fun Modifier.glassBackdropLayer(state: HazeState): Modifier {
    val demand = LocalGlassDemand.current
    val enabled = glassEnabled()
    return then(if (enabled && demand.count > 0) Modifier.hazeSource(state) else Modifier)
}

/** True inside a dialog window, where glass samples the undimmed app window and needs a heavier tint. */
val LocalInDialogWindow = staticCompositionLocalOf { false }

/** Which sides of a panel draw the rim. */
enum class GlassEdge { All, BottomOnly, None }

/** The dim under a modal: light when glass sets the panel apart, heavier when only the dim does. */
val glassScrim: Color
    @Composable
    get() = Color.Black.copy(alpha = if (glassEnabled()) 0.28f else 0.55f)

/**
 * Draws one of the surface tiers ([Tier]). The caller says what kind of surface a thing is, and
 * the material, the rim and the fallback follow from the tier and from whether the surface sits in
 * a dialog window. The shape comes from the caller, to match where the surface is docked, never
 * from a shape scale.
 */
@Composable
fun Modifier.surface(tier: Tier, shape: Shape = RectangleShape, rim: GlassEdge = GlassEdge.All): Modifier = when (tier) {
    Tier.Flat -> clip(shape).background(palette.ground)
    Tier.Panel -> panelGlass(shape, heavy = LocalInDialogWindow.current, rim = rim)
    Tier.Chrome -> chromeSurface(shape)
    Tier.Scrim -> background(glassScrim)
}

/**
 * The chrome tier. It has no blur, so it is safe on chrome that stays composed while video plays.
 * It has a near-black gradient body, a rim lit at the top, and the only shadow in the app, because
 * it floats over moving video with no edge to anchor to.
 */
fun Modifier.chromeSurface(shape: Shape = Radius.panelShape): Modifier = this
    .shadow(20.dp, shape)
    .clip(shape)
    .background(CHROME_BODY)
    // Fixed on purpose: chrome only floats over video, which the room always keeps dark.
    .border(width = 1.dp, brush = OVER_VIDEO_RIM, shape = shape)

/**
 * The chrome body, built once.
 *
 * Its colours never depend on the theme, and [chromeSurface] is not a composable, so nothing would
 * remember a gradient built inside it. Each call would then allocate a colour list and a gradient
 * on every recomposition, and one caller, the scrub bubble, recomposes for the length of a drag.
 */
private val CHROME_BODY: Brush = Brush.verticalGradient(
    listOf(Color(0xFF1B1B21).copy(alpha = 0.90f), Color(0xFF08080B).copy(alpha = 0.94f))
)

/** A blur this wide keeps text readable on a translucent panel with a low tint. */
private val GLASS_BLUR_RADIUS = 40.dp

/**
 * The alpha of the wash built into the glass: black on a dark theme, white on a light one. On a
 * dark theme it stops the panel from glowing over a bright scene.
 */
private const val GLASS_INNER_DIM = 0.40f

/**
 * The panel tier: the backdrop blurred and tinted with the palette's panel colour, and a rim.
 * Haze can sample only what Compose draws, so over a platform video view, below Android 12, and
 * with glass off, this is a plain tonal panel instead.
 */
@Composable
private fun Modifier.panelGlass(shape: Shape, heavy: Boolean, rim: GlassEdge): Modifier {
    val hazeState = LocalHazeState.current
    val container = palette.panel
    val enabled = glassEnabled()
    val tint = if (heavy) 0.38f else 0.26f
    val opaqueTint = if (heavy) 0.80f else 0.65f

    // Demand counts only when glass is on and the surface is visible, so the disabled path never
    // starts a capture and a hidden HUD stops paying for one.
    val demand = LocalGlassDemand.current
    val suspended = LocalGlassSuspended.current
    if (enabled && !suspended) {
        DisposableEffect(demand) {
            demand.acquire()
            onDispose { demand.release() }
        }
    }

    /* Which way the glass shades. A dark theme needs a dark inner wash and a lit rim. A light theme
     * needs the opposite: a fixed white-on-black pair makes a light panel (Daylight, for example)
     * look stained at the bottom and edgeless at the top. */
    val isDark = palette.isDark
    val wash = if (isDark) Color.Black else Color.White
    val style = remember(container, tint, opaqueTint, isDark) {
        HazeBlurStyle {
            blurRadius(GLASS_BLUR_RADIUS)
            // A transparent background makes it glass: only the blurred capture and a tint show.
            backgroundColor(Color.Transparent)
            colorEffects(listOf(HazeColorEffect.tint(wash.copy(alpha = GLASS_INNER_DIM)), HazeColorEffect.tint(container.copy(alpha = tint))))
            fallbackColorEffect(HazeColorEffect.tint(container.copy(alpha = opaqueTint)))
        }
    }
    val fallback = remember(container, opaqueTint, isDark) {
        val foot = if (isDark) lerp(container, Color.Black, 0.45f) else lerp(container, Color.Black, 0.10f)
        Brush.verticalGradient(listOf(container.copy(alpha = opaqueTint), foot.copy(alpha = opaqueTint)))
    }

    return this
        .clip(shape)
        .then(
            if (enabled && hazeState != null) {
                /* Quality keeps the capture at full resolution. The default Behind selection must
                 * stay: All would let glass inside a source sample the capture that contains the
                 * glass itself, and the render thread would recurse until it crashes. */
                Modifier.hazeBlur(input = HazeInput.Sources(hazeState), style = style, performanceMode = HazePerformanceMode.Quality)
            } else {
                Modifier.background(fallback)
            }
        )
        .background(glassSheen(isDark))
        .then(
            when (rim) {
                GlassEdge.All -> Modifier.border(width = 1.dp, brush = panelRim(isDark), shape = shape)
                // Full-bleed chrome draws only the edge that faces content.
                GlassEdge.BottomOnly -> Modifier.drawWithContent {
                    drawContent()
                    val h = 1.dp.toPx()
                    val edge = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.10f)
                    drawRect(edge, Offset(0f, size.height - h), Size(size.width, h))
                }
                GlassEdge.None -> Modifier
            }
        )
}

/** The rim over video and on dark panels: lit along the top edge, nearly gone at the bottom. */
private val OVER_VIDEO_RIM: Brush =
    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f)))

/** The rim for a light theme: the same gradient in black, the only colour that shows there. */
private val LIGHT_PANEL_RIM: Brush =
    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.14f), Color.Black.copy(alpha = 0.04f)))

private fun panelRim(isDark: Boolean): Brush = if (isDark) OVER_VIDEO_RIM else LIGHT_PANEL_RIM

/** The faint top light and bottom shade of a dark panel, built once. */
private val DARK_SHEEN: Brush =
    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.04f), Color.Black.copy(alpha = 0.20f)))

/** A light panel gets a faint shade instead of a highlight, which would not show on it. */
private val LIGHT_SHEEN: Brush =
    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.02f), Color.Black.copy(alpha = 0.08f)))

private fun glassSheen(isDark: Boolean): Brush = if (isDark) DARK_SHEEN else LIGHT_SHEEN

/**
 * Asks the platform to blur whatever sits behind the dialog window that calls it. It is the only
 * blur that reaches a platform video view. It works on Android 12 and up only, and the system
 * refuses it on weak GPUs and in battery saver, so it adds to the panel tint and never replaces it.
 */
@Composable
expect fun DialogBackdropBlur()
