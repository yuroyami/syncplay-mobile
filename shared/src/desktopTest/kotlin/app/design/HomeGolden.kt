package app.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import app.Screen
import app.home.HomeScreenUI
import app.home.HomeViewmodel
import app.home.JoinConfig
import app.home.JoinRow
import app.home.components.HomeEnginePicker
import app.player.PlayerEngine
import app.player.PlayerImpl
import app.preferences.Preferences
import app.preferences.datastore
import app.preferences.prefKey
import app.preferences.set
import app.preferences.value
import app.room.RoomViewmodel
import app.theme.Space
import app.theme.TRINITY
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.skia.EncodedImageFormat
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.exoplayer
import syncplaymobile.shared.generated.resources.kiteplayer
import syncplaymobile.shared.generated.resources.mpv
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The home form across the windows it really meets. Every size here is a device's screen with
 * its system bars already taken off, because the harness has no insets of its own: 360x640 with
 * three-button navigation leaves 360x568, an iPhone 14 leaves 390x763, a Pixel 412x867.
 *
 * Two things are pinned. No label is ever cut at the default text size, and the join key is on
 * screen at rest wherever the form is meant to fit without scrolling. The join key sat below the
 * fold on most phones before the form learned to tighten.
 */
class HomeGolden {

    private class Home(val file: File, val textLayouts: List<TextLayoutResult>, val join: Rect?, val heightPx: Int) {
        fun assertAllTextFits() {
            assertTrue(textLayouts.isNotEmpty(), "No text layouts in ${file.name}")
            for (layout in textLayouts) {
                val label = "${file.name}: ${layout.layoutInput.text.text}"
                assertTrue(!layout.multiParagraph.didExceedMaxLines, "Clipped lines: $label")
                for (line in 0 until layout.lineCount) {
                    assertTrue(!layout.isLineEllipsized(line), "Truncated text: $label")
                }
            }
        }

        fun assertJoinOnScreen() {
            val bounds = assertNotNull(join, "${file.name}: no join key in the semantics tree")
            assertTrue(bounds.bottom <= heightPx + 1f && bounds.top >= 0f, "${file.name}: join key at ${bounds.top}..${bounds.bottom} px, window is $heightPx px tall")
        }
    }

    private fun collect(node: SemanticsNode, texts: MutableList<TextLayoutResult>, joins: MutableList<Rect>) {
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(texts)
        if (node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == JOIN_LABEL } == true) joins += node.boundsInRoot
        node.children.forEach { collect(it, texts, joins) }
    }

    /** One home screen in a window of exactly [w] x [h] dp, no insets, the bar included. */
    private fun renderHome(name: String, w: Int, h: Int, fontScale: Float = 1f): Home {
        DesignHarness.initDatastore()
        val densityValue = if (w > 1400 || h > 1400) 1f else 2f
        val density = Density(densityValue, fontScale)
        val scene = DesignHarness.onUiThread { ImageComposeScene(width = (w * densityValue).toInt(), height = (h * densityValue).toInt(), density = density, coroutineContext = Dispatchers.Main.immediate) {
            DesignHarness.Frame(TRINITY) {
                Box(Modifier.size(w.dp, h.dp)) {
                    HomeScreenUI(remember { HomeViewmodel(mutableStateListOf(Screen.Home)) })
                }
            }
        } }
        return try {
            var image = DesignHarness.onUiThread { scene.render(0L) }
            repeat(30) { i -> image = DesignHarness.onUiThread { scene.render((i + 1) * 16_000_000L) } }
            val suffix = if (fontScale != 1f) "-fs$fontScale" else ""
            val file = File(DesignHarness.outDir, "home-$name-${w}x${h}$suffix.png")
            image.encodeToData(EncodedImageFormat.PNG)?.bytes?.let(file::writeBytes)
            val texts = mutableListOf<TextLayoutResult>()
            val joins = mutableListOf<Rect>()
            DesignHarness.onUiThread { scene.semanticsOwners.forEach { collect(it.unmergedRootSemanticsNode, texts, joins) } }
            println("GOLDEN ${file.name} join=${joins.firstOrNull()}")
            Home(file, texts, joins.firstOrNull(), (h * densityValue).toInt())
        } finally {
            DesignHarness.onUiThread { scene.close() }
        }
    }

    private fun saveJoin(config: JoinConfig?) = runBlocking {
        Preferences.NEVER_SHOW_TIPS.set(true)
        if (config == null) {
            datastore.edit { it.remove(Preferences.JOIN_CONFIG.prefKey()) }
            var tries = 0
            while (Preferences.JOIN_CONFIG.value() != null && tries++ < 50) Thread.sleep(20)
        } else {
            val json = Json.encodeToString(config)
            Preferences.JOIN_CONFIG.set(json)
            var tries = 0
            while (Preferences.JOIN_CONFIG.value() != json && tries++ < 50) Thread.sleep(20)
        }
    }

    private val official = JoinConfig(user = "yuroyami", room = "movie-night", ip = "syncplay.pl", port = 8997)
    private val custom = JoinConfig(user = "yuroyami", room = "movie-night", ip = "192.168.1.20", port = 8999, pw = "secret")
    private val hosted = JoinConfig(user = "yuroyami", room = "movie-night", ip = "127.0.0.1", port = 8999)

    /** Screens minus their bars, where the form must fit at rest in Official mode. */
    private val fitsAtRest = listOf(
        // phones upright: 360x640 with buttons, iPhone SE 3, 360x780 with buttons, iPhone 14, Pixel
        360 to 568, 375 to 647, 360 to 708, 390 to 763, 412 to 867,
        // phones on their side: an 800x360 Android with gestures, an iPhone 14
        800 to 312, 750 to 369,
        // tablets, the desktop minimum, desktop windows
        600 to 936, 768 to 1000, 1024 to 744, 800 to 480, 1280 to 720, 1920 to 1080,
        // a third of a tablet, a short wide window, a foldable inner screen
        300 to 900, 1200 to 400, 673 to 800,
    )

    @Test
    fun officialFitsAtRestAndNothingIsCut() {
        saveJoin(official)
        for ((w, h) in fitsAtRest) {
            val home = renderHome("official", w, h)
            home.assertAllTextFits()
            home.assertJoinOnScreen()
        }
        // The smallest iPhone scrolls a few points; its labels still have to be whole.
        renderHome("official", 320, 548).assertAllTextFits()
        // Bigger text: the tiers move with it, so the common phones still fit at rest.
        for ((w, h) in listOf(390 to 763, 412 to 867)) {
            val home = renderHome("official", w, h, fontScale = 1.3f)
            home.assertAllTextFits()
            home.assertJoinOnScreen()
        }
        // 200 percent only has to render; segmented labels may ellipsise at that size.
        renderHome("official", 412, 867, fontScale = 2f)
    }

    @Test
    fun freshInstallAndOtherServerModesKeepTheirWords() {
        saveJoin(null)
        for ((w, h) in listOf(360 to 568, 390 to 763, 800 to 312, 1280 to 720)) {
            val home = renderHome("fresh", w, h)
            home.assertAllTextFits()
            home.assertJoinOnScreen()
        }
        saveJoin(custom)
        for ((w, h) in listOf(360 to 568, 390 to 763, 800 to 312, 768 to 1000, 1280 to 720)) renderHome("custom", w, h).assertAllTextFits()
        // Custom has two more rows; the phones that carry them at rest.
        for ((w, h) in listOf(390 to 763, 412 to 867, 800 to 312)) renderHome("custom", w, h).assertJoinOnScreen()
        saveJoin(hosted)
        for ((w, h) in listOf(360 to 568, 390 to 763, 800 to 312, 768 to 1000, 1280 to 720)) renderHome("host", w, h).assertAllTextFits()
        // The hosting panel outgrows a phone, but on a wide window the join key stays put.
        for ((w, h) in listOf(800 to 312, 1024 to 744, 1280 to 720)) renderHome("host", w, h).assertJoinOnScreen()
        saveJoin(official)
    }

    /**
     * The three-engine picker Android shows, which the desktop harness never composes on its
     * own. 284dp is the narrowest column the split form makes; 320dp phones get 284 too.
     */
    @Test
    fun threeEnginePickerKeepsEveryWordFromTheNarrowestColumn() {
        val engines = listOf(
            FakeEngine("ExoPlayer", Res.drawable.exoplayer, isSystem = true),
            FakeEngine("mpv", Res.drawable.mpv, isDefault = true),
            FakeEngine("KitePlayer", Res.drawable.kiteplayer, isExperimental = true),
        )
        for (w in listOf(284, 324, 393)) {
            for (compact in listOf(false, true)) {
                val result = DesignHarness.render(if (compact) "engine-picker-compact" else "engine-picker", w, heightDp = 220) {
                    Box(Modifier.fillMaxWidth()) {
                        HomeEnginePicker(engines = engines, selectedEngine = "mpv", onSelectEngine = {}, compact = compact)
                    }
                }
                result.assertAllTextFits()
            }
        }
        // Bigger text may shrink the words toward the floor, never below the picker's height.
        DesignHarness.render("engine-picker", 360, heightDp = 260, fontScale = 1.3f) {
            Box(Modifier.fillMaxWidth()) { HomeEnginePicker(engines = engines, selectedEngine = "mpv", onSelectEngine = {}) }
        }
    }

    /** The join key with the shortcut saver beside it, and unfolded by a tap on the saver. */
    @Test
    fun joinRowUnfoldsTheShortcutSaverOverTheJoinKey() {
        DesignHarness.initDatastore()
        for (w in listOf(284, 324, 393)) {
            val density = Density(2f, 1f)
            val scene = DesignHarness.onUiThread { ImageComposeScene(width = w * 2, height = 200, density = density, coroutineContext = Dispatchers.Main.immediate) {
                DesignHarness.Frame(TRINITY) {
                    Box(Modifier.size(w.dp, 100.dp).padding(Space.gutter)) { JoinRow(onJoin = {}, onSaveShortcut = {}) }
                }
            } }
            try {
                var image = DesignHarness.onUiThread { scene.render(0L) }
                repeat(10) { i -> image = DesignHarness.onUiThread { scene.render((i + 1) * 16_000_000L) } }
                File(DesignHarness.outDir, "join-row-${w}dp.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
                // The saver is a 48dp square at the row's end, inside the gutter.
                val x = (w - Space.gutter.value - 24f) * 2f
                val y = (Space.gutter.value + 24f) * 2f
                DesignHarness.onUiThread {
                    scene.sendPointerEvent(PointerEventType.Press, Offset(x, y))
                    scene.sendPointerEvent(PointerEventType.Release, Offset(x, y))
                }
                repeat(30) { i -> image = DesignHarness.onUiThread { scene.render((i + 11) * 16_000_000L) } }
                File(DesignHarness.outDir, "join-row-${w}dp-open.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
                val texts = mutableListOf<TextLayoutResult>()
                DesignHarness.onUiThread { scene.semanticsOwners.forEach { collect(it.unmergedRootSemanticsNode, texts, mutableListOf()) } }
                val explanation = texts.firstOrNull { it.layoutInput.text.text.startsWith("Save this setup") }
                assertNotNull(explanation, "join-row-${w}dp-open: the saver did not unfold")
                if (w >= 324) assertTrue(!explanation.isLineEllipsized(0), "join-row-${w}dp-open: the explanation is cut")
            } finally {
                DesignHarness.onUiThread { scene.close() }
            }
        }
    }

    private companion object {
        const val JOIN_LABEL = "Join room"
    }
}

/** An engine with no implementation behind it, so the picker can be drawn without a device. */
internal class FakeEngine(
    override val name: String,
    override val img: DrawableResource,
    override val isDefault: Boolean = false,
    override val isSystem: Boolean = false,
    override val isExperimental: Boolean = false,
) : PlayerEngine {
    override val isAvailable: Boolean get() = true
    override fun createImpl(viewmodel: RoomViewmodel): PlayerImpl = error("golden")
}
