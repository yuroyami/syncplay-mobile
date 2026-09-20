package app.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.home.components.HomeEnginePicker
import app.theme.TRINITY
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.EncodedImageFormat
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.exoplayer
import syncplaymobile.shared.generated.resources.kiteplayer
import syncplaymobile.shared.generated.resources.mpv
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The engine card under the picker. Opening it is one motion: the picker grows to the card's
 * final height and stops there. It used to measure the words against the width it was
 * animating through, so a short card overshot its height and shrank back.
 */
class EngineCardTest {
    @Test
    fun theCardGrowsToItsHeightOnceAndNeverPastIt() {
        DesignHarness.initDatastore()
        val engines = listOf(
            FakeEngine("ExoPlayer", Res.drawable.exoplayer, isSystem = true),
            FakeEngine("mpv", Res.drawable.mpv, isDefault = true),
            FakeEngine("KitePlayer", Res.drawable.kiteplayer, isExperimental = true),
        )
        for (selected in listOf("mpv", "ExoPlayer", "KitePlayer")) {
            val w = 360
            var heightPx = 0
            val scene = DesignHarness.onUiThread {
                ImageComposeScene(width = w * 2, height = 1200, density = Density(2f, 1f), coroutineContext = Dispatchers.Main.immediate) {
                    DesignHarness.Frame(TRINITY) {
                        Box(Modifier.width(w.dp).onSizeChanged { heightPx = it.height }) {
                            HomeEnginePicker(engines = engines, selectedEngine = selected, onSelectEngine = {})
                        }
                    }
                }
            }
            try {
                var frame = 0L
                fun render() = DesignHarness.onUiThread { scene.render(frame++ * 16_000_000L) }
                repeat(10) { render() }
                val closedPx = heightPx
                val tip = DesignHarness.onUiThread { scene.semanticsOwners.firstNotNullOf { find(it.unmergedRootSemanticsNode, "Help") } }
                val centre = tip.boundsInWindow.center
                DesignHarness.onUiThread {
                    scene.sendPointerEvent(PointerEventType.Press, Offset(centre.x, centre.y))
                    scene.sendPointerEvent(PointerEventType.Release, Offset(centre.x, centre.y))
                }
                val heights = List(50) { render(); heightPx }
                File(DesignHarness.outDir, "engine-card-$selected-${w}dp.png")
                    .writeBytes(render().encodeToData(EncodedImageFormat.PNG)!!.bytes)
                val opened = heights.last()
                assertTrue(opened > closedPx, "$selected: the card did not open ($closedPx -> $opened)")
                assertEquals(opened, heights.max(), "$selected: the card grew past its final height: $heights")
                assertEquals(heights, heights.sorted(), "$selected: the card shrank while opening: $heights")
            } finally {
                DesignHarness.onUiThread { scene.close() }
            }
        }
    }

    private fun find(node: SemanticsNode, description: String): SemanticsNode? {
        if (node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(description) == true) return node
        return node.children.firstNotNullOfOrNull { find(it, description) }
    }
}
