package app.uicomponents

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import java.net.URI

internal actual val acceptsMediaDrops: Boolean = true

// Some systems keep the data hidden until the drop, and reading it then throws.
@OptIn(ExperimentalComposeUiApi::class)
internal actual fun DragAndDropEvent.readDroppedItems(): List<DroppedItem>? = runCatching { droppedItems(dragData()) }.getOrNull()

/** The items in [data]: each file with whether it is a folder, or the text. A `file:` link in the text counts as a file. */
internal fun droppedItems(data: DragData): List<DroppedItem> = when (data) {
    is DragData.FilesList -> data.readFiles().mapNotNull(::fileAt)
    is DragData.Text -> {
        val text = data.readText()
        val firstLine = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
        listOf(fileAt(firstLine) ?: DroppedItem.Text(text))
    }
    else -> emptyList()
}

/** The file behind a `file:` URI, or null when [uri] is not one. */
private fun fileAt(uri: String): DroppedItem.File? {
    if (!uri.startsWith("file:", ignoreCase = true)) return null
    val file = runCatching { File(URI(uri)) }.getOrNull() ?: return null
    return DroppedItem.File(file.path, file.isDirectory)
}

/* The drop was accepted with the action the source proposed, which can be a move. After a move the
 * source may delete its file, so the drop is accepted again as a copy, as the original Syncplay
 * window does. Compose reports the drop complete after onDrop, so this still counts. */
@OptIn(ExperimentalComposeUiApi::class)
internal actual fun DragAndDropEvent.keepSourceFile() {
    val drop = nativeEvent as? DropTargetDropEvent ?: return
    if (drop.sourceActions and DnDConstants.ACTION_COPY != 0) runCatching { drop.acceptDrop(DnDConstants.ACTION_COPY) }
}
