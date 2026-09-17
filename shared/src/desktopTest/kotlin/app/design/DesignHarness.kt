package app.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import app.LocalGlobalViewmodel
import app.SyncplayViewmodel
import app.preferences.LocalPrefsState
import app.preferences.createDataStore
import app.preferences.datastore
import app.preferences.datastoreStateFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.LocalTheme
import app.i18n.EnAppStrings
import app.i18n.LocalAppStrings
import app.i18n.appStrings
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import app.theme.LocalPalette
import app.theme.Palette
import app.theme.SaveableTheme
import app.theme.TRINITY
import app.theme.DAYLIGHT
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlinx.coroutines.Dispatchers
import javax.swing.SwingUtilities
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Renders real composables headlessly and writes PNGs, the way every DESIGN measurement is
 * made. Provides the locals a surface needs outside AdamScreen. Resource fonts do not resolve
 * here, so goldens judge layout and spacing, not letterforms.
 */
object DesignHarness {

    val outDir: File = File(System.getenv("DESIGN_GOLDEN_OUT") ?: "build/design-goldens").also { it.mkdirs() }

    /** The measured height of the last render, in dp. */
    data class Result(val file: File, val contentHeightDp: Int, val textLayouts: List<TextLayoutResult>) {
        fun assertAllTextFits() {
            assertTrue(textLayouts.isNotEmpty(), "No text layouts in ${file.name}")
            for (layout in textLayouts) {
                // An empty label has nothing to clip; a value column with no value draws one.
                if (layout.layoutInput.text.text.isEmpty()) continue
                val label = "${file.name}: ${layout.layoutInput.text.text}"
                assertFalse(layout.multiParagraph.didExceedMaxLines, "Clipped lines: $label")
                for (line in 0 until layout.lineCount) {
                    assertFalse(layout.isLineEllipsized(line), "Truncated text: $label")
                    // Paragraph width may retain loose measurement constraints even when the
                    // text fits its final size. Check the actual line width, not hasVisualOverflow.
                    assertTrue(layout.getLineRight(line) - layout.getLineLeft(line) <= layout.size.width + 1f, "Clipped width: $label")
                    assertTrue(layout.getLineBottom(line) <= layout.size.height + 1f, "Clipped height: $label")
                }
            }
        }
    }

    private fun textLayouts(node: SemanticsNode): List<TextLayoutResult> {
        val layouts = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        node.children.forEach { layouts += textLayouts(it) }
        return layouts
    }

    private var datastoreReady = false

    /** Surfaces read preferences (the glass switch, the settings rows), so the harness owns a throwaway datastore. */
    @Synchronized
    fun initDatastore() {
        if (datastoreReady) return
        val dir = File(System.getProperty("java.io.tmpdir"), "synkplay-design-harness").also { it.mkdirs() }
        File(dir, "harness.preferences_pb").delete()
        datastore = createDataStore { File(dir, "harness.preferences_pb").absolutePath }
        datastoreStateFlow.value
        datastoreReady = true
    }

    @Composable
    fun Frame(theme: SaveableTheme, overVideo: Boolean = false, language: String = "en", content: @Composable () -> Unit) {
        val base = Palette.from(theme.dynamicScheme, theme)
        val pal = if (overVideo) base.overVideo() else base
        val prefs = datastoreStateFlow.collectAsState()
        val vm = remember { SyncplayViewmodel() }
        CompositionLocalProvider(
            LocalTheme provides theme,
            LocalPalette provides pal,
            LocalPrefsState provides prefs,
            LocalGlobalViewmodel provides vm,
            LocalAppStrings provides (appStrings[language] ?: EnAppStrings),
            // The app pins this in AdamScreen; the harness has to match or Arabic renders mirrored.
            LocalLayoutDirection provides LayoutDirection.Ltr,
        ) {
            run {
                Box(Modifier.fillMaxSize().background(pal.ground)) { content() }
            }
        }
    }

    /** Scene creation, measurement and disposal share the same thread as Compose's callbacks. */
    internal fun <T> onUiThread(action: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) return action()
        var result: kotlin.Result<T>? = null
        SwingUtilities.invokeAndWait { result = runCatching(action) }
        return result!!.getOrThrow()
    }

    fun render(
        name: String,
        widthDp: Int,
        heightDp: Int = 1600,
        fontScale: Float = 1f,
        theme: SaveableTheme = TRINITY,
        overVideo: Boolean = false,
        /** Renders the screen in one of the shipped languages. English unless a test says otherwise. */
        language: String = "en",
        content: @Composable () -> Unit,
    ): Result {
        initDatastore()
        val density = Density(2f, fontScale)
        var measuredPx = 0
        val scene = onUiThread { ImageComposeScene(
            width = (widthDp * density.density).toInt(),
            height = (heightDp * density.density).toInt(),
            density = density,
            coroutineContext = Dispatchers.Main.immediate,
        ) {
            Frame(theme, overVideo, language) {
                Box(Modifier.width(widthDp.dp).onSizeChanged { measuredPx = it.height }) { content() }
            }
        } }
        return try {
            var image = onUiThread { scene.render(0L) }
            // Animations and resource loading need frames; 30 x 16ms covers every entrance.
            repeat(30) { i -> image = onUiThread { scene.render((i + 1) * 16_000_000L) } }
            val suffix = buildString {
                append("-${widthDp}dp")
                if (fontScale != 1f) append("-fs${fontScale}")
                if (theme !== TRINITY) append("-${theme.name.lowercase().replace(' ', '_')}")
                if (overVideo) append("-video")
                if (language != "en") append("-$language")
            }
            val file = File(outDir, "$name$suffix.png")
            image.encodeToData(EncodedImageFormat.PNG)?.bytes?.let(file::writeBytes)
            val heightDpMeasured = (measuredPx / density.density).toInt()
            println("GOLDEN $name$suffix height=${heightDpMeasured}dp -> ${file.absolutePath}")
            val layouts = onUiThread { scene.semanticsOwners.flatMap { textLayouts(it.unmergedRootSemanticsNode) } }
            Result(file, heightDpMeasured, layouts)
        } finally {
            onUiThread { scene.close() }
        }
    }

    val lightTheme: SaveableTheme get() = DAYLIGHT
}
