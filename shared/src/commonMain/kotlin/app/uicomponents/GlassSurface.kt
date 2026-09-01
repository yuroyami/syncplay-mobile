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
 * The backdrop every glass surface samples. Provided once at the root over the whole tree; null
 * only for a surface rendered outside it, which then gets a plain tonal panel.
 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/** Counts the glass surfaces on screen, so the backdrop only captures when one needs it. */
@Stable
class GlassDemand {
    var count: Int by mutableIntStateOf(0)
        private set

    fun acquire() { count++ }
    fun release() { count-- }
}

private val LocalGlassDemand = staticCompositionLocalOf { GlassDemand() }

/** The one switch for the whole glass system: no capture, no blur, solid panels when off. */
@Composable
fun glassEnabled(): Boolean = !Preferences.DISABLE_FROSTED_GLASS.watchPref().value

/** The same switch outside composition, for the Android player views choosing a surface type. */
fun glassEnabledNow(): Boolean = !Preferences.DISABLE_FROSTED_GLASS.value()

/**
 * Marks [content] as the backdrop every glass surface blurs, and publishes it as [LocalHazeState].
 * Wraps the app once above navigation. The capture is attached only while a glass surface is on
 * screen, or every device would pay for a full-screen capture continuously. Glass inside this
 * tree cannot sample this capture, which is why in-window chrome needs [glassBackdropLayer].
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

/** Dim drawn under a modal: light when glass separates the panel, heavier when only the dim does. */
val glassScrim: Color
    @Composable
    get() = Color.Black.copy(alpha = if (glassEnabled()) 0.28f else 0.55f)

/**
 * The surface tiers from DESIGN/GLASS_SURFACES. Callers say what kind of surface a thing is; the
 * material, rim and fallback follow from the tier and from whether it sits in a dialog window.
 * Shapes come from the caller's dock, never from a shape scale.
 */
@Composable
fun Modifier.surface(tier: Tier, shape: Shape = RectangleShape, rim: GlassEdge = GlassEdge.All): Modifier = when (tier) {
    Tier.Flat -> clip(shape).background(palette.ground)
    Tier.Panel -> panelGlass(shape, heavy = LocalInDialogWindow.current, rim = rim)
    Tier.Chrome -> chromeSurface(shape)
    Tier.Scrim -> background(glassScrim)
}

/**
 * The chrome tier: no blur, so it is safe on chrome that stays composed while video plays. A
 * near black gradient body, the top-lit rim, and the one shadow in the app, because this floats
 * over moving video with no edge to anchor to.
 */
fun Modifier.chromeSurface(shape: Shape = Radius.panelShape): Modifier = this
    .shadow(20.dp, shape)
    .clip(shape)
    .background(Brush.verticalGradient(listOf(Color(0xFF1B1B21).copy(alpha = 0.90f), Color(0xFF08080B).copy(alpha = 0.94f))))
    .border(width = 1.dp, brush = rimBrush(), shape = shape)

/** A blur this wide is what buys readability on a translucent panel with a low tint. */
private val GLASS_BLUR_RADIUS = 40.dp

/** Dim baked into the glass, so the panel is not a hole of light over a bright scene. */
private const val GLASS_INNER_DIM = 0.40f

/**
 * The panel tier: the backdrop blurred and tinted with the palette's panel colour, and a rim.
 * Haze can only sample what Compose draws, so over a platform video view, below Android 12, and
 * with glass off this is a plain tonal panel instead.
 */
@Composable
private fun Modifier.panelGlass(shape: Shape, heavy: Boolean, rim: GlassEdge): Modifier {
    val hazeState = LocalHazeState.current
    val container = palette.panel
    val enabled = glassEnabled()
    val tint = if (heavy) 0.38f else 0.26f
    val opaqueTint = if (heavy) 0.80f else 0.65f

    // Demand is registered only when glass can run, so the disabled path never arms a capture.
    val demand = LocalGlassDemand.current
    if (enabled) {
        DisposableEffect(demand) {
            demand.acquire()
            onDispose { demand.release() }
        }
    }

    val style = HazeBlurStyle {
        blurRadius(GLASS_BLUR_RADIUS)
        // A transparent background is what makes it glass: only the blurred capture and a light tint.
        backgroundColor(Color.Transparent)
        colorEffects(listOf(HazeColorEffect.tint(Color.Black.copy(alpha = GLASS_INNER_DIM)), HazeColorEffect.tint(container.copy(alpha = tint))))
        fallbackColorEffect(HazeColorEffect.tint(container.copy(alpha = opaqueTint)))
    }

    return this
        .clip(shape)
        .then(
            if (enabled && hazeState != null) {
                /* Quality keeps the capture at full resolution, and the default Behind selection
                 * is load-bearing: All would let glass inside a source sample the capture that
                 * contains itself and the render thread recurses until it dies. */
                Modifier.hazeBlur(input = HazeInput.Sources(hazeState), style = style, performanceMode = HazePerformanceMode.Quality)
            } else {
                Modifier.background(
                    Brush.verticalGradient(
                        listOf(container.copy(alpha = opaqueTint), lerp(container, Color.Black, 0.45f).copy(alpha = opaqueTint))
                    )
                )
            }
        )
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.04f), Color.Black.copy(alpha = 0.20f))))
        .then(
            when (rim) {
                GlassEdge.All -> Modifier.border(width = 1.dp, brush = rimBrush(), shape = shape)
                // Full-bleed chrome draws only the edge that faces content.
                GlassEdge.BottomOnly -> Modifier.drawWithContent {
                    drawContent()
                    val h = 1.dp.toPx()
                    drawRect(Color.White.copy(alpha = 0.10f), Offset(0f, size.height - h), Size(size.width, h))
                }
                GlassEdge.None -> Modifier
            }
        )
}

/** The rim: lit along the top edge, nearly gone at the bottom. */
private fun rimBrush(): Brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f)))

/**
 * Asks the platform to blur whatever sits behind the dialog window this is called from. The only
 * blur that reaches a platform video view; Android 12 and up, refused on weak GPUs and in battery
 * saver, so an enhancement over the panel tint, never a replacement.
 */
@Composable
expect fun DialogBackdropBlur()
