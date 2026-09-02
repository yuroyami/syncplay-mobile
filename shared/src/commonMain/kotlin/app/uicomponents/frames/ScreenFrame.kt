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
import app.uicomponents.controls.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.action_back
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
 * The frame every page shares: a 54dp bar plus the status inset with a back glyph, a `display`
 * title and up to two trailing glyph buttons, a hairline that appears once content has scrolled
 * under the bar, and the insets solved once. The room does not use this; it is a mode with its
 * own frame, on purpose.
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
                    GlyphButton(BackGlyph, name = stringResource(Res.string.action_back), onClick = onBack, size = Space.glyphLarge)
                }
                Text(
                    text = title,
                    style = Type.display,
                    color = p.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = if (onBack != null) Space.gapTight else 0.dp),
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
