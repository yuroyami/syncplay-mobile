package app.uicomponents.frames

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import app.uicomponents.controls.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import app.theme.Radius
import app.theme.Space
import app.theme.Tier
import app.theme.Type
import app.theme.palette
import app.uicomponents.GlassEdge
import app.uicomponents.controls.Rule
import app.uicomponents.surface

/**
 * The frame of a room panel: a 42dp header with the title and icon actions, a thin line, and a
 * body. No inner cards. The caller passes the shape, which depends on where the panel is docked.
 */
@Composable
fun PanelFrame(
    title: String,
    modifier: Modifier = Modifier,
    shape: Shape = Radius.panelShape,
    rim: GlassEdge = GlassEdge.All,
    scrollable: Boolean = true,
    centerTitle: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = palette
    PanelSurface(modifier, shape, rim) {
        Row(
            modifier = Modifier.fillMaxWidth().height(Space.row).padding(start = if (centerTitle) Space.gapTight else Space.gutter, end = Space.gapTight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A centred title gets a spacer one icon button wide, to balance the close button.
            if (centerTitle) Spacer(Modifier.width(Space.touchMin))
            Text(
                text = title,
                style = Type.label,
                color = p.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (centerTitle) TextAlign.Center else null,
                modifier = Modifier.weight(1f),
            )
            actions()
        }
        Rule()
        if (scrollable) {
            val scroll = rememberScrollState()
            // Full width, so the bar sits on the panel's edge and not at the end of the widest row.
            ScrollbarHost(scroll, Modifier.weight(1f, fill = false).fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().verticalScroll(scroll), content = content)
            }
        } else {
            Column(Modifier.weight(1f, fill = false), content = content)
        }
    }
}

/** The panel without the header, for a panel whose own tabs already name it. */
@Composable
fun PanelSurface(
    modifier: Modifier = Modifier,
    shape: Shape = Radius.panelShape,
    rim: GlassEdge = GlassEdge.All,
    content: @Composable ColumnScope.() -> Unit,
) {
    // A tap that lands between rows stops here instead of reaching the HUD and hiding it.
    Column(modifier.surface(Tier.Panel, shape, rim).pointerInput(Unit) { detectTapGestures { } }, content = content)
}
