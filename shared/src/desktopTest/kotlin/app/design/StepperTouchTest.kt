package app.design

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import app.uicomponents.controls.ListRow
import app.uicomponents.controls.RowGap
import app.uicomponents.controls.RowLabel
import app.uicomponents.controls.Stepper
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A stepper arrow takes a finger across 48dp in both directions, the platform minimum. In a dense
 * list the rows above and below take their own taps, so the arrow cannot borrow their height.
 */
class StepperTouchTest {
    @Test
    fun eachArrowTakesATapAcross48dpInBothDirections() {
        var index by mutableStateOf(1)
        var steps = 0
        var neighbourTaps = 0
        DesignHarness.drive(widthDp = 360, television = false, content = {
            Column {
                ListRow(onClick = { neighbourTaps++ }) { RowLabel("Above") }
                ListRow {
                    RowLabel("Pattern")
                    RowGap()
                    Stepper(listOf("One", "Two", "Three"), index, { index = it; steps++ }, wrap = true)
                }
                ListRow(onClick = { neighbourTaps++ }) { RowLabel("Below") }
            }
        }) {
            // The harness renders at density 2, so 23dp is 46px. Compose gives a tap that misses every box
            // to the nearest small target, and a 42dp row reaches 3dp past its edge. So the corners stop
            // 4dp short of the rows above and below.
            val side = 23 * 2f
            val corner = 20 * 2f
            val taps = listOf(
                0f to 0f, -side to 0f, side to 0f, 0f to -side, 0f to side,
                -side to -corner, side to -corner, -side to corner, side to corner,
            )
            val misses = mutableListOf<String>()
            for (name in listOf("Previous", "Next")) {
                val centre = arrow(name).boundsInWindow.center
                for ((dx, dy) in taps) {
                    val before = steps
                    tap(Offset(centre.x + dx, centre.y + dy))
                    if (steps != before + 1) misses += "$name at $dx, $dy px"
                }
            }
            assertEquals(emptyList(), misses, "these taps missed the arrow")
            assertEquals(0, neighbourTaps, "a tap meant for an arrow reached a neighbouring row")
        }
    }

    private fun DesignHarness.Driver.arrow(name: String): SemanticsNode = DesignHarness.onUiThread {
        scene.semanticsOwners.firstNotNullOf { find(it.unmergedRootSemanticsNode, name) }
    }

    private fun find(node: SemanticsNode, name: String): SemanticsNode? {
        if (node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(name) == true) return node
        return node.children.firstNotNullOfOrNull { find(it, name) }
    }
}
