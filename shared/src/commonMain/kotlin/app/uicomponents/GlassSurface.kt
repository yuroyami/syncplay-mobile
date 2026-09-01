package app.uicomponents

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import app.preferences.watchPref
import app.preferences.value
import app.preferences.Preferences
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent

/**
 * The app-wide backdrop that every glass surface samples. Provided once at the composition root
 * over the whole [app.AdamScreen] tree; null only if a surface renders outside it, in which case
 * [glassSurface] falls back to a plain tonal panel.
 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/** Counts the glass surfaces currently on screen, so the backdrop only captures when one needs it. */
@Stable
class GlassDemand {
    var count: Int by mutableIntStateOf(0)
        private set

    fun acquire() { count++ }
    fun release() { count-- }
}

private val LocalGlassDemand = staticCompositionLocalOf { GlassDemand() }

/**
 * The one place the whole glass system asks whether it is allowed to run.
 *
 * False means: no Haze capture, no blur, no platform window blur, solid panels, and (via
 * `useEfficientVideoSurface` on Android) players back on SurfaceView. Read from the
 * [Preferences.DISABLE_FROSTED_GLASS] pref so a change lands everywhere at once.
 */
@Composable
fun glassEnabled(): Boolean = !Preferences.DISABLE_FROSTED_GLASS.watchPref().value

/**
 * Non-composable read of the same switch, for call sites outside composition such as the Android
 * player views deciding which surface type to inflate.
 */
fun glassEnabledNow(): Boolean = !Preferences.DISABLE_FROSTED_GLASS.value()

/**
 * Marks [content] as the backdrop that every glass surface in the app blurs, and publishes it as
 * [LocalHazeState]. Wraps the whole app once, above navigation, so the capture spans screen
 * transitions instead of restarting with each one.
 *
 * `hazeSource` records the content into an offscreen layer on every draw whether or not anything
 * consumes it, so it is attached only while a glass surface is actually on screen. Otherwise every
 * device would pay for a full-screen capture continuously, including the pre-Android-12 ones that
 * get a scrim instead of a blur.
 *
 * A glass surface INSIDE this tree cannot sample this capture (it would be sampling itself), which
 * is why in-window chrome needs [glassBackdropLayer] plus a scoped [LocalHazeState]; dialog and
 * popup windows sample it fine.
 */
@Composable
fun GlassBackdrop(content: @Composable () -> Unit) {
    val hazeState = rememberHazeState()
    val demand = remember { GlassDemand() }

    val enabled = glassEnabled()

    CompositionLocalProvider(
        LocalHazeState provides hazeState,
        LocalGlassDemand provides demand,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (enabled && demand.count > 0) Modifier.hazeSource(hazeState) else Modifier)
        ) { content() }
    }
}

/**
 * Marks this element as the backdrop for a scoped [LocalHazeState] (see [RoomScreenUI]'s video
 * layer): glass drawn in the SAME window can then blur it, which the app-wide backdrop cannot
 * offer them. Demand-gated like [GlassBackdrop], so an idle screen records nothing.
 */
@Composable
fun Modifier.glassBackdropLayer(state: HazeState): Modifier {
    val demand = LocalGlassDemand.current
    val enabled = glassEnabled()
    return then(if (enabled && demand.count > 0) Modifier.hazeSource(state) else Modifier)
}

/** How much of the backdrop a glass surface lets through. Thinner reads as more glass. */
enum class GlassMaterial { UltraThin, Thin, Regular, Thick }

/**
 * Wider than the 24dp HazeMaterials ships with. A stronger blur is what buys readability on a
 * translucent panel, so raising this is what lets the tint stay low without the text getting busy.
 */
private val GLASS_BLUR_RADIUS = 40.dp

/**
 * Dim drawn under a popup. Light when glass is on, because the blur is what separates the panel;
 * heavier when glass is off, where the dim is the only separation left.
 */
val glassScrim: Color
    @Composable
    get() = Color.Black.copy(alpha = if (glassEnabled()) 0.28f else 0.55f)

/**
 * Dim baked into the glass itself, applied to the blurred capture before the color tint. The
 * scrim and the platform dialog dim only darken the world OUTSIDE the panel (they live in the
 * dialog window; the capture samples the app window under it), so without this the panel reads
 * as a hole of light over a bright scene: brighter inside than outside.
 */
private const val GLASS_INNER_DIM = 0.40f

/**
 * Turns the element into a frosted panel: the backdrop behind it is blurred and tinted, with a
 * hairline edge to seat it against whatever it covers.
 *
 * Haze can only sample pixels Compose itself draws. Over a platform video view (ExoPlayer, mpv and
 * every iOS engine and KitePlayer's native path) there is nothing to blur, and the
 * same is true below Android 12 where Haze draws its scrim instead. The material's tint is opaque
 * enough to read against on its own, so those cases degrade to a plain tonal panel rather than to
 * unreadable text.
 */
@Composable
fun Modifier.glassSurface(
    shape: Shape = MaterialTheme.shapes.extraLarge,
    material: GlassMaterial = GlassMaterial.Thin,
    edge: GlassEdge = GlassEdge.All,
): Modifier {
    val hazeState = LocalHazeState.current
    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    val style = glassStyle(material, container)
    val enabled = glassEnabled()

    // Only register demand when glass can actually run, so the disabled path never arms a capture.
    val demand = LocalGlassDemand.current
    if (enabled) {
        DisposableEffect(demand) {
            demand.acquire()
            onDispose { demand.release() }
        }
    }

    return this
        .clip(shape)
        .then(
            if (enabled && hazeState != null) {
                // Quality = full-resolution capture. The default Adaptive mode halves the capture
                // for a blur this wide, and the noise grain gets stamped at that half resolution
                // and upscaled into mushy blobs. Blur hides downscaling; grain does not.
                // Default Behind selection, and that is load-bearing: All would let glass INSIDE
                // a source sample the capture that contains itself, and HWUI walks that cycle
                // until the render thread stack overflows (SIGSEGV, 512 frames deep). Behind
                // excludes the same-state ancestor: in-window glass blurs only a sibling layer
                // (the room's video capture), and windowed glass (dialogs, menus) has no
                // ancestor so it samples everything.
                Modifier.hazeBlur(
                    input = HazeInput.Sources(hazeState),
                    style = style,
                    performanceMode = HazePerformanceMode.Quality,
                )
            } else {
                // Solid fallback keeps the pill's body: the container color sliding into a
                // darkened version of itself, top to bottom.
                Modifier.background(
                    Brush.verticalGradient(
                        listOf(
                            container.copy(alpha = opaqueTintAlpha(material)),
                            lerp(container, Color.Black, 0.45f).copy(alpha = opaqueTintAlpha(material)),
                        )
                    )
                )
            }
        )
        // Subtle top-lit body shading over the blur, the depth cue the gesture pill wears.
        .background(
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.04f), Color.Black.copy(alpha = 0.20f))
            )
        )
        .then(
            when (edge) {
                GlassEdge.All -> Modifier.border(width = 1.dp, brush = glassEdgeBrush(), shape = shape)
                // Full-bleed chrome: a rim on the screen edges reads as a floating card, so only
                // the edge that actually faces content gets drawn.
                GlassEdge.BottomOnly -> Modifier.drawWithContent {
                    drawContent()
                    val h = 1.dp.toPx()
                    drawRect(
                        color = Color.White.copy(alpha = 0.10f),
                        topLeft = Offset(0f, size.height - h),
                        size = Size(size.width, h),
                    )
                }
                GlassEdge.None -> Modifier
            }
        )
}

/** Which sides of a glass surface draw the rim. */
enum class GlassEdge { All, BottomOnly, None }

/**
 * The hairline that sells the panel as a physical object: lit along the top edge, fading to
 * nearly nothing at the bottom, exactly like the gesture pill's rim.
 */
@Composable
@ReadOnlyComposable
private fun glassEdgeBrush(): Brush = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f))
)

/**
 * Deliberately NOT built from `HazeMaterials`. Those presets fill an opaque `backgroundColor`
 * rect underneath the captured content before blurring it, which makes the panel a solid
 * rectangle holding a picture of the blur rather than something you can see through. Keeping the
 * background transparent is what makes the surface real glass: only the blurred capture and a
 * light tint are painted, so whatever the capture missed still shows through.
 */
@Composable
@ReadOnlyComposable
private fun glassStyle(material: GlassMaterial, container: Color): HazeBlurStyle = HazeBlurStyle {
    blurRadius(GLASS_BLUR_RADIUS)
    backgroundColor(Color.Transparent)
    // Ordered: darken the blurred capture first, then lay the material color over it.
    colorEffects(
        listOf(
            HazeColorEffect.tint(Color.Black.copy(alpha = GLASS_INNER_DIM)),
            HazeColorEffect.tint(container.copy(alpha = tintAlpha(material))),
        )
    )
    // Below Android 12 there is no blur at all, so the tint alone has to carry readability.
    fallbackColorEffect(HazeColorEffect.tint(container.copy(alpha = opaqueTintAlpha(material))))
}

/** Tint over a real blur. Low on purpose: the blur is what separates the panel, not the opacity. */
private fun tintAlpha(material: GlassMaterial): Float = when (material) {
    GlassMaterial.UltraThin -> 0.16f
    GlassMaterial.Thin -> 0.26f
    GlassMaterial.Regular -> 0.38f
    GlassMaterial.Thick -> 0.52f
}

/** Tint with no blur behind it, so it has to be heavy enough to read against on its own. */
private fun opaqueTintAlpha(material: GlassMaterial): Float = when (material) {
    GlassMaterial.UltraThin -> 0.55f
    GlassMaterial.Thin -> 0.65f
    GlassMaterial.Regular -> 0.80f
    GlassMaterial.Thick -> 0.90f
}

/**
 * Asks the platform to blur whatever sits behind the dialog window this is called from.
 *
 * This is the only blur that reaches a platform video view, because the system compositor runs it
 * on the finished screen rather than on Compose's own pixels. Android 12+ only, and the system
 * still refuses it on weak GPUs, in battery saver, and during some protected video playback, so it
 * is an enhancement on top of [glassSurface], never a replacement for it.
 */
@Composable
expect fun DialogBackdropBlur()
