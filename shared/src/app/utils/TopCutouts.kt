package app.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The camera cutouts on the top edge of the window, as horizontal spans in window pixels. The status
 * strip of the portrait room keeps its text out of them. Android reports them from the window
 * insets. The other platforms report none, and the strip then assumes a notch in the middle.
 */
object TopCutouts {
    private val current = MutableStateFlow<List<IntRange>>(emptyList())

    val spans: StateFlow<List<IntRange>> = current.asStateFlow()

    fun report(spans: List<IntRange>) {
        current.value = spans
    }
}
