package app.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import app.Screen
import app.home.HomeScreenUI
import app.home.HomeViewmodel
import app.home.JoinConfig
import app.home.RecentJoin
import app.i18n.EnAppStrings
import app.preferences.Preferences
import app.preferences.set
import app.preferences.value
import app.theme.Palette
import app.theme.TRINITY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The recent rooms open in a layer over the home form, right above the join row. The layer takes
 * no room in the form, so the join row (the join key and the recent rooms key) keeps its place and
 * its size before, during and after the open and the close. The rest of the home screen stays
 * where it is too. The checks run on a phone, on the short 800x312 window, at a text scale of 1.3
 * and in a right-to-left layout, by touch and by keyboard.
 */
class RecentRoomsLayerTest {

    private val recents = listOf(
        RecentJoin("yuroyami", "movie-night", "syncplay.pl", 8997),
        RecentJoin("yuroyami", "anime-club", "192.168.1.20", 8999, hasPassword = true),
        RecentJoin("mina", "sunday-films", "watch.example.org", 8999),
    )

    /** The spoken name of the first entry of the layer. */
    private val firstEntry = EnAppStrings.homeRecentJoin(recents[0].room, recents[0].server)

    private var before: List<Any?> = emptyList()

    @BeforeTest
    fun fill() {
        DesignHarness.initDatastore()
        before = listOf(
            Preferences.NEVER_SHOW_TIPS.value(), Preferences.REMEMBER_INFO.value(),
            Preferences.RECENT_JOINS.value(), Preferences.JOIN_CONFIG.value(),
        )
        val join = Json.encodeToString(JoinConfig(user = "yuroyami", room = "movie-night", ip = "syncplay.pl", port = 8997))
        val list = Json.encodeToString(recents)
        runBlocking {
            Preferences.NEVER_SHOW_TIPS.set(true)
            Preferences.REMEMBER_INFO.set(true)
            Preferences.JOIN_CONFIG.set(join)
            Preferences.RECENT_JOINS.set(list)
        }
        await { Preferences.RECENT_JOINS.value() == list && Preferences.JOIN_CONFIG.value() == join && Preferences.NEVER_SHOW_TIPS.value() }
    }

    // The store is shared by the whole run, so the next class must find it as it was.
    @AfterTest
    fun restore() {
        runBlocking {
            Preferences.NEVER_SHOW_TIPS.set(before[0] as Boolean)
            Preferences.REMEMBER_INFO.set(before[1] as Boolean)
            Preferences.RECENT_JOINS.set(before[2] as String? ?: "[]")
            (before[3] as String?)?.let { Preferences.JOIN_CONFIG.set(it) }
        }
    }

    /** Waits up to a second for a write to reach the snapshot that the app reads. */
    private fun await(condition: () -> Boolean) {
        var tries = 0
        while (!condition() && tries++ < 50) Thread.sleep(20)
        assertTrue(condition(), "the datastore never reached the expected state")
    }

    /** One node of the screen: what it says, and where it is in scene pixels. */
    private data class Part(val label: String, val bounds: Rect)

    private fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::walk)

    private fun DesignHarness.Driver.nodes(): List<SemanticsNode> = DesignHarness.onUiThread {
        scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }
    }

    private fun labelOf(node: SemanticsNode): String? {
        val config = node.config
        val words = config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
            config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            listOfNotNull(config.getOrNull(SemanticsProperties.EditableText)?.text)
        return words.joinToString(" | ").takeIf { it.isNotBlank() }
    }

    /** Every node that says something, with its place: the home screen, and the layer while it shows. */
    private fun DesignHarness.Driver.parts(): List<Part> =
        DesignHarness.onUiThread { nodes().mapNotNull { node -> labelOf(node)?.let { Part(it, node.boundsInWindow) } } }

    private fun DesignHarness.Driver.described(name: String): SemanticsNode? =
        nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(name) == true }

    private fun readsJoin(node: SemanticsNode): Boolean =
        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == EnAppStrings.connectButtonJoin } == true || node.children.any(::readsJoin)

    /** The join row: the join key and the recent rooms key, as laid out. */
    private fun DesignHarness.Driver.joinRow(): List<Rect> = DesignHarness.onUiThread {
        val join = assertNotNull(nodes().firstOrNull { SemanticsActions.OnClick in it.config && readsJoin(it) }, "The join key shows")
        val key = assertNotNull(described(EnAppStrings.homeRecentTitle), "The recent rooms key shows")
        listOf(join.boundsInWindow, key.boundsInWindow)
    }

    /** The row of the layer: the one node that scrolls sideways. */
    private fun DesignHarness.Driver.layer(): Rect? = DesignHarness.onUiThread {
        nodes().firstOrNull { SemanticsProperties.HorizontalScrollAxisRange in it.config }?.boundsInWindow
    }

    private fun DesignHarness.Driver.focusedLabel(): String? = DesignHarness.onUiThread {
        nodes().lastOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }?.let(::labelOf)
    }

    /** A finger tap with no frame after it, so the frames that follow can be checked one by one. */
    private fun DesignHarness.Driver.touch(position: Offset) = DesignHarness.onUiThread {
        scene.sendPointerEvent(PointerEventType.Press, position, type = PointerType.Touch)
        scene.sendPointerEvent(PointerEventType.Release, position, type = PointerType.Touch)
    }

    /** The home screen in a window of [widthDp] x [heightDp] dp, driven by touch, or by keyboard on a [television]. */
    private fun home(
        widthDp: Int,
        heightDp: Int,
        fontScale: Float = 1f,
        rtl: Boolean = false,
        television: Boolean = false,
        check: DesignHarness.Driver.() -> Unit,
    ) {
        val driver = DesignHarness.Driver()
        val input = DesignHarness.FixedInputMode(if (television) InputMode.Keyboard else InputMode.Touch)
        val scene = DesignHarness.onUiThread {
            ImageComposeScene(widthDp * 2, heightDp * 2, Density(2f, fontScale), coroutineContext = Dispatchers.Main.immediate) {
                DesignHarness.Frame(TRINITY, television = television) {
                    CompositionLocalProvider(
                        LocalInputModeManager provides input,
                        LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    ) {
                        val focusManager = LocalFocusManager.current
                        SideEffect { driver.focusManager = focusManager }
                        Box(Modifier.size(widthDp.dp, heightDp.dp)) { HomeScreenUI(remember { HomeViewmodel(mutableStateListOf(Screen.Home)) }) }
                    }
                }
            }
        }
        try {
            driver.scene = scene
            driver.frames(30)
            driver.check()
        } finally {
            DesignHarness.onUiThread { scene.close() }
        }
    }

    /**
     * Scrolls the form until the whole join row shows in a window [heightPx] tall, as a person does
     * before a tap. A click gives a key focus, and focus scrolls a cut key into view by itself.
     */
    private fun DesignHarness.Driver.showWholeJoinRow(heightPx: Float) {
        val cut = DesignHarness.onUiThread {
            val join = nodes().first { SemanticsActions.OnClick in it.config && readsJoin(it) }
            Rect(join.positionInRoot, join.size.toSize()).bottom - heightPx
        }
        if (cut <= 0f) return
        DesignHarness.onUiThread {
            val form = nodes().first { SemanticsProperties.VerticalScrollAxisRange in it.config }
            form.config[SemanticsActions.ScrollBy].action?.invoke(0f, cut + 2f)
        }
        // The scroll animates, and a tap during it only stops it, so let it end first.
        frames(60)
    }

    private fun assertSameParts(expected: List<Part>, actual: List<Part>, moment: String) {
        val missing = expected.filter { it !in actual }
        assertTrue(missing.isEmpty(), "$moment, these parts of the home screen moved or changed size: $missing")
    }

    @Test
    fun theJoinRowAndTheFormKeepTheirBoundsWhileTheLayerOpensAndCloses() {
        val base = Palette.from(TRINITY.dynamicScheme, TRINITY)
        val ground = base.panel.compositeOver(base.ground).toArgb()
        for ((w, h) in listOf(390 to 763, 360 to 568, 800 to 312)) for (scale in listOf(1f, 1.3f)) for (rtl in listOf(false, true)) {
            val name = "${w}x$h" + (if (scale != 1f) "-fs$scale" else "") + (if (rtl) "-rtl" else "")
            home(w, h, scale, rtl) {
                showWholeJoinRow(h * 2f)
                val row = joinRow()
                val closed = parts()
                assertNull(layer(), "$name: the layer starts closed")

                // A tap on the recent rooms key opens the layer. Every frame of the entrance is checked.
                touch(row[1].center)
                repeat(24) {
                    frames(1)
                    assertEquals(row, joinRow(), "$name: the join row moved on frame ${it + 1} of the opening")
                    assertSameParts(closed, parts(), "$name, frame ${it + 1} of the opening")
                }
                val open = assertNotNull(layer(), "$name: the key opens the layer")
                assertTrue(parts().any { it.label == firstEntry }, "$name: the layer shows the first recent room")

                // Right above the join row, and as wide as it.
                val gap = row.minOf { it.top } - open.bottom
                assertTrue(gap in 0f..13f, "$name: the layer ends ${gap / 2f} dp above the join row, not 6 dp")
                assertEquals(row.minOf { it.left }, open.left, 1f, "$name: the layer starts where the join row starts")
                assertEquals(row.maxOf { it.right }, open.right, 1f, "$name: the layer ends where the join row ends")

                // An opaque ground: inside the padding of the layer, the pixel is the ground colour itself.
                val image = ImageIO.read(save("home-recent-layer-$name-open"))
                val x = if (rtl) (open.right - 6f).toInt() else (open.left + 6f).toInt()
                val seen = image.getRGB(x, open.center.y.toInt())
                assertTrue(sameColour(ground, seen), "$name: the ground of the layer is ${hex(seen)}, not the opaque ${hex(ground)}")

                // A second tap on the key closes the layer, and nothing moves on the way out either.
                touch(row[1].center)
                repeat(16) {
                    frames(1)
                    assertEquals(row, joinRow(), "$name: the join row moved on frame ${it + 1} of the closing")
                    assertSameParts(closed, parts(), "$name, frame ${it + 1} of the closing")
                }
                frames(10)
                assertNull(layer(), "$name: a tap on the key closes the layer")
                assertEquals(closed, parts(), "$name: after the close, the home screen is as it was")
            }
        }
    }

    @Test
    fun aTapOutsideTheLayerClosesIt() = home(390, 763) {
        val row = joinRow()
        tap(row[1].center)
        frames(20)
        assertNotNull(layer(), "The key opens the layer")
        // On the top bar, away from the layer and from every key of the form.
        tap(Offset(390f, 40f))
        frames(20)
        assertNull(layer(), "A tap outside closes the layer")
        assertEquals(row, joinRow(), "The join row is where it was")
    }

    @Test
    fun underAKeyboardFocusEntersTheLayerAndEscapeGivesItBackToTheKey() = home(390, 763, television = true) {
        val key = assertNotNull(described(EnAppStrings.homeRecentTitle))
        DesignHarness.onUiThread { key.config[SemanticsActions.RequestFocus].action?.invoke() }
        frames(30)
        assertEquals(EnAppStrings.homeRecentTitle, focusedLabel(), "The recent rooms key takes focus")
        // Taken once the key has focus: the first field gives up focus and its help line.
        val row = joinRow()
        press(Key.Enter)
        // Focus moves in over a few tries, 60 ms apart in real time.
        repeat(8) { Thread.sleep(70); frames(3) }
        assertNotNull(layer(), "Enter on the key opens the layer")
        assertEquals(firstEntry, focusedLabel(), "Focus enters the first recent room")
        assertEquals(row, joinRow(), "The join row is where it was")
        press(Key.Escape)
        repeat(8) { Thread.sleep(70); frames(3) }
        assertNull(layer(), "Escape closes the layer")
        assertEquals(EnAppStrings.homeRecentTitle, focusedLabel(), "Focus goes back to the recent rooms key")
        assertEquals(row, joinRow(), "The join row is still where it was")
    }

    private fun sameColour(a: Int, b: Int): Boolean =
        (0..24 step 8).all { shift -> abs(((a shr shift) and 0xFF) - ((b shr shift) and 0xFF)) <= 1 }

    private fun hex(argb: Int) = "#" + argb.toUInt().toString(16).padStart(8, '0')
}
