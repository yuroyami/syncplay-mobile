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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.theme.Theming.flexibleGradient
import app.uicomponents.FlexibleIcon
import app.uicomponents.gradientOverlay
import app.uicomponents.tvFocusable
import com.composeunstyled.TextInput
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.UnstyledTextField

// Bridges the existing value/onValueChange API of 13+ callers onto UnstyledTextField's
// stateful TextFieldState API introduced in compose-unstyled 2.0. The internal sync is
// loop-free thanks to equality checks on both directions.
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

    UnstyledTextField(
        state = state,
        modifier = modifier.tvFocusable(
            focusRequester = focusRequester,
            shape = shape,
            addFocusable = false,
        ),
        textColor = MaterialTheme.colorScheme.onTertiaryContainer,
        lineLimits = TextFieldLineLimits.SingleLine,
        textAlign = TextAlign.Center,
        enabled = enabled,
        readOnly = dropdownState != null,
        onKeyboardAction = KeyboardActionHandler {
            if (clearFocusWhenDone) {
                focusManager.clearFocus(true)
            } else {
                focusManager.moveFocus(focusDirection = FocusDirection.Next)
            }
        },
        cursorBrush = Brush.verticalGradient(colors = flexibleGradient),
        keyboardOptions = KeyboardOptions(keyboardType = type ?: KeyboardType.Text)
    ) {
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
                    shadowColors = listOf(MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.5f)),
                    tintColors = listOf(MaterialTheme.colorScheme.onTertiaryContainer),
                    size = 36
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                TextInput(
                    placeholder = if (label != null) {
                        { Text(label, color = Color.Gray) }
                    } else null
                )
            }
            // We either show a dropdown cursor or we fill the space with a transparent icon so
            // that the input text remains exactly in the center.
            if (dropdownState != null) {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownState.value, modifier = Modifier.gradientOverlay())
            } else if (icon != null) {
                UnstyledIcon(
                    imageVector = Icons.Default.Done, contentDescription = null,
                    modifier = Modifier.padding(start = 4.dp), tint = Color.Transparent
                )
            }
        }
    }
}
