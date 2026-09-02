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
 * The chrome every room panel shares: a 42dp header with the title and glyph actions, a hairline,
 * and a body. No inner cards. The shape comes from the dock the panel sits in.
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
    // A tap that lands between rows stops here instead of reaching the HUD and hiding it.
    Column(modifier.surface(Tier.Panel, shape, rim).pointerInput(Unit) { detectTapGestures { } }) {
        Row(
            modifier = Modifier.fillMaxWidth().height(Space.row).padding(start = if (centerTitle) Space.gapTight else Space.gutter, end = Space.gapTight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A centred title gets a lead-in the width of one glyph key, balancing the close key.
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
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), content = content)
        } else {
            Column(Modifier.weight(1f, fill = false), content = content)
        }
    }
}
