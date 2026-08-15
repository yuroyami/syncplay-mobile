package app.home.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.theme.Theming
import app.uicomponents.tvFocusable

// Uses BasicTextField directly rather than compose-unstyled's UnstyledTextField + TextInput
// (innerTextField is internal there, which blocks custom decoration).
//
// Bridges a value/onValueChange API onto the stateful TextFieldState; the two-way sync stays
// loop-free via the equality checks below.
@Composable
fun HomeTextField(
    modifier: Modifier,
    icon: ImageVector? = null,
    label: String? = null,
    value: String,
    dropdownState: MutableState<Boolean>? = null,
    onValueChange: (String) -> Unit,
    type: KeyboardType? = null,
    cornerRadius: Dp = 16.dp,
    height: Dp = 56.dp,
    clearFocusWhenDone: Boolean = false,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
) {
    val focusManager = LocalFocusManager.current
    val state = rememberTextFieldState(value)

    LaunchedEffect(value) {
        if (state.text.toString() != value) {
            state.edit { replace(0, length, value) }
        }
    }

    // The collector below outlives recompositions (keyed on the stable state object), so it must
    // read the CURRENT value/onValueChange, not the ones captured when it first launched. Callers
    // like HomeScreen re-key their backing states after the saved config loads; a stale lambda
    // here would keep writing into the orphaned pre-re-key state and edits would never reach
    // the Join button.
    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)

    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { newText ->
            if (newText != currentValue) currentOnValueChange(newText)
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val cornerRadiusAnimated by animateDpAsState(
        targetValue = if (dropdownState?.value == true) 0.dp else cornerRadius,
        animationSpec = spring()
    )

    val shape = RoundedCornerShape(cornerRadiusAnimated)
    val textColor = MaterialTheme.colorScheme.onSurface
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    )

    BasicTextField(
        state = state,
        modifier = modifier.tvFocusable(
            focusRequester = focusRequester,
            shape = shape,
            addFocusable = false,
        ),
        interactionSource = interactionSource,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor, textAlign = TextAlign.Center),
        lineLimits = TextFieldLineLimits.SingleLine,
        enabled = enabled,
        readOnly = dropdownState != null,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = type ?: KeyboardType.Text),
        onKeyboardAction = KeyboardActionHandler {
            if (clearFocusWhenDone) {
                focusManager.clearFocus(true)
            } else {
                focusManager.moveFocus(focusDirection = FocusDirection.Next)
            }
        },
        decorator = { innerTextField ->
            /* Clear (X) appears only on editable, non-empty fields. */
            val showClear = dropdownState == null && enabled && state.text.isNotEmpty()

            Row(
                modifier = Modifier.fillMaxWidth().height(height)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(width = if (isFocused) 2.dp else 1.dp, color = borderColor, shape = shape)
                    .padding(PaddingValues(horizontal = Theming.SpaceLG, vertical = Theming.SpaceSM)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = Theming.SpaceMD).size(24.dp)
                    )
                } else if (showClear) {
                    // Leading counterweight for the trailing X on icon-less fields, so the
                    // centered text does not drift sideways when the X appears.
                    Spacer(modifier = Modifier.padding(end = Theming.SpaceMD).size(24.dp))
                }
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally)) {
                        if (state.text.isEmpty() && label != null) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        innerTextField()
                    }
                }
                // Trailing slot: dropdown chevron, or a clear (X) button when there is text,
                // else a transparent counterweight mirroring the leading icon so the centered
                // text sits at the true optical center of the field.
                when {
                    dropdownState != null -> ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownState.value)

                    showClear -> Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = Theming.SpaceMD)
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = false, radius = 16.dp)
                            ) {
                                state.edit { replace(0, length, "") }
                            }
                    )

                    icon != null -> Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.Transparent,
                        modifier = Modifier.padding(start = Theming.SpaceMD).size(24.dp)
                    )
                }
            }
        },
    )
}
