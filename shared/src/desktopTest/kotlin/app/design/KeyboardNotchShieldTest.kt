package app.design

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalPlatformWindowInsets
import androidx.compose.ui.platform.PlatformInsets
import androidx.compose.ui.platform.PlatformWindowInsets
import androidx.compose.ui.platform.PlatformWindowInsetsProviderNode
import androidx.compose.ui.unit.Density
import app.room.ui.misc.KeyboardNotchShield
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The room takes a tap anywhere as "outside the keyboard" and closes the keyboard. The keyboard
 * stops short of a camera notch, so a thumb that lands on that strip while typing would close it.
 * With the keyboard open, the shield must consume a tap on the notch columns and nothing else.
 * With the keyboard closed, the strip must take taps again.
 *
 * The window is 800 by 600 pixels. Insets reach Compose in two ways, a composition local and a
 * layout node, and the test feeds both, as in [HomeFocusTest].
 */
@OptIn(InternalComposeUiApi::class)
class KeyboardNotchShieldTest {

    private val strip = 80
    private val onStrip = Offset(20f, 300f)
    private val onEndStrip = Offset(780f, 300f)
    private val onRoom = Offset(400f, 300f)

    @Test
    fun aTapOnTheNotchStripIsSwallowedWhileTheKeyboardIsOpen() {
        withRoom(PlatformInsets(getLeft = { strip })) { room ->
            room.keyboard(240)
            room.tap(onStrip)
            assertEquals(0, room.taps)
        }
    }

    @Test
    fun theRestOfTheRoomStillTakesTheTap() {
        withRoom(PlatformInsets(getLeft = { strip })) { room ->
            room.keyboard(240)
            room.tap(onRoom)
            assertEquals(1, room.taps)
        }
    }

    @Test
    fun theStripIsLiveWhileTheKeyboardIsClosed() {
        withRoom(PlatformInsets(getLeft = { strip })) { room ->
            room.tap(onStrip)
            assertEquals(1, room.taps)
            room.keyboard(240)
            room.tap(onStrip)
            assertEquals(1, room.taps)
            room.keyboard(0)
            room.tap(onStrip)
            assertEquals(2, room.taps)
        }
    }

    @Test
    fun aNotchOnTheEndEdgeIsCoveredToo() {
        withRoom(PlatformInsets(getRight = { strip })) { room ->
            room.keyboard(240)
            room.tap(onEndStrip)
            assertEquals(0, room.taps)
            room.tap(onStrip)
            assertEquals(1, room.taps)
        }
    }

    @Test
    fun aNotchOnTheTopEdgeLeavesTheSidesAlone() {
        withRoom(PlatformInsets(getTop = { strip })) { room ->
            room.keyboard(240)
            room.tap(onStrip)
            room.tap(onEndStrip)
            assertEquals(2, room.taps)
        }
    }

    /** A room stand-in: one tap handler under the shield, counting what gets through. */
    private class Room(private val scene: ImageComposeScene, private val keyboardPx: MutableState<Int>) {
        var taps = 0
        private var frame = 0L

        fun keyboard(px: Int) {
            keyboardPx.value = px
            settle()
        }

        fun tap(at: Offset) {
            DesignHarness.onUiThread {
                scene.sendPointerEvent(PointerEventType.Press, at)
                scene.sendPointerEvent(PointerEventType.Release, at)
            }
            settle()
        }

        fun settle(frames: Int = 10) {
            repeat(frames) { DesignHarness.onUiThread { scene.render(frame++ * 16_000_000L) } }
        }
    }

    private fun withRoom(cutout: PlatformInsets, block: (Room) -> Unit) {
        val keyboardPx = mutableStateOf(0)
        val insets = object : PlatformWindowInsets {
            override val displayCutout: PlatformInsets = cutout
            override val ime: PlatformInsets = PlatformInsets(getBottom = { keyboardPx.value })
        }
        var room: Room? = null
        val scene = DesignHarness.onUiThread {
            ImageComposeScene(width = 800, height = 600, density = Density(2f), coroutineContext = Dispatchers.Main.immediate) {
                CompositionLocalProvider(LocalPlatformWindowInsets provides insets) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .then(WindowInsetsElement(insets))
                            .pointerInput(Unit) { detectTapGestures { room?.let { it.taps++ } } },
                    ) {
                        // The same read the room makes: the keyboard is open while its inset is.
                        KeyboardNotchShield(keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0)
                    }
                }
            }
        }
        try {
            val stand = Room(scene, keyboardPx)
            room = stand
            stand.settle()
            block(stand)
        } finally {
            DesignHarness.onUiThread { scene.close() }
        }
    }

    private class WindowInsetsNode(var insets: PlatformWindowInsets) : PlatformWindowInsetsProviderNode() {
        override fun calculatePlatformInsets(ancestorWindowInsets: PlatformWindowInsets): PlatformWindowInsets = insets
    }

    private data class WindowInsetsElement(val insets: PlatformWindowInsets) : ModifierNodeElement<WindowInsetsNode>() {
        override fun create() = WindowInsetsNode(insets)
        override fun update(node: WindowInsetsNode) {
            node.insets = insets
        }
    }
}
