package app.uicomponents.frames

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import app.i18n.strings
import app.uicomponents.controls.Text
import app.uicomponents.controls.FontSizeRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.theme.Motion
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.controls.BackGlyph
import app.uicomponents.controls.GlyphButton
import app.uicomponents.controls.Rule

/**
 * The frame that every page shares. It has a 54dp bar (plus the status bar inset) with a back
 * button, a `display` title and up to two trailing icon buttons. A thin line appears under the bar
 * once content has scrolled under it ([scrolled]), and the window insets are handled here once.
 * The room does not use this frame: the room is a separate mode with its own frame, on purpose.
 */
@Composable
fun ScreenFrame(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    scrolled: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val p = palette
    val ruleAlpha by animateFloatAsState(if (scrolled) 1f else 0f, Motion.quick(), label = "barRule")
    Column(modifier.fillMaxSize().background(p.ground)) {
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.statusBars.union(WindowInsets.displayCutout)
                        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Space.bar)
                    .padding(start = if (onBack != null) Space.gapTight else Space.gutter, end = Space.gapTight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    GlyphButton(BackGlyph, name = strings.actionBack, onClick = onBack, size = Space.glyphLarge)
                }
                // A long title in a large text size shrinks before it is cut.
                Text(
                    text = title,
                    style = Type.display,
                    color = p.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = if (onBack != null) Space.gapTight else 0.dp),
                    autoSize = FontSizeRange(Type.display.fontSize),
                )
                actions()
            }
            Rule(Modifier.alpha(ruleAlpha))
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .imePadding(),
            content = content,
        )
    }
}
