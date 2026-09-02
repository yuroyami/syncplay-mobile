package app.uicomponents.frames

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
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

enum class NoticeSeverity { Info, Quiet, Sync, Warn }

class NoticeItem(val id: Long, val text: String, val severity: NoticeSeverity, val holdMs: Long)

/**
 * The transient message queue: at most [max] notices, the oldest leaves first, and a warning is
 * never dropped to make room for an info. A zero or negative hold posts nothing, which is how
 * the notice duration preference switches notices off.
 */
@Stable
class NoticeQueue(private val max: Int = 3) {
    val items = mutableStateListOf<NoticeItem>()
    private var nextId = 0L

    fun post(text: String, severity: NoticeSeverity = NoticeSeverity.Info, holdMs: Long) {
        if (holdMs <= 0 || text.isBlank()) return
        if (items.size >= max) {
            val victim = when (severity) {
                NoticeSeverity.Warn -> items.first()
                else -> items.firstOrNull { it.severity != NoticeSeverity.Warn } ?: return
            }
            items.remove(victim)
        }
        items.add(NoticeItem(nextId++, text, severity, holdMs))
    }

    fun dismiss(item: NoticeItem) { items.remove(item) }

    fun clear() = items.clear()
}

/**
 * One notice: a 2dp stub coloured by severity and a `note` line. On the `chrome` tier over video,
 * or on the panel colours as a toast on a flat screen. A live region, assertive for warnings.
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
            .widthIn(max = 420.dp)
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

/** Renders a [NoticeQueue] as a stack, newest at the bottom, each fading after its hold. */
@Composable
fun NoticeHost(queue: NoticeQueue, modifier: Modifier = Modifier, overVideo: Boolean = true) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.gapTight), horizontalAlignment = Alignment.CenterHorizontally) {
        queue.items.forEach { item ->
            key(item.id) {
                val visible = remember { MutableTransitionState(false) }.apply { targetState = true }
                LaunchedEffect(item) {
                    delay(item.holdMs)
                    queue.dismiss(item)
                }
                AnimatedVisibility(
                    visibleState = visible,
                    enter = fadeIn(Motion.quick()) + slideInVertically(Motion.move()) { -it / 3 },
                    exit = fadeOut(Motion.move()),
                ) {
                    Notice(item.text, item.severity, overVideo = overVideo)
                }
            }
        }
    }
}
