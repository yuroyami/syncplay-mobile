package app.room

import kotlinx.coroutines.flow.MutableStateFlow

/** The add-media expansion temporarily borrows the side dock, until another control claims it. */
internal class SidePanelCoordinator(private val panels: List<MutableStateFlow<Boolean>>) {
    val mediaExpanded = MutableStateFlow(false)
    private var suspended: MutableStateFlow<Boolean>? = null

    fun open(target: MutableStateFlow<Boolean>, forced: Boolean?) {
        val opening = forced ?: !target.value
        if (opening) {
            collapseMedia(restore = false)
            panels.forEach { it.value = it === target }
        } else target.value = false
    }

    fun expandMedia() {
        if (mediaExpanded.value) return
        suspended = panels.firstOrNull { it.value }
        panels.forEach { it.value = false }
        mediaExpanded.value = true
    }

    fun collapseMedia(restore: Boolean = true) {
        mediaExpanded.value = false
        val previous = suspended
        suspended = null
        if (restore && panels.none { it.value }) previous?.value = true
    }
}
