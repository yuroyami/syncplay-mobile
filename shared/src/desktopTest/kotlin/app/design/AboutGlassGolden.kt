package app.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.Screen
import app.home.HomeScreenUI
import app.home.HomeViewmodel
import app.home.components.PopupAPropos
import app.preferences.Preferences
import app.preferences.set
import app.theme.DAYLIGHT
import app.theme.SaveableTheme
import app.theme.TRINITY
import app.uicomponents.GlassKind
import app.uicomponents.LocalHazeState
import app.uicomponents.LocalInDialogWindow
import app.uicomponents.LocalWidthClass
import app.uicomponents.WidthClass
import app.uicomponents.frames.ModalFrame
import app.uicomponents.frames.ModalSize
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The About popup in liquid glass over the home screen, the screen it opens on. The harness has no
 * dialog window, so the home screen sits in a Haze source and the frame draws over it, as the
 * popup's window does over the app's window. One image per theme puts the frosted panel and the
 * liquid one side by side.
 */
class AboutGlassGolden {

    @Composable
    private fun AboutOverHome(glass: GlassKind, widthDp: Int, heightDp: Int, widthClass: WidthClass) {
        val backdrop = rememberHazeState()
        CompositionLocalProvider(LocalHazeState provides backdrop, LocalWidthClass provides widthClass) {
            Box(Modifier.size(widthDp.dp, heightDp.dp)) {
                Box(Modifier.size(widthDp.dp, heightDp.dp).hazeSource(backdrop)) {
                    HomeScreenUI(remember { HomeViewmodel(mutableStateListOf(Screen.Home)) })
                }
                CompositionLocalProvider(LocalInDialogWindow provides true) {
                    ModalFrame(ModalSize.Panel, null, true, {}, actions = null, glass = glass) {
                        PopupAPropos.AboutBody(
                            updateResult = null,
                            updateChecking = false,
                            onCheckUpdate = {},
                            onOpenUri = {},
                            onLicences = {},
                            onWatchAlone = {},
                        )
                    }
                }
            }
        }
    }

    /** Renders [content] into a [widthDp] x [heightDp] image and returns its file and text layouts. */
    private fun render(name: String, theme: SaveableTheme, widthDp: Int, heightDp: Int, content: @Composable () -> Unit): Pair<File, List<TextLayoutResult>> {
        DesignHarness.initDatastore()
        // The home screen opens its tips popup at start, and the harness draws a dialog over everything.
        runBlocking { Preferences.NEVER_SHOW_TIPS.set(true) }
        val density = Density(2f, 1f)
        val scene = DesignHarness.onUiThread {
            ImageComposeScene(width = widthDp * 2, height = heightDp * 2, density = density, coroutineContext = Dispatchers.Main.immediate) {
                DesignHarness.Frame(theme) { content() }
            }
        }
        return try {
            var image = DesignHarness.onUiThread { scene.render(0L) }
            // The sheet slides in and Haze captures its backdrop over a few frames.
            repeat(40) { i -> image = DesignHarness.onUiThread { scene.render((i + 1) * 16_000_000L) } }
            val file = File(DesignHarness.outDir, "$name-${theme.name.lowercase().replace(' ', '_')}.png")
            image.encodeToData(EncodedImageFormat.PNG)?.bytes?.let(file::writeBytes)
            println("GOLDEN ${file.absolutePath}")
            val texts = mutableListOf<TextLayoutResult>()
            DesignHarness.onUiThread { scene.semanticsOwners.forEach { collectText(it.unmergedRootSemanticsNode, texts) } }
            file to texts
        } finally {
            DesignHarness.onUiThread { scene.close() }
        }
    }

    private fun collectText(node: SemanticsNode, into: MutableList<TextLayoutResult>) {
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(into)
        node.children.forEach { collectText(it, into) }
    }

    private fun assertNoTextCut(file: File, texts: List<TextLayoutResult>) {
        assertTrue(texts.isNotEmpty(), "No text layouts in ${file.name}")
        for (layout in texts) {
            val label = "${file.name}: ${layout.layoutInput.text.text}"
            assertTrue(!layout.multiParagraph.didExceedMaxLines, "Clipped lines: $label")
            for (line in 0 until layout.lineCount) assertTrue(!layout.isLineEllipsized(line), "Truncated text: $label")
        }
    }

    @Test
    fun aboutInLiquidGlassKeepsEveryWordOverTheHomeScreen() {
        for (theme in listOf(TRINITY, DAYLIGHT)) {
            val (phone, phoneTexts) = render("about-liquid-390x844", theme, 390, 844) {
                AboutOverHome(GlassKind.Liquid, 390, 844, WidthClass.Compact)
            }
            assertNoTextCut(phone, phoneTexts)
            val (desktop, desktopTexts) = render("about-liquid-1280x800", theme, 1280, 800) {
                AboutOverHome(GlassKind.Liquid, 1280, 800, WidthClass.Expanded)
            }
            assertNoTextCut(desktop, desktopTexts)
            // For the eye: the frosted panel on the left, the liquid one on the right.
            render("about-frosted-vs-liquid", theme, 780, 844) {
                Row {
                    AboutOverHome(GlassKind.Frosted, 390, 844, WidthClass.Compact)
                    AboutOverHome(GlassKind.Liquid, 390, 844, WidthClass.Compact)
                }
            }
        }
    }
}
