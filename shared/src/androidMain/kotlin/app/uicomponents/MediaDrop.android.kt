package app.uicomponents

import androidx.compose.ui.draganddrop.DragAndDropEvent

// No drops on this platform: see MediaDrop.kt.

internal actual val acceptsMediaDrops: Boolean = false

internal actual fun DragAndDropEvent.readDroppedItems(): List<DroppedItem>? = null

internal actual fun DragAndDropEvent.keepSourceFile() = Unit
