package app.home.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.theme.Theming.flexibleGradient
import app.uicomponents.FlexibleIcon
import app.uicomponents.gradientOverlay
import app.uicomponents.tvFocusable
import com.composeunstyled.UnstyledIcon

// Uses BasicTextField directly rather than compose-unstyled's UnstyledTextField + TextInput:
// TextInput wrap-contents its inner text composable (innerTextField is internal), so
// textAlign = Center has no extra width to center within. BasicTextField lets the inner text
// sit in a fillMaxWidth Box, making center alignment visible.
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
    cornerRadius: Dp = 35.dp,
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

    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { newText ->
            if (newText != value) onValueChange(newText)
        }
    }

    val cornerRadiusAnimated by animateDpAsState(
        targetValue = if (dropdownState?.value == true) 0.dp else cornerRadius,
        animationSpec = spring()
    )

    val shape = RoundedCornerShape(cornerRadiusAnimated)
    val textColor = MaterialTheme.colorScheme.onTertiaryContainer

    BasicTextField(
        state = state,
        modifier = modifier.tvFocusable(
            focusRequester = focusRequester,
            shape = shape,
            addFocusable = false,
        ),
        textStyle = TextStyle(color = textColor, textAlign = TextAlign.Center),
        lineLimits = TextFieldLineLimits.SingleLine,
        enabled = enabled,
        readOnly = dropdownState != null,
        cursorBrush = Brush.verticalGradient(colors = flexibleGradient),
        keyboardOptions = KeyboardOptions(keyboardType = type ?: KeyboardType.Text),
        onKeyboardAction = KeyboardActionHandler {
            if (clearFocusWhenDone) {
                focusManager.clearFocus(true)
            } else {
                focusManager.moveFocus(focusDirection = FocusDirection.Next)
            }
        },
        decorator = { innerTextField ->
            Row(
                modifier = Modifier.fillMaxWidth().height(height)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .border(width = Dp.Hairline, brush = Brush.linearGradient(colors = flexibleGradient), shape = shape)
                    .padding(PaddingValues(horizontal = 4.dp, vertical = 12.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    FlexibleIcon(
                        modifier = Modifier.padding(2.dp),
                        icon = icon,
                        shadowColors = listOf(textColor.copy(alpha = 0.5f)),
                        tintColors = listOf(textColor),
                        size = 36
                    )
                }
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally)) {
                        if (state.text.isEmpty() && label != null) {
                            Text(label, color = Color.Gray)
                        }
                        innerTextField()
                    }
                }
                // Dropdown cursor, or a transparent icon filling the same space so the input
                // text stays exactly centered.
                if (dropdownState != null) {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownState.value, modifier = Modifier.gradientOverlay())
                } else if (icon != null) {
                    UnstyledIcon(
                        imageVector = Icons.Default.Done, contentDescription = null,
                        modifier = Modifier.padding(start = 4.dp), tint = Color.Transparent
                    )
                }
            }
        },
    )
}
