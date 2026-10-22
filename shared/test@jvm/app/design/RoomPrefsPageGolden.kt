package app.design

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.toSize
import app.preferences.Preferences
import app.preferences.flow
import app.preferences.set
import app.preferences.settings.InlineEditorHost
import app.preferences.settings.InlineEditorPage
import app.preferences.settings.LocalInlineEditor
import app.preferences.settings.SettingRow
import app.preferences.value
import app.room.ui.rightcards.InRoomNestedPage
import app.theme.Space
import app.theme.TRINITY
import app.uicomponents.controls.BackGlyph
import app.uicomponents.controls.GlyphButton
import app.uicomponents.controls.hex
import app.uicomponents.frames.PanelFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The in-room settings panel, driven through its real rows, so that each page change also
 * exercises the lifetimes of those rows.
 */
class RoomPrefsPageGolden {
    @Test
    fun chatColoursPageRendersInsideThePanel() {
        ColorPanel(320, 220, 1f).use { panel ->
            panel.openChatColors()
            panel.save("chat-colours")
            assertTrue(panel.hasActiveScroll(), "The list of chat colors must retain scrolling")
            panel.scrollToBottom()
            panel.assertTextVisible(LAST_COLOR)
            panel.tapBack()
            assertEquals(0, panel.depth)
            panel.assertTextVisible(CHAT_COLORS)
        }
    }

    @Test
    fun colorPickerFitsTheRemainingCardHeightAndKeepsResetAndBackVisible() {
        for ((width, height) in listOf(320 to 220, 320 to 260, 420 to 340, 360 to 640)) {
            for (scale in listOf(1f, 2f)) {
                ColorPanel(width, height, scale).use { panel ->
                    panel.openChatColors()
                    panel.tapText(TIMESTAMP_COLOR)
                    assertEquals(2, panel.depth)
                    panel.assertPickerFits()
                    assertFalse(panel.hasActiveScroll(), "The fitted color editor must not require vertical scrolling")
                    panel.assertTextVisible(RESET)
                    panel.assertBackVisible()
                    panel.save("color-picker")
                    panel.tapBack()
                    assertEquals(1, panel.depth)
                    panel.assertTextVisible(TIMESTAMP_COLOR)
                    panel.tapBack()
                    assertEquals(0, panel.depth)
                }
            }
        }
    }

    @Test
    fun colorChangesAndResetPersistAfterTheLaunchingRowHasBeenDisposed() {
        DesignHarness.initDatastore()
        val pref = Preferences.COLOR_TIMESTAMP
        runBlocking {
            pref.set(pref.default)
            withTimeout(2000) { pref.flow().first { it == pref.default } }
        }
        try {
            ColorPanel(320, 260, 1f).use { panel ->
                panel.openChatColors()
                panel.tapText(TIMESTAMP_COLOR)
                panel.settleStorage()
                assertEquals(pref.default, pref.value(), "Opening a picker must not change the colour")
                panel.tapPicker()
                panel.awaitPreference { it != pref.default }
                val selected = pref.value()
                panel.tapAlphaTrack()
                panel.awaitPreference { it != selected }
                val withAlpha = pref.value()
                val alpha = withAlpha ushr 24
                assertTrue(alpha in 1..254, "The opacity track must choose a translucent color")
                panel.tapPicker(yFraction = 0.25f)
                panel.awaitPreference { (it and 0xFFFFFF) != (withAlpha and 0xFFFFFF) }
                assertEquals(alpha, pref.value() ushr 24, "Picking brightness must preserve the chosen opacity")
                panel.save("color-picker-changed")

                val lastPick = panel.tapPickerAndBackImmediately()
                assertEquals(1, panel.depth)
                panel.awaitPreference { it == lastPick }
                assertEquals(alpha, lastPick ushr 24)
                panel.tapText(TIMESTAMP_COLOR)
                panel.tapText(RESET)
                panel.awaitPreference { it == pref.default }
                panel.settleStorage()
                assertEquals(pref.default, pref.value(), "Reset must survive pending picker callbacks")
                assertTrue(selected != pref.default)
                panel.save("color-picker-reset")
                panel.tapBack()
                panel.assertTextVisible(Color(pref.default).hex())
                assertEquals(pref.default, pref.value())
            }
        } finally {
            runBlocking { pref.set(pref.default) }
        }
    }

    private class ColorPanel(val width: Int, val height: Int, val fontScale: Float) : AutoCloseable {
        private val pages = mutableStateListOf<InlineEditorPage>()
        val depth: Int get() = pages.size
        private var frame = 0L
        private val scene: ImageComposeScene

        init {
            DesignHarness.initDatastore()
            scene = ImageComposeScene(width = width * 2, height = height * 2, density = Density(2f, fontScale)) {
                DesignHarness.Frame(TRINITY, overVideo = true) {
                    val host = remember { InlineEditorHost { page -> pages.add(page) } }
                    val page = pages.lastOrNull()
                    PanelFrame(
                        title = page?.title ?: "Chat",
                        modifier = Modifier.fillMaxSize(),
                        scrollable = page?.scrollable ?: true,
                        actions = {
                            if (pages.isNotEmpty()) {
                                GlyphButton(BackGlyph, name = "Back") { pages.removeAt(pages.lastIndex) }
                            }
                        },
                    ) {
                        CompositionLocalProvider(LocalInlineEditor provides host) {
                            if (page == null) Preferences.CHAT_COLORS_ENTRY.SettingRow()
                            else InRoomNestedPage(page.content)
                        }
                    }
                }
            }
        }

        private fun advance(frames: Int = 30) {
            repeat(frames) { scene.render(frame++ * 16_000_000L) }
        }

        fun openChatColors() {
            advance()
            tapText(CHAT_COLORS)
            assertEquals(1, depth)
        }

        private fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
        private fun allNodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { nodes(it.unmergedRootSemanticsNode) }

        private fun textNode(text: String): SemanticsNode = assertNotNull(allNodes().firstOrNull {
            it.config.getOrNull(SemanticsProperties.Text)?.any { label -> label.text == text } == true
        }, "No text: $text")

        private fun backNode(): SemanticsNode = assertNotNull(allNodes().firstOrNull {
            it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Back") == true &&
                it.config.getOrNull(SemanticsActions.OnClick) != null
        }, "No Back action")

        private fun pickerNode(): SemanticsNode = assertNotNull(allNodes().firstOrNull {
            it.config.getOrNull(SemanticsProperties.TestTag) == "inline-color-picker"
        }, "No color picker")

        private fun unclippedBounds(node: SemanticsNode) = Rect(node.positionInRoot, node.size.toSize())

        private fun assertVisible(node: SemanticsNode, label: String) {
            val bounds = unclippedBounds(node)
            assertTrue(bounds.width > 0 && bounds.height > 0, "$label has no size: $bounds")
            assertTrue(bounds.left >= -1 && bounds.top >= -1 && bounds.right <= width * 2 + 1 && bounds.bottom <= height * 2 + 1,
                "$label is outside ${width}x${height} at $fontScale text: $bounds")
        }

        fun assertTextVisible(text: String) = assertVisible(textNode(text), text)
        fun assertBackVisible() = assertVisible(backNode(), "Back")

        fun assertPickerFits() {
            val node = pickerNode()
            assertVisible(node, "Picker")
            val bounds = unclippedBounds(node)
            assertTrue(bounds.top >= Space.row.value * 2, "Picker crosses into the panel header: $bounds")
            assertTrue(bounds.height <= 260 * 2 + 1, "Picker exceeds its preferred maximum height")
            assertTrue(abs(bounds.width / bounds.height - 1.3125f) <= 0.035f,
                "Picker width must follow height with a square color field: $bounds")
            val clipped = node.boundsInRoot
            assertTrue(abs(bounds.width - clipped.width) <= 1 && abs(bounds.height - clipped.height) <= 1,
                "Picker is clipped by its parent: $bounds versus $clipped")
        }

        fun hasActiveScroll(): Boolean = allNodes().any {
            val range = it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
            range != null && range.maxValue() > 0.5f
        }

        fun scrollToBottom() {
            val action = assertNotNull(allNodes().firstNotNullOfOrNull {
                it.config.getOrNull(SemanticsActions.ScrollBy)?.action
            }, "No scrolling action")
            assertTrue(action(0f, 10_000f))
            advance()
        }

        private fun tapRaw(position: Offset) {
            scene.sendPointerEvent(PointerEventType.Press, position)
            scene.sendPointerEvent(PointerEventType.Release, position)
        }

        private fun tap(position: Offset) {
            tapRaw(position)
            advance()
        }

        fun tapText(text: String) {
            assertTextVisible(text)
            tap(unclippedBounds(textNode(text)).center)
        }

        fun tapBack() {
            assertBackVisible()
            tap(unclippedBounds(backNode()).center)
        }

        fun tapPicker(yFraction: Float = 0.7f) {
            assertPickerFits()
            val bounds = unclippedBounds(pickerNode())
            // The color field owns 8/10.5 of the width. Tap away from its initial thumb and tracks.
            tap(Offset(bounds.left + bounds.width * 0.3f, bounds.top + bounds.height * yFraction))
        }

        fun tapAlphaTrack() {
            assertPickerFits()
            val bounds = unclippedBounds(pickerNode())
            // The alpha track occupies the final 1/10.5 of the picker width.
            tap(Offset(bounds.left + bounds.width * (10f / 10.5f), bounds.top + bounds.height * 0.45f))
        }

        private fun previewArgb(): Int {
            val hex = assertNotNull(allNodes().firstNotNullOfOrNull { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull { it.text.startsWith('#') }?.text
            }, "No color preview").removePrefix("#")
            return if (hex.length == 6) hex.toInt(16) or 0xFF000000.toInt() else hex.toLong(16).toInt()
        }

        fun tapPickerAndBackImmediately(): Int {
            val before = previewArgb()
            val bounds = unclippedBounds(pickerNode())
            tapRaw(Offset(bounds.left + bounds.width * 0.55f, bounds.top + bounds.height * 0.5f))
            // Apply the picker's callback, then leave without advancing through the 50ms debounce.
            advance(3)
            val lastPick = previewArgb()
            assertTrue(lastPick != before, "The last tap must change the color before leaving")
            tapRaw(unclippedBounds(backNode()).center)
            advance(2)
            return lastPick
        }

        fun settleStorage() {
            repeat(5) {
                advance(5)
                runBlocking { delay(20) }
            }
            advance(5)
        }

        fun awaitPreference(predicate: (Int) -> Boolean) {
            repeat(50) {
                advance(5)
                if (predicate(Preferences.COLOR_TIMESTAMP.value())) return
                runBlocking { delay(20) }
            }
            assertTrue(predicate(Preferences.COLOR_TIMESTAMP.value()), "The inline color preference was not written")
        }

        fun save(name: String) {
            advance()
            val image = scene.render(frame++ * 16_000_000L)
            val file = File(DesignHarness.outDir, "room-prefs-$name-${width}x${height}dp-fs$fontScale.png")
            file.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            println("GOLDEN ${file.absolutePath}")
            val layouts = mutableListOf<TextLayoutResult>()
            allNodes().forEach { it.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts) }
            // A long panel title may ellipsise; all editor labels below the header must stay whole.
            if (depth == 2) {
                DesignHarness.Result(file, height, layouts.filterNot { it.layoutInput.text.text == pages.last().title }).assertAllTextFits()
            }
        }

        override fun close() = scene.close()
    }

    private companion object {
        const val CHAT_COLORS = "Chat colors"
        const val TIMESTAMP_COLOR = "Timestamp text color"
        const val LAST_COLOR = "Error message text color"
        const val RESET = "Reset Default"
    }
}
