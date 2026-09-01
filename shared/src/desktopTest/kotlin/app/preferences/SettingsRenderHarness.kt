package app.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.LocalGlobalViewmodel
import app.LocalSettingStyling
import app.LocalTheme
import app.SyncplayViewmodel
import app.preferences.settings.GLOBAL_ADVANCED
import app.preferences.settings.GLOBAL_NETWORK
import app.preferences.settings.INROOM_CHAT_PROPERTIES
import app.preferences.settings.INROOM_PLAYER_SETTINGS
import app.preferences.settings.SettingCategory
import app.preferences.settings.SettingStyling
import app.preferences.settings.SettingsUI
import app.preferences.settings.settingGLOBALstyle
import app.preferences.settings.settingROOMstyle
import app.theme.defaultTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * Renders the REAL settings composables headlessly and writes PNGs, so a redesign can be
 * measured against pixels instead of guesses. Not an assertion test: it dumps images to
 * SETTINGS_RENDER_OUT (or the temp dir) and prints where they landed.
 */
class SettingsRenderHarness {

    private val outDir = File(
        System.getenv("SETTINGS_RENDER_OUT")
            ?: (System.getProperty("java.io.tmpdir") + "/settings-render")
    )

    private fun initDatastore() {
        val dir = File(System.getProperty("java.io.tmpdir"), "synkplay-render-harness")
        dir.mkdirs()
        datastore = createDataStore { File(dir, "harness.preferences_pb").absolutePath }
        // Force the lazy hot flow to build now, while the datastore is definitely assigned.
        datastoreStateFlow.value
    }

    @Composable
    private fun Harness(styling: SettingStyling, content: @Composable () -> Unit) {
        val vm = SyncplayViewmodel()
        MaterialTheme(colorScheme = darkColorScheme()) {
            CompositionLocalProvider(
                LocalGlobalViewmodel provides vm,
                LocalTheme provides defaultTheme,
                LocalSettingStyling provides styling,
            ) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) { content() }
            }
        }
    }

    private fun renderCategory(
        name: String,
        category: SettingCategory,
        widthDp: Int,
        styling: SettingStyling,
        heightDp: Int = 3000,
    ) {
        val density = Density(2f)
        val scene = ImageComposeScene(
            width = (widthDp * density.density).toInt(),
            height = (heightDp * density.density).toInt(),
            density = density,
        ) {
            Harness(styling) {
                Box(Modifier.width(widthDp.dp)) {
                    SettingsUI.SettingScreen(settingCategory = category)
                }
            }
        }
        try {
            // Compose resources load asynchronously; pump frames so the strings resolve.
            var img = scene.render(0L)
            repeat(30) { i -> img = scene.render((i + 1) * 16_000_000L) }
            outDir.mkdirs()
            val f = File(outDir, "$name-${widthDp}dp.png")
            img.encodeToData(EncodedImageFormat.PNG)?.bytes?.let { f.writeBytes(it) }
            println("RENDERED $name @${widthDp}dp -> ${f.absolutePath}")
        } finally {
            scene.close()
        }
    }

    @Test
    fun renderCurrentSettings() {
        initDatastore()
        renderCategory("global-network", GLOBAL_NETWORK, 360, settingGLOBALstyle)
        renderCategory("global-advanced", GLOBAL_ADVANCED, 360, settingGLOBALstyle)
        renderCategory("inroom-player", INROOM_PLAYER_SETTINGS, 360, settingGLOBALstyle)
        renderCategory("inroom-chat", INROOM_CHAT_PROPERTIES, 360, settingGLOBALstyle)
        renderCategory("inroom-player-roomstyle", INROOM_PLAYER_SETTINGS, 300, settingROOMstyle)
        renderCategory("global-network-tablet", GLOBAL_NETWORK, 720, settingGLOBALstyle)
        println("OUT_DIR = ${outDir.absolutePath}")
    }
}
