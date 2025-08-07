package com.yuroyami.syncplay.ui.screens.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composeunstyled.TextInput
import com.yuroyami.syncplay.ui.theme.Paletting.SP_GRADIENT
import com.composeunstyled.Icon as UnstyledIcon
import com.composeunstyled.Text as UnstyledText
import com.composeunstyled.TextField as UnstyledTextField

@Composable
fun HomeTextField(
    modifier: Modifier,
    icon: ImageVector? = null,
    label: String? = null,
    value: String,
    dropdownState: MutableState<Boolean>? = null,
    onValueChange: (String) -> Unit,
    type: KeyboardType? = null,
    cornerRadius: Dp = 40.dp
) {
    val focusManager = LocalFocusManager.current

    val cornerRadiusAnimated by animateDpAsState(
        targetValue = if (dropdownState?.value == true) 0.dp else cornerRadius,
        animationSpec = spring()
    )

    UnstyledTextField(
        modifier = modifier,
        value = value,
        onValueChange = onValueChange,
        textColor = Color.White,
        singleLine = true,
        editable = dropdownState == null,
        keyboardActions = KeyboardActions(
            onDone = {
                focusManager.moveFocus(focusDirection = FocusDirection.Next)
            }
        ),
        cursorBrush = Brush.verticalGradient(colors = SP_GRADIENT),
        keyboardOptions = KeyboardOptions(keyboardType = type ?: KeyboardType.Text)
    ) {
        TextInput(
            leading = if (icon != null) { { UnstyledIcon(imageVector = icon, contentDescription = null, modifier = Modifier.padding(end = 4.dp), tint = Color.White) } } else null,
            trailing = {
                dropdownState?.let { expandedDropdown ->
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDropdown.value)
                }
            },
            shape = RoundedCornerShape(cornerRadiusAnimated),
            placeholder = if (label != null) { { UnstyledText(label, color = Color.Gray) } } else null,
            backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 21.dp)
        )
    }
}