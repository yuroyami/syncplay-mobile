package app.room.ui.rightcards

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A long press and drag that moves one playlist row. While the finger is down the panel reorders
 * its own copy of the rows, so the room hears a single move, when the finger lifts.
 */
@Stable
internal class PlaylistDragState(private val list: LazyListState, private val scope: CoroutineScope) {

    /** The rows as drawn during a drag, each with its index in the room's list. Null when idle. */
    var rows by mutableStateOf<List<Pair<Int, String>>?>(null)
        private set

    /** Where the dragged row sits in [rows] right now. */
    var draggedAt by mutableStateOf<Int?>(null)
        private set

    private var entriesAtStart: List<String> = emptyList()
    private var travelled by mutableFloatStateOf(0f)
    private var startOffset = 0

    /** How far the dragged row is drawn from its own slot, which keeps it under the finger. */
    val draggedOffset: Float
        get() = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggedAt }
            ?.let { startOffset + travelled - it.offset } ?: 0f

    /** Lifts the row under [at]. False when the press was not on a row. */
    fun start(at: Offset, entries: List<String>): Boolean {
        val hit = list.layoutInfo.visibleItemsInfo.firstOrNull { at.y.toInt() in it.offset..(it.offset + it.size) }
            ?: return false
        entriesAtStart = entries
        rows = entries.mapIndexed { index, entry -> index to entry }
        draggedAt = hit.index
        startOffset = hit.offset
        travelled = 0f
        return true
    }

    fun drag(dy: Float) {
        val current = rows ?: return
        val at = draggedAt ?: return
        travelled += dy
        val info = list.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == at } ?: return
        val top = item.offset + draggedOffset
        val middle = (top + item.size / 2f).toInt()
        val target = info.visibleItemsInfo.firstOrNull { it.index != at && middle in it.offset..(it.offset + it.size) }
        if (target != null) {
            // Swapping with the first visible row would carry the scroll position along with it.
            if (at == list.firstVisibleItemIndex || target.index == list.firstVisibleItemIndex) {
                list.requestScrollToItem(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)
            }
            rows = current.toMutableList().apply { add(target.index, removeAt(at)) }
            draggedAt = target.index
        } else {
            // Held past an edge of the panel: the list scrolls under the finger.
            val past = when {
                travelled > 0 -> (top + item.size - info.viewportEndOffset).coerceAtLeast(0f)
                travelled < 0 -> (top - info.viewportStartOffset).coerceAtMost(0f)
                else -> 0f
            }
            if (past != 0f) scope.launch { list.scrollBy(past) }
        }
    }

    /**
     * Ends the drag. Returns the move to send, as positions in the room's list, or null when the
     * row went back to its place or the list changed under the finger.
     */
    fun end(entriesNow: List<String>): Pair<Int, Int>? {
        val current = rows
        val at = draggedAt
        rows = null
        draggedAt = null
        travelled = 0f
        if (current == null || at == null || entriesNow != entriesAtStart) return null
        val from = current[at].first
        return if (from == at) null else from to at
    }
}
