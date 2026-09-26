package app.uicomponents.frames

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import app.uicomponents.controls.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.theme.Motion
import app.theme.Radius
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.chromeSurface
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

enum class NoticeSeverity { Info, Quiet, Sync, Warn }

class NoticeItem(val id: Long, val text: String, val severity: NoticeSeverity, val holdMs: Long) {
    /** True once the notice is on its way out. It fades first, and then the host removes it. */
    var leaving by mutableStateOf(false)
        internal set
}

/**
 * The queue of notices (short-lived messages). It shows at most [max] notices. When it is full, a
 * new warning pushes out the oldest notice, and any other new notice pushes out the oldest
 * non-warning, or is dropped when only warnings are left. Blank text or a zero or negative hold
 * posts nothing, which is how the notice duration setting turns notices off.
 *
 * A notice that leaves (at the end of its hold, pushed out, or cleared) first fades out in
 * [NoticeHost], which then removes it. So no notice vanishes from one frame to the next.
 */
@Stable
class NoticeQueue(private val max: Int = 3) {
    val items = mutableStateListOf<NoticeItem>()
    private var nextId = 0L

    fun post(text: String, severity: NoticeSeverity = NoticeSeverity.Info, holdMs: Long) {
        if (holdMs <= 0 || text.isBlank()) return
        val shown = items.filterNot { it.leaving }
        if (shown.size >= max) {
            val victim = when (severity) {
                NoticeSeverity.Warn -> shown.first()
                else -> shown.firstOrNull { it.severity != NoticeSeverity.Warn } ?: return
            }
            victim.leaving = true
        }
        // With no host on screen to finish the fades, leaving notices would pile up.
        while (items.size >= max * 3) {
            items.firstOrNull { it.leaving }?.let(items::remove) ?: break
        }
        items.add(NoticeItem(nextId++, text, severity, holdMs))
    }

    /** Starts the fade out of [item]. */
    fun dismiss(item: NoticeItem) { item.leaving = true }

    /** Starts the fade out of every notice. */
    fun clear() = items.forEach { it.leaving = true }

    /** Removes [item] once its fade out has ended. */
    internal fun remove(item: NoticeItem) { items.remove(item) }
}

/**
 * One notice: a 2dp stripe coloured by severity, and a line of `note` text. Over video it uses the
 * chrome tier; on a flat screen it is a toast in the panel colours. Screen readers announce it as
 * a live region, assertive for warnings.
 */
@Composable
fun Notice(
    text: String,
    severity: NoticeSeverity,
    modifier: Modifier = Modifier,
    overVideo: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val p = palette
    val stub: Brush = when (severity) {
        NoticeSeverity.Info -> Brush.verticalGradient(listOf(p.accent, p.accent))
        NoticeSeverity.Quiet -> Brush.verticalGradient(listOf(p.inkFaint, p.inkFaint))
        NoticeSeverity.Sync -> Brush.verticalGradient(p.brandField)
        NoticeSeverity.Warn -> Brush.verticalGradient(listOf(p.bad, p.bad))
    }
    val ink = if (overVideo) Color.White else p.ink
    Row(
        modifier = modifier
            .widthIn(max = Space.noticeWidth)
            .height(IntrinsicSize.Min)
            .heightIn(min = Space.rowCompact)
            .then(
                if (overVideo) Modifier.chromeSurface(Radius.panelShape)
                else Modifier.clip(Radius.panelShape).background(p.panel).border(Space.hair, p.rule, Radius.panelShape)
            )
            .semantics { liveRegion = if (severity == NoticeSeverity.Warn) LiveRegionMode.Assertive else LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(2.dp).fillMaxHeight().background(stub))
        Text(
            text = text,
            style = Type.note,
            color = ink,
            modifier = Modifier.weight(1f, fill = false).padding(horizontal = Space.gap, vertical = Space.gapTight + 2.dp),
        )
        if (trailing != null) {
            Box(Modifier.padding(end = Space.gapTight)) { trailing() }
        }
    }
}

/**
 * Renders a [NoticeQueue] as a stack, newest at the bottom. A notice fades in while its place
 * opens, and after its hold it fades out while its place closes, so the others glide instead of
 * jumping. The gap between notices sits inside each one, so it closes with it.
 */
@Composable
fun NoticeHost(queue: NoticeQueue, modifier: Modifier = Modifier, overVideo: Boolean = true) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        queue.items.forEach { item ->
            key(item.id) {
                val visible = remember { MutableTransitionState(false) }
                visible.targetState = !item.leaving
                LaunchedEffect(item) {
                    delay(item.holdMs)
                    queue.dismiss(item)
                }
                LaunchedEffect(item.leaving) {
                    if (!item.leaving) return@LaunchedEffect
                    // One frame first, so the fade out has started, then the end of that fade.
                    withFrameNanos { }
                    snapshotFlow { visible.isIdle && !visible.currentState }.first { it }
                    queue.remove(item)
                }
                AnimatedVisibility(
                    visibleState = visible,
                    enter = fadeIn(Motion.fade()) + expandVertically(Motion.fade(), expandFrom = Alignment.Top),
                    exit = fadeOut(Motion.fade()) + shrinkVertically(Motion.fade(), shrinkTowards = Alignment.Top),
                ) {
                    Notice(item.text, item.severity, overVideo = overVideo, modifier = Modifier.padding(vertical = Space.gapTight / 2))
                }
            }
        }
    }
}
