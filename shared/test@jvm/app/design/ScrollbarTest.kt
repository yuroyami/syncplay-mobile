package app.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import app.preferences.settings.SettingsScreenUI
import app.theme.Space
import app.theme.Type
import app.uicomponents.controls.Text
import app.uicomponents.frames.PanelFrame
import app.uicomponents.frames.ScrollbarHost
import app.uicomponents.LocalWidthClass
import app.uicomponents.WidthClass
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * On desktop, a scrolling panel has a scrollbar on its end edge, and a mouse can drag it. A drag
 * that starts at the top of the bar and moves down scrolls the content down. Dragging the content
 * itself at the top would do nothing, so a scroll here proves that the bar is there.
 */
class ScrollbarTest {

    /** The bar's middle, in scene pixels, for a container [widthDp] wide. Scene pixels are dp times two. */
    private fun barX(widthDp: Int) = (widthDp - 4) * 2f

    @Test
    fun aLongPanelScrollsWhenItsBarIsDragged() = DesignHarness.drive(widthDp = 320, heightDp = 300, television = false, content = {
        PanelFrame(title = "Long", modifier = Modifier.fillMaxWidth().height(300.dp)) {
            repeat(40) { Text("Row $it", style = Type.label, modifier = Modifier.height(40.dp)) }
        }
    }) {
        val before = topOf("Row 0")
        // The body starts under the header row, so the bar's top is just below it.
        val barTop = (Space.row.value + 8f) * 2f
        mouseDrag(from = Offset(barX(320), barTop), to = Offset(barX(320), barTop + 200f))
        assertTrue(topOf("Row 0") < before - 100f, "Dragging the bar scrolls the panel")
    }

    @Test
    fun aLongListScrollsWhenItsBarIsDragged() = DesignHarness.drive(widthDp = 320, heightDp = 300, television = false, content = {
        val list = rememberLazyListState()
        ScrollbarHost(list, Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), state = list) {
                items(60) { Text("Item $it", style = Type.label, modifier = Modifier.height(40.dp)) }
            }
        }
    }) {
        mouseDrag(from = Offset(barX(320), 16f), to = Offset(barX(320), 216f))
        assertTrue(textNodes().none { it == "Item 0" }, "Dragging the bar scrolls the list past its first item")
    }

    /** The bar must not change the panel's size: a short panel stays as tall as its content. */
    @Test
    fun aShortPanelKeepsTheHeightOfItsContent() = DesignHarness.drive(widthDp = 320, heightDp = 300, television = false, content = {
        Column {
            PanelFrame(title = "Short", modifier = Modifier.fillMaxWidth()) {
                repeat(2) { Text("Row $it", style = Type.label, modifier = Modifier.height(40.dp)) }
            }
            Text("Below", style = Type.label)
        }
    }) {
        // The header, its line and two 40dp rows come to about 123dp, far from the 300dp window.
        assertTrue(topOf("Below") < 150f * 2f, "The panel keeps the height of its content: ${topOf("Below")}px")
    }

    /** The settings on a window shorter than their content, in one pane: the category list. */
    @Test
    fun theSettingsListScrollsWhenItsBarIsDragged() = DesignHarness.drive(widthDp = 360, heightDp = 240, television = false, content = {
        SettingsScreenUI(categoryKey = null)
    }) {
        dragsTheBarAt(barX(360), minX = 0f)
    }

    /** The same in two panes: the open category on the right and the category list on the left. */
    @Test
    fun bothSettingsPanesScrollWhenTheirBarsAreDragged() = DesignHarness.drive(widthDp = 900, heightDp = 240, television = false, content = {
        CompositionLocalProvider(LocalWidthClass provides WidthClass.Expanded) { SettingsScreenUI(categoryKey = null) }
    }) {
        // The category list is 280dp wide, and a 1dp line separates the panes.
        dragsTheBarAt(barX(900), minX = 281f * 2f)
        dragsTheBarAt(barX(280), minX = 0f, maxX = 280f * 2f)
    }

    /**
     * Drags the bar at [x] from the top of the screen's body, and checks that the first text in
     * the pane between [minX] and [maxX] moves up. The body starts under the 54dp title bar.
     */
    private fun DesignHarness.Driver.dragsTheBarAt(x: Float, minX: Float, maxX: Float = Float.MAX_VALUE) {
        val bodyTop = Space.bar.value * 2f
        val (text, before) = nodes()
            .filter { node -> node.config.getOrNull(SemanticsProperties.Text) != null }
            .map { node -> node.config[SemanticsProperties.Text].joinToString { it.text } to node.positionInRoot }
            .filter { (_, at) -> at.x in minX..maxX && at.y > bodyTop }
            .minBy { (_, at) -> at.y }
            .let { (text, at) -> text to at.y }
        mouseDrag(from = Offset(x, bodyTop + 12f), to = Offset(x, bodyTop + 212f))
        val after = nodes().first { node -> node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } == text }.positionInRoot.y
        assertTrue(after < before - 100f, "Dragging the bar at x=$x scrolls \"$text\" up: from $before to $after")
    }

    private fun DesignHarness.Driver.nodes(): List<SemanticsNode> = DesignHarness.onUiThread {
        fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::walk)
        scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }
    }

    private fun DesignHarness.Driver.textNodes(): List<String> =
        nodes().flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }

    private fun DesignHarness.Driver.topOf(text: String): Float =
        nodes().first { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true }.positionInRoot.y
}
