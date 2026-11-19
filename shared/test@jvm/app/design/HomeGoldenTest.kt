package app.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.datastore.preferences.core.edit
import app.Screen
import app.home.HomeScreenUI
import app.home.HomeViewmodel
import app.home.JoinConfig
import app.home.JoinRow
import app.home.RecentJoin
import app.home.components.HomeEnginePicker
import app.i18n.EnAppStrings
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The home form across the windows that it meets on real devices. Every size here is a device's
 * screen with its system bars already taken off, because the harness has no insets of its own:
 * 360x640 with three-button navigation leaves 360x568, an iPhone 14 leaves 390x763, a Pixel 412x867.
 *
 * Two things are pinned. No label is ever cut at the default text size, and the whole join key is
 * on screen at rest wherever the form is meant to fit without scrolling. At rest means a touch
 * screen. In keyboard input mode the first field takes focus when the screen opens, and its help
 * line can push the join key under the edge of a short phone: the compact spacing keeps the key on
 * screen only while no help line shows. On most phones the join key stays on screen only because
 * the form tightens at short heights.
 */
class HomeGoldenTest {

    private class Home(val file: File, val textLayouts: List<TextLayoutResult>, val joinKey: Rect?, val heightPx: Int) {
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

        /** The whole join key is on screen. The check reads the key as laid out, so a key cut by the window edge fails. */
        fun assertJoinOnScreen() {
            val bounds = assertNotNull(joinKey, "${file.name}: no join key in the semantics tree")
            assertTrue(bounds.bottom <= heightPx + 1f && bounds.top >= 0f, "${file.name}: join key at ${bounds.top}..${bounds.bottom} px, window is $heightPx px tall")
        }
    }

    private fun collect(node: SemanticsNode, texts: MutableList<TextLayoutResult>, joinKeys: MutableList<Rect>) {
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(texts)
        // The whole key: the clickable node around the label, as laid out, before any clip.
        if (SemanticsActions.OnClick in node.config && readsJoin(node)) joinKeys += Rect(node.positionInRoot, node.size.toSize())
        node.children.forEach { collect(it, texts, joinKeys) }
    }

    private fun readsJoin(node: SemanticsNode): Boolean =
        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == JOIN_LABEL } == true || node.children.any(::readsJoin)

    private fun described(node: SemanticsNode, description: String): SemanticsNode? =
        node.takeIf { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(description) == true }
            ?: node.children.firstNotNullOfOrNull { described(it, description) }

    /**
     * One home screen in a window of exactly [w] x [h] dp, no insets, the bar included. With
     * [openRecents], a tap on the recent rooms key opens their row before the render is saved.
     *
     * The harness runs in keyboard input mode, as a television or a desktop does: the first field
     * takes focus when the screen opens and shows its help line. With [touch], the render is a touch
     * screen, where no field has focus at rest.
     */
    private fun renderHome(name: String, w: Int, h: Int, fontScale: Float = 1f, openRecents: Boolean = false, touch: Boolean = false): Home {
        DesignHarness.initDatastore()
        val densityValue = if (w > 1400 || h > 1400) 1f else 2f
        val density = Density(densityValue, fontScale)
        val scene = DesignHarness.onUiThread { ImageComposeScene(width = (w * densityValue).toInt(), height = (h * densityValue).toInt(), density = density, coroutineContext = Dispatchers.Main.immediate) {
            DesignHarness.Frame(TRINITY) {
                val input = if (touch) DesignHarness.FixedInputMode(InputMode.Touch) else LocalInputModeManager.current
                CompositionLocalProvider(LocalInputModeManager provides input) {
                    Box(Modifier.size(w.dp, h.dp)) {
                        HomeScreenUI(remember { HomeViewmodel(mutableStateListOf(Screen.Home)) })
                    }
                }
            }
        } }
        return try {
            var image = DesignHarness.onUiThread { scene.render(0L) }
            repeat(30) { i -> image = DesignHarness.onUiThread { scene.render((i + 1) * 16_000_000L) } }
            if (openRecents) {
                val key = DesignHarness.onUiThread { scene.semanticsOwners.firstNotNullOfOrNull { described(it.unmergedRootSemanticsNode, EnAppStrings.homeRecentTitle) } }
                assertNotNull(key, "home-$name-${w}x$h: no recent rooms key")
                val center = key.boundsInRoot.center
                DesignHarness.onUiThread {
                    scene.sendPointerEvent(PointerEventType.Press, center)
                    scene.sendPointerEvent(PointerEventType.Release, center)
                }
                repeat(30) { i -> image = DesignHarness.onUiThread { scene.render((i + 31) * 16_000_000L) } }
            }
            val suffix = (if (touch) "-touch" else "") + (if (openRecents) "-open" else "") + (if (fontScale != 1f) "-fs$fontScale" else "")
            val file = File(DesignHarness.outDir, "home-$name-${w}x${h}$suffix.png")
            image.encodeToData(EncodedImageFormat.PNG)?.bytes?.let(file::writeBytes)
            val texts = mutableListOf<TextLayoutResult>()
            val joinKeys = mutableListOf<Rect>()
            DesignHarness.onUiThread { scene.semanticsOwners.forEach { collect(it.unmergedRootSemanticsNode, texts, joinKeys) } }
            println("GOLDEN ${file.name} join=${joinKeys.firstOrNull()} window=${(h * densityValue).toInt()}px")
            Home(file, texts, joinKeys.firstOrNull(), (h * densityValue).toInt())
        } finally {
            DesignHarness.onUiThread { scene.close() }
        }
    }

    /** The tips setting before the test. [saveJoin] turns the tips off for the renders. */
    private var tipsBefore = false

    @BeforeTest
    fun rememberTips() {
        DesignHarness.initDatastore()
        tipsBefore = Preferences.NEVER_SHOW_TIPS.value()
    }

    // The store is shared by the whole run, so a later class must not find the tips off.
    @AfterTest
    fun restoreTips() {
        runBlocking { Preferences.NEVER_SHOW_TIPS.set(tipsBefore) }
    }

    private fun saveJoin(config: JoinConfig?) = runBlocking {
        Preferences.NEVER_SHOW_TIPS.set(true)
        // The store is shared by every test in this run, so a join made elsewhere must not add a row here.
        saveRecents(emptyList())
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

    private fun saveRecents(list: List<RecentJoin>) = runBlocking {
        val json = Json.encodeToString(list)
        Preferences.RECENT_JOINS.set(json)
        var tries = 0
        while (Preferences.RECENT_JOINS.value() != json && tries++ < 50) Thread.sleep(20)
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
            // Keyboard input mode: the first field has focus and shows its help line, which must fit too.
            renderHome("official", w, h).assertAllTextFits()
            // A touch screen at rest: no field has focus, and the whole join key is on screen.
            renderHome("official", w, h, touch = true).assertJoinOnScreen()
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
        // Custom mode has two more rows. These phones still show the join key at rest.
        for ((w, h) in listOf(390 to 763, 412 to 867, 800 to 312)) renderHome("custom", w, h).assertJoinOnScreen()
        saveJoin(hosted)
        for ((w, h) in listOf(360 to 568, 390 to 763, 800 to 312, 768 to 1000, 1280 to 720)) renderHome("host", w, h).assertAllTextFits()
        // The hosting panel outgrows a phone, but on a wide window the join key stays on screen.
        for ((w, h) in listOf(800 to 312, 1024 to 744, 1280 to 720)) renderHome("host", w, h).assertJoinOnScreen()
        saveJoin(official)
    }

    @Test
    fun recentRoomsOpenAboveTheJoinKeyAndKeepTheirWords() {
        saveJoin(official)
        saveRecents(
            listOf(
                RecentJoin("yuroyami", "movie-night", "syncplay.pl", 8997),
                RecentJoin("yuroyami", "anime-club", "192.168.1.20", 8999, hasPassword = true),
                RecentJoin("mina", "sunday-films", "watch.example.org", 8999),
            ),
        )
        for ((w, h) in listOf(360 to 708, 390 to 763, 412 to 867, 800 to 312, 1024 to 744, 1280 to 720)) {
            for (open in listOf(false, true)) {
                val home = renderHome("recent", w, h, openRecents = open)
                home.assertAllTextFits()
                // The open row grows above the join key, and the form scrolls so the whole key stays in view.
                home.assertJoinOnScreen()
            }
        }
        renderHome("recent", 390, 763, fontScale = 1.3f, openRecents = true).assertAllTextFits()
        saveJoin(official)
    }

    /**
     * The picker of the three Android engines (the video players that the app can drive), which
     * the desktop harness never composes on its own. 284dp is the narrowest column that the split
     * form makes, and 320dp phones get 284dp too.
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

    /**
     * The join row with both side keys. The shortcut key and the recent rooms key are 48dp squares
     * at the end, one gap apart, and the join key takes the rest of the row. Both outer edges sit on
     * the gutter.
     */
    @Test
    fun joinRowKeepsItsSideKeysFixedAndTheJoinKeyTakesTheRest() {
        DesignHarness.initDatastore()
        val recents = listOf(RecentJoin("yuroyami", "movie-night", "syncplay.pl", 8997))
        for (w in listOf(284, 324, 393)) {
            val px = 2f
            val scene = DesignHarness.onUiThread { ImageComposeScene(width = w * 2, height = 200, density = Density(px, 1f), coroutineContext = Dispatchers.Main.immediate) {
                DesignHarness.Frame(TRINITY) {
                    Box(Modifier.size(w.dp, 100.dp).padding(Space.gutter)) { JoinRow(onJoin = {}, onSaveShortcut = {}, recents = recents) }
                }
            } }
            try {
                var image = DesignHarness.onUiThread { scene.render(0L) }
                repeat(10) { i -> image = DesignHarness.onUiThread { scene.render((i + 1) * 16_000_000L) } }
                File(DesignHarness.outDir, "join-row-recents-${w}dp.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
                fun bounds(match: (SemanticsNode) -> Boolean): Rect = DesignHarness.onUiThread {
                    val node = scene.semanticsOwners.firstNotNullOf { find(it.unmergedRootSemanticsNode, match) }
                    Rect(node.positionInRoot, node.size.toSize())
                }
                val join = bounds { SemanticsActions.OnClick in it.config && readsJoin(it) }
                val shortcut = bounds { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(EnAppStrings.connectButtonSaveshortcut) == true }
                val recentKey = bounds { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(EnAppStrings.homeRecentTitle) == true }
                val label = "join-row-recents-${w}dp"
                val edge = Space.gutter.value * px
                val gap = Space.gap.value * px
                val side = Space.touchMin.value * px
                assertEquals(edge, join.left, 0.5f, "$label: the join key starts on the gutter")
                assertEquals(w * px - edge, recentKey.right, 0.5f, "$label: the recent rooms key ends on the gutter")
                assertEquals(side, shortcut.width, 0.5f, "$label: the shortcut key is a square")
                assertEquals(side, recentKey.width, 0.5f, "$label: the recent rooms key is a square")
                assertEquals(gap, shortcut.left - join.right, 0.5f, "$label: one gap between the join key and the shortcut key")
                assertEquals(gap, recentKey.left - shortcut.right, 0.5f, "$label: one gap between the side keys")
                println("JOINROW $label join=${join.left}..${join.right} shortcut=${shortcut.left}..${shortcut.right} recents=${recentKey.left}..${recentKey.right} width=${w * px}")
            } finally {
                DesignHarness.onUiThread { scene.close() }
            }
        }
    }

    private fun find(node: SemanticsNode, match: (SemanticsNode) -> Boolean): SemanticsNode? =
        node.takeIf(match) ?: node.children.firstNotNullOfOrNull { find(it, match) }

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
