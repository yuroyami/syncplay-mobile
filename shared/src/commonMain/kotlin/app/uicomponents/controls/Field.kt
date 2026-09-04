package app.uicomponents.controls

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import app.uicomponents.controls.Icon
import app.uicomponents.controls.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.action_clear
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.theme.Motion
import app.theme.Space
import app.theme.Type
import app.theme.palette

/**
 * The hairline field: a single underline that thickens to 2dp and takes the accent on focus.
 * No box. Optional leading glyph in the gutter, optional trailing clear glyph on its own target
 * that the keyboard's focus traversal skips. A controlled field, so a host that re-keys its
 * state never leaves a stale callback behind.
 */
@Composable
fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leading: ImageVector? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    showClear: Boolean = true,
    singleLine: Boolean = true,
    textStyle: TextStyle = Type.label,
    name: String? = null,
) {
    val p = palette
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    // Reported for the desktop key map, which must not spend the arrows on seeking mid-sentence.
    DisposableEffect(focused) {
        if (focused) TextInputFocus.report(true)
        onDispose { if (focused) TextInputFocus.report(false) }
    }
    val lineColor by animateColorAsState(if (focused) p.accent else p.rule, Motion.quick(), label = "line")
    val lineWidth by animateDpAsState(if (focused) 2.dp else Space.hair, Motion.quick(), label = "lineWidth")

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Space.row)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .semantics { if (name != null) contentDescription = name }
            .drawBehind {
                val w = lineWidth.toPx()
                drawRect(lineColor, Offset(0f, size.height - w), Size(size.width, w))
            },
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle.copy(color = if (enabled) p.ink else p.disabled),
        singleLine = singleLine,
        cursorBrush = SolidColor(p.accent),
        interactionSource = source,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onImeAction?.invoke() }),
        decorationBox = { inner ->
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = Space.row),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leading != null) {
                    Icon(leading, contentDescription = null, tint = if (focused) p.accent else p.inkDim, modifier = Modifier.size(Space.glyph))
                    Spacer(Modifier.width(Space.gap))
                }
                val centred = textStyle.textAlign == TextAlign.Center
                Box(
                    modifier = Modifier.weight(1f).padding(vertical = Space.gapTight),
                    contentAlignment = if (centred) Alignment.Center else Alignment.CenterStart,
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = textStyle, color = p.inkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    // The editor gets the full width, so a centred style has something to centre in.
                    Box(Modifier.fillMaxWidth()) { inner() }
                }
                if (showClear && value.isNotEmpty() && enabled && !readOnly) {
                    GlyphButton(
                        icon = CloseGlyph,
                        name = stringResource(Res.string.action_clear),
                        onClick = { onValueChange("") },
                        tint = p.inkDim,
                        // Never a focus stop: keyboard traversal jumps field to field, not to the clear glyph.
                        modifier = Modifier.focusProperties { canFocus = false },
                    )
                }
            }
        },
    )
}
