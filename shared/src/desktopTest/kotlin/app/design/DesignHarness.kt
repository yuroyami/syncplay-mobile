package app.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.LocalTheme
import app.theme.LocalPalette
import app.theme.Palette
import app.theme.SaveableTheme
import app.theme.TRINITY
import app.theme.DAYLIGHT
import app.theme.appShapes
import app.theme.appTypography
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/**
 * Renders real composables headlessly and writes PNGs, the way every DESIGN measurement is
 * made. Provides the locals a surface needs outside AdamScreen. Resource fonts do not resolve
 * here, so goldens judge layout and spacing, not letterforms.
 */
object DesignHarness {

    val outDir: File = File(System.getenv("DESIGN_GOLDEN_OUT") ?: "build/design-goldens").also { it.mkdirs() }

    /** The measured height of the last render, in dp. */
    data class Result(val file: File, val contentHeightDp: Int)

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
    fun Frame(theme: SaveableTheme, overVideo: Boolean = false, content: @Composable () -> Unit) {
        val base = Palette.from(theme.dynamicScheme, theme)
        val pal = if (overVideo) base.overVideo() else base
        val prefs = datastoreStateFlow.collectAsState()
        val vm = remember { SyncplayViewmodel() }
        CompositionLocalProvider(
            LocalTheme provides theme,
            LocalPalette provides pal,
            LocalPrefsState provides prefs,
            LocalGlobalViewmodel provides vm,
        ) {
            MaterialTheme(colorScheme = theme.dynamicScheme, typography = appTypography, shapes = appShapes) {
                Box(Modifier.fillMaxSize().background(pal.ground)) { content() }
            }
        }
    }

    fun render(
        name: String,
        widthDp: Int,
        heightDp: Int = 1600,
        fontScale: Float = 1f,
        theme: SaveableTheme = TRINITY,
        overVideo: Boolean = false,
        content: @Composable () -> Unit,
    ): Result {
        initDatastore()
        val density = Density(2f, fontScale)
        var measuredPx = 0
        val scene = ImageComposeScene(
            width = (widthDp * density.density).toInt(),
            height = (heightDp * density.density).toInt(),
            density = density,
        ) {
            Frame(theme, overVideo) {
                Box(Modifier.width(widthDp.dp).onSizeChanged { measuredPx = it.height }) { content() }
            }
        }
        return try {
            var image = scene.render(0L)
            // Animations and resource loading need frames; 30 x 16ms covers every entrance.
            repeat(30) { i -> image = scene.render((i + 1) * 16_000_000L) }
            val suffix = buildString {
                append("-${widthDp}dp")
                if (fontScale != 1f) append("-fs${fontScale}")
                if (theme !== TRINITY) append("-${theme.name.lowercase().replace(' ', '_')}")
                if (overVideo) append("-video")
            }
            val file = File(outDir, "$name$suffix.png")
            image.encodeToData(EncodedImageFormat.PNG)?.bytes?.let(file::writeBytes)
            val heightDpMeasured = (measuredPx / density.density).toInt()
            println("GOLDEN $name$suffix height=${heightDpMeasured}dp -> ${file.absolutePath}")
            Result(file, heightDpMeasured)
        } finally {
            scene.close()
        }
    }

    val lightTheme: SaveableTheme get() = DAYLIGHT
}
