package app.uicomponents

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.i18n.AppStrings
import app.i18n.strings
import app.theme.Motion
import app.theme.Radius
import app.theme.Space
import app.theme.Tier
import app.theme.Type
import app.theme.palette
import app.uicomponents.controls.Text
import app.utils.isPlayableMediaFilename
import kotlinx.serialization.Serializable

/** One item that a drop carries: a file, or text such as a link. */
sealed interface DroppedItem {
    data class File(val path: String, val isDirectory: Boolean) : DroppedItem
    data class Text(val text: String) : DroppedItem
}

/** Media that a drop opens: a file by its path, or a link. */
@Serializable
sealed interface DroppedMedia {
    @Serializable
    data class File(val path: String) : DroppedMedia {
        /** The file name, without the folders. */
        val name: String get() = path.substringAfterLast('/').substringAfterLast('\\')
    }

    @Serializable
    data class Link(val url: String) : DroppedMedia
}

/** Why a drop is refused. */
enum class DropRefusal { NotMedia, Folder, Unsupported }

/** What a drop does: it opens media, or it is refused. */
sealed interface DropPlan {
    data class Open(val media: DroppedMedia) : DropPlan
    data class Refuse(val why: DropRefusal) : DropPlan
}

/**
 * Decides what a drop does. The first item decides, as in the window of the original Syncplay: a
 * media file or a link opens. A folder, any other file, and text that is not a link are refused.
 */
fun planDrop(items: List<DroppedItem>): DropPlan = when (val first = items.firstOrNull()) {
    null -> DropPlan.Refuse(DropRefusal.Unsupported)
    is DroppedItem.File -> {
        val media = DroppedMedia.File(first.path)
        when {
            first.isDirectory -> DropPlan.Refuse(DropRefusal.Folder)
            isPlayableMediaFilename(media.name) -> DropPlan.Open(media)
            else -> DropPlan.Refuse(DropRefusal.NotMedia)
        }
    }
    is DroppedItem.Text -> linkIn(first.text)?.let { DropPlan.Open(DroppedMedia.Link(it)) } ?: DropPlan.Refuse(DropRefusal.Unsupported)
}

/** The link in dropped text: its first line, when that line is one address with a scheme. */
private fun linkIn(text: String): String? =
    text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }?.takeIf { LINK.matches(it) }

// A scheme, "://" and no spaces. Syncplay's own isURL() only looks for "://".
private val LINK = Regex("""[A-Za-z][A-Za-z0-9+.-]*://\S+""")

/** The sentence for a refused drop. It shows over the window, and in a notice after the drop. */
fun AppStrings.dropRefusal(why: DropRefusal): String = when (why) {
    DropRefusal.NotMedia -> mediaDropNotMedia
    DropRefusal.Folder -> mediaDropFolder
    DropRefusal.Unsupported -> mediaDropUnsupported
}

/**
 * The media drop target of one screen. While an item is over the window, [over] is true and
 * [preview] says what a drop would do. [onPlan] gets the plan of every drop, refusals included, so
 * the screen can say why it refused one. Only desktop takes drops: see [mediaDropTarget].
 */
class MediaDropTarget(private val onPlan: (DropPlan) -> Unit) : DragAndDropTarget {

    /** True while a dragged item is over the screen. */
    var over by mutableStateOf(false)
        private set

    /** What a drop would do. Null while the platform keeps the dragged data hidden until the drop. */
    var preview by mutableStateOf<DropPlan?>(null)
        private set

    /** A drag reached the window. [items] is null when its data cannot be read before the drop. */
    internal fun started(items: List<DroppedItem>?) {
        preview = items?.let(::planDrop)
    }

    internal fun entered() {
        over = true
    }

    internal fun exited() {
        over = false
    }

    internal fun ended() {
        over = false
        preview = null
    }

    /** The drop itself. Data that cannot be read counts as unsupported. */
    internal fun dropped(items: List<DroppedItem>?): DropPlan {
        ended()
        val plan = items?.let(::planDrop) ?: DropPlan.Refuse(DropRefusal.Unsupported)
        onPlan(plan)
        return plan
    }

    override fun onStarted(event: DragAndDropEvent) = started(event.readDroppedItems())
    override fun onEntered(event: DragAndDropEvent) = entered()
    override fun onExited(event: DragAndDropEvent) = exited()
    override fun onEnded(event: DragAndDropEvent) = ended()

    override fun onDrop(event: DragAndDropEvent): Boolean {
        val plan = dropped(event.readDroppedItems())
        if (plan is DropPlan.Open) event.keepSourceFile()
        return plan is DropPlan.Open
    }
}

/** Takes media drops on the platforms that have them (desktop). Elsewhere it changes nothing. */
fun Modifier.mediaDropTarget(target: MediaDropTarget): Modifier =
    if (acceptsMediaDrops) dragAndDropTarget(shouldStartDragAndDrop = AnyDrag, target = target) else this

private val AnyDrag: (DragAndDropEvent) -> Boolean = { true }

/**
 * Dims the screen while an item is over it, and says what a drop will do: [openText] for media
 * that opens, the reason for a refusal, or a hint while the data stays hidden until the drop.
 */
@Composable
fun BoxScope.MediaDropOverlay(target: MediaDropTarget, openText: @Composable (DroppedMedia) -> String) {
    val p = palette
    AnimatedVisibility(target.over, Modifier.matchParentSize(), enter = fadeIn(Motion.quick()), exit = fadeOut(Motion.quick())) {
        Box(Modifier.fillMaxSize().surface(Tier.Scrim), contentAlignment = Alignment.Center) {
            val plan = target.preview
            Text(
                text = when (plan) {
                    null -> strings.mediaDropHint
                    is DropPlan.Open -> openText(plan.media)
                    is DropPlan.Refuse -> strings.dropRefusal(plan.why)
                },
                style = Type.label,
                color = if (plan is DropPlan.Refuse) p.warn else p.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(Space.gutter)
                    .widthIn(max = 420.dp)
                    .surface(Tier.Panel, Radius.panelShape)
                    .padding(horizontal = Space.gutter, vertical = Space.gap),
            )
        }
    }
}

/** Whether this platform takes drops. Only desktop does. */
internal expect val acceptsMediaDrops: Boolean

/** The items that [this] drop carries, or null when the platform cannot read them now. */
internal expect fun DragAndDropEvent.readDroppedItems(): List<DroppedItem>?

/** Takes the drop as a copy where the platform lets the source move it, so the source keeps its file. */
internal expect fun DragAndDropEvent.keepSourceFile()
