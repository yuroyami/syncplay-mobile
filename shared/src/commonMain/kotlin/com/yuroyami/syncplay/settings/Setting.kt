package com.yuroyami.syncplay.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuroyami.syncplay.compose.ComposeUtils.FlexibleFancyText
import com.yuroyami.syncplay.compose.ComposeUtils.MultiChoiceDialog
import com.yuroyami.syncplay.compose.ComposeUtils.SmartFancyIcon
import com.yuroyami.syncplay.compose.popups.PopupColorPicker.ColorPickingPopup
import com.yuroyami.syncplay.lyricist.rememberStrings
import com.yuroyami.syncplay.ui.Paletting
import com.yuroyami.syncplay.utils.colorpicker.HsvColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** Main class that does the required logic and UI for a single Setting.
 * @param type The type of the setting, must be a [SettingType]
 * @param key Key string that identifies the setting, to save it and read it.
 * @param title Title of the setting, visible to the user, which appears in a bigger text size.
 * @param summary Summary of the setting, preferably explaining how the setting works.
 * @param icon Icon for the setting, it's null by default so no icon will be shown unless you pass an [ImageVector] for it
 * @param defaultValue The default value of the setting, can be of any type but will throw an exception when incompatible
 * @param enabled Whether the setting is enabled, this is true by default if you don't pass any value so it's optional.
 * @param styling The styling used for the setting appearance (Fonts, sizes, colors for icon, title, summary, padding etc)
 * */
sealed class Setting<T>(
    val type: SettingType = SettingType.OneClickSettingType, val key: String = "",
    val title: StringResource, val summary: StringResource,
    val defaultValue: T? = null,
    val icon: ImageVector? = null, val enabled: Boolean = true, val styling: SettingStyling = SettingStyling()
) {
    /** This is the abstract function that will be called by Compose UI in order to draw the setting.
     * Each setting type has its own UI, so within this sealed class, we override it and draw it respectively;
     */
    @Composable
    abstract fun SettingComposable(modifier: Modifier)

    /** ======= Now to specific types of SETTINGs and their respective child classes ======= */

    class OneClickSetting(
        type: SettingType, key: String, summary: StringResource, title: StringResource, defaultValue: Any? = null,
        icon: ImageVector?, enabled: Boolean = true, styling: SettingStyling,
        val onClick: (() -> Unit)? = null,
    ) : Setting<Any>(
        type = type, key = key, summary = summary, title = title, defaultValue = defaultValue,
        icon = icon, enabled = enabled, styling = styling
    ) {
        @Composable
        override fun SettingComposable(modifier: Modifier) {
            ListItem(
                modifier = modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = Paletting.SP_ORANGE)
                    ) {
                        onClick?.let { it() }
                    },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),

                headlineContent = {
                    Row(verticalAlignment = CenterVertically) {
                        SmartFancyIcon(
                            tintColors = styling.iconTints,
                            icon = icon ?: Icons.Filled.QuestionMark, size = styling.iconSize.toInt()
                        )
                        FlexibleFancyText(
                            text = stringResource(title),
                            fillingColors = styling.titleFilling ?: listOf(MaterialTheme.colorScheme.primary),
                            strokeColors = styling.titleStroke ?: listOf(),
                            shadowColors = styling.titleShadow ?: Paletting.SP_GRADIENT,
                            size = styling.titleSize,
                            font = styling.titleFont
                        )
                    }
                },
                supportingContent = {
                    Text(
                        text = stringResource(summary),
                        style = TextStyle(
                            color = styling.summaryColor,
                            fontFamily = styling.summaryFont?.let { FontFamily(it) } ?: FontFamily.Default, fontSize = styling.summarySize.sp,
                        )
                    )
                }
            )
        }
    }

    class YesNoDialogSetting(
        type: SettingType, key: String, summary: StringResource, title: StringResource, defaultValue: Any? = null,
        icon: ImageVector?, enabled: Boolean = true, styling: SettingStyling,
        val rationale: StringResource,
        val onYes: (CoroutineScope.() -> Unit)? = null,
        val onNo: (CoroutineScope.() -> Unit)? = null
        //TODO: Show "done" message (i.e: Operation successfully carried out) in a snackbar message
    ) : Setting<Any>(
        type = type, key = key, summary = summary, title = title, defaultValue = defaultValue,
        icon = icon, enabled = enabled, styling = styling
    ) {
        @Composable
        override fun SettingComposable(modifier: Modifier) {
            val scope = rememberCoroutineScope { Dispatchers.IO }
            val lyricist = rememberStrings()

            var dialog by remember { mutableStateOf(false) }
            if (dialog ) {
                AlertDialog(
                    onDismissRequest = { dialog = false },
                    confirmButton = {
                        TextButton(onClick = {
                            dialog = false
                            onYes?.invoke(scope)
                        }) { Text(lyricist.strings.yes) }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            dialog = false
                            onNo?.invoke(scope)
                        }) { Text(lyricist.strings.no) }
                    },
                    text = { Text(stringResource(rationale)) }
                )
            }
            ListItem(
                modifier = modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = Paletting.SP_ORANGE)
                    ) {
                        dialog = true
                    },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ){
                        SmartFancyIcon(
                            tintColors = styling.iconTints,
                            icon = icon ?: Icons.Filled.QuestionMark, size = styling.iconSize.toInt()
                        )
                        FlexibleFancyText(
                            text = stringResource(title),
                            fillingColors = styling.titleFilling ?: listOf(MaterialTheme.colorScheme.primary),
                            strokeColors = styling.titleStroke ?: listOf(),
                            shadowColors = styling.titleShadow ?: Paletting.SP_GRADIENT,
                            size = styling.titleSize,
                            font = styling.titleFont
                        )
                    }
                },
                supportingContent = {
                    Text(
                        text = stringResource(summary),
                        style = TextStyle(
                            color = styling.summaryColor,
                            fontFamily = styling.summaryFont?.let { FontFamily(it) } ?: FontFamily.Default, fontSize = styling.summarySize.sp,
                        )
                    )
                }
            )
        }
    }

    class PopupSetting(
        type: SettingType, key: String, summary: StringResource, title: StringResource, defaultValue: Any? = null,
        icon: ImageVector?, enabled: Boolean = true, styling: SettingStyling,
        val popupComposable: @Composable() ((MutableState<Boolean>) -> Unit)? = null
    ) : Setting<Any>(
        type = type, key = key, summary = summary, title = title, defaultValue = defaultValue,
        icon = icon, enabled = enabled, styling = styling
    ) {
        @Composable
        override fun SettingComposable(modifier: Modifier) {
            val popupVisibility = remember { mutableStateOf(false) }

            ListItem(
                modifier = modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = Paletting.SP_ORANGE)

                    ) {
                        popupVisibility.value = true
                    },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),

                headlineContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ){
                        SmartFancyIcon(
                            tintColors = styling.iconTints,
                            shadowColors = styling.iconShadows,
                            icon = icon ?: Icons.Filled.QuestionMark, size = styling.iconSize.toInt(),
                        )
                        FlexibleFancyText(
                            text = stringResource(title),
                            fillingColors = styling.titleFilling ?: listOf(MaterialTheme.colorScheme.primary),
                            strokeColors = styling.titleStroke ?: listOf(),
                            shadowColors = styling.titleShadow ?: Paletting.SP_GRADIENT,
                            size = styling.titleSize,
                            font = styling.titleFont
                        )
                    }
                },
                supportingContent = {
                    Text(
                        text = stringResource(summary),
                        style = TextStyle(
                            color = styling.summaryColor,
                            fontFamily = styling.summaryFont?.let { FontFamily(it) } ?: FontFamily.Default, fontSize = styling.summarySize.sp,
                        )
                    )
                }
            )


            popupComposable?.invoke(popupVisibility)
        }
    }

    class BooleanSetting(
        type: SettingType, key: String, summary: StringResource, title: StringResource, defaultValue: Boolean?,
        icon: ImageVector?, enabled: Boolean = true, styling: SettingStyling,
        val onBooleanChanged: (Boolean) -> Unit = {},
    ) : Setting<Boolean>(
        type = type, key = key, summary = summary, title = title, defaultValue = defaultValue,
        icon = icon, enabled = enabled, styling = styling
    ) {
        /** A boolean setting UI composable function. This either creates a CheckboxSetting or a ToggleSetting
         * based on the parameter [type] that is passed, whether it is a checkbox setting, or a toggle button. */
        @Composable
        override fun SettingComposable(modifier: Modifier) {
            val boolean = getSettingState()
            val scope = rememberCoroutineScope { Dispatchers.IO }

            ListItem(
                modifier = modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = Paletting.SP_ORANGE)

                    ) {
                        scope.launch {
                            writeValue(key, !boolean.value)
                        }
                    },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),

                trailingContent = {
                    if (type == SettingType.CheckboxSettingType) {
                        Checkbox(
                            checked = boolean.value,
                            enabled = enabled,
                            onCheckedChange = { b ->
                                scope.launch {
                                    writeValue(key, b)
                                }
                                onBooleanChanged.invoke(b)
                            }
                        )
                    } else {
                        Switch(
                            checked = boolean.value,
                            enabled = enabled,
                            onCheckedChange = { b ->
                                scope.launch {
                                    writeValue(key, b)
                                }
                                onBooleanChanged.invoke(b)
                            }
                        )
                    }
                },
                headlineContent = {
                    Row(verticalAlignment = CenterVertically) {
                        SmartFancyIcon(
                            tintColors = styling.iconTints,
                            shadowColors = styling.iconShadows,
                            icon = icon ?: Icons.Filled.QuestionMark, size = styling.iconSize.toInt(),
                        )
                        FlexibleFancyText(
                            text = stringResource(title),
                            fillingColors = styling.titleFilling ?: listOf(MaterialTheme.colorScheme.primary),
                            strokeColors = styling.titleStroke ?: listOf(),
                            shadowColors = styling.titleShadow ?: Paletting.SP_GRADIENT,
                            size = styling.titleSize,
                            font = styling.titleFont
                        )
                    }
                },
                supportingContent = {
                    Text(
                        text = stringResource(summary),
                        style = TextStyle(
                            color = styling.summaryColor,
                            fontFamily = styling.summaryFont?.let { FontFamily(it) } ?: FontFamily.Default, fontSize = styling.summarySize.sp,
                        )
                    )
                }
            )
        }
    }

    class MultiChoiceSetting(
        type: SettingType, key: String, summary: StringResource, title: StringResource, defaultValue: String?,
        icon: ImageVector?, enabled: Boolean = true, styling: SettingStyling,
        val entryKeys: List<String> = listOf(),
        val entryValues: List<String> = listOf(),
        val onItemChosen: ((index: Int, value: String) -> Unit)? = null
    ) : Setting<String>(
        type = type, key = key, summary = summary, title = title, defaultValue = defaultValue,
        icon = icon, enabled = enabled, styling = styling
    ) {
        /** A multi-choice setting which shows a popup dialog when clicked.
         * @exception SettingCreationException When [entryKeys] and [entryValues] are not passed/specified. */
        @Composable
        override fun SettingComposable(modifier: Modifier) {
            val dialogOpen = remember { mutableStateOf(false) }
            val selectedItem = getSettingState()
            val scope = rememberCoroutineScope { Dispatchers.IO }

            val renderedValues = entryValues

            if (dialogOpen.value) {
                MultiChoiceDialog(
                    items = entryKeys,
                    title = stringResource(title),
                    onDismiss = { dialogOpen.value = false },
                    selectedItem = renderedValues.indexOf(selectedItem.value),
                    onItemClick = { i ->
                        dialogOpen.value = false

                        scope.launch {
                            writeValue(key, renderedValues[i])

                            onItemChosen?.let { it(i, renderedValues[i]) }

                        }
                    })
            }

            ListItem(
                modifier = modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = Paletting.SP_ORANGE)

                    ) {
                        dialogOpen.value = true
                    },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = {
                    Row(verticalAlignment = CenterVertically) {
                        SmartFancyIcon(
                            tintColors = styling.iconTints,
                            shadowColors = styling.iconShadows,
                            icon = icon ?: Icons.Filled.QuestionMark, size = styling.iconSize.toInt(),
                        )
                        FlexibleFancyText(
                            text = stringResource(title),
                            fillingColors = styling.titleFilling ?: listOf(MaterialTheme.colorScheme.primary),
                            strokeColors = styling.titleStroke ?: listOf(),
                            shadowColors = styling.titleShadow ?: Paletting.SP_GRADIENT,
                            size = styling.titleSize,
                            font = styling.titleFont
                        )
                    }
                },
                trailingContent = {
                    Icon(imageVector = Icons.AutoMirrored.Filled.List, "")
                },
                supportingContent = {
                    Text(
                        text = stringResource(summary),
                        style = TextStyle(
                            color = styling.summaryColor,
                            fontFamily = styling.summaryFont?.let { FontFamily(it) } ?: FontFamily.Default, fontSize = styling.summarySize.sp,
                        )
                    )
                }
            )
        }
    }

    class SliderSetting(
        type: SettingType, key: String, summary: StringResource, title: StringResource, defaultValue: Int?,
        icon: ImageVector?, enabled: Boolean = true, styling: SettingStyling,
        val maxValue: Int = 100,
        val minValue: Int = 0,
        val onValueChanged: ((newValue: Int) -> Unit)? = null,
    ) : Setting<Int>(
        type = type, key = key, summary = summary, title = title, defaultValue = defaultValue,
        icon = icon, enabled = enabled, styling = styling
    ) {
        @Composable
        override fun SettingComposable(modifier: Modifier) {
            val value = getSettingState()
            val scope = rememberCoroutineScope { Dispatchers.IO }

            ListItem(
                modifier = modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = Paletting.SP_ORANGE)

                    ) {},
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = {
                    Row(verticalAlignment = CenterVertically) {
                        SmartFancyIcon(
                            tintColors = styling.iconTints,
                            shadowColors = styling.iconShadows,
                            icon = icon ?: Icons.Filled.QuestionMark, size = styling.iconSize.toInt()
                        )

                        FlexibleFancyText(
                            text = stringResource(title),
                            fillingColors = styling.titleFilling ?: listOf(MaterialTheme.colorScheme.primary),
                            strokeColors = styling.titleStroke ?: listOf(),
                            shadowColors = styling.titleShadow ?: Paletting.SP_GRADIENT,
                            size = styling.titleSize,
                            font = styling.titleFont
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = (value.value).toString(),
                            style = TextStyle(
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = styling.summaryFont?.let { FontFamily(it) } ?: FontFamily.Default,
                                fontSize = (13).sp
                            )
                        )
                    }
                },
                supportingContent = {
                    Column {
                        Text(
                            text = stringResource(summary),
                            style = TextStyle(
                                color = styling.summaryColor,
                                fontFamily = styling.summaryFont?.let { FontFamily(it) } ?: FontFamily.Default, fontSize = styling.summarySize.sp,
                            )
                        )

                        Slider(
                            value = value.value.toFloat(),
                            enabled = enabled,
                            valueRange = (minValue.toFloat())..(maxValue.toFloat()),
                            onValueChange = { f ->
                                if (f != value.value.toFloat()) {
                                    onValueChanged?.invoke(f.roundToInt())
                                }

                                scope.launch {
                                    writeValue(key, f.roundToInt())
                                }
                            }, modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                        )
                    }
                },

            )
        }
    }

    class ColorSetting(
        type: SettingType, key: String, summary: StringResource, title: StringResource, defaultValue: Int?,
        icon: ImageVector?, enabled: Boolean = true, styling: SettingStyling
    ) : Setting<Int>(
        type = type, key = key, summary = summary, title = title, defaultValue = defaultValue,
        icon = icon, enabled = enabled, styling = styling
    ) {
        /** A color setting UI composable function. Clicking this would displays a popup to the user. */
        @Composable
        override fun SettingComposable(modifier: Modifier) {
            val color = getSettingState()
            val colorDialogState = remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope { Dispatchers.IO }

            ListItem(
                modifier = modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = Paletting.SP_ORANGE)

                    ) {
                        colorDialogState.value = true
                    },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = {
                    Row(verticalAlignment = CenterVertically) {
                        SmartFancyIcon(
                            tintColors = styling.iconTints,
                            shadowColors = styling.iconShadows,
                            icon = icon ?: Icons.Filled.QuestionMark, size = styling.iconSize.toInt()
                        )
                        FlexibleFancyText(
                            text = stringResource(title),
                            fillingColors = styling.titleFilling ?: listOf(MaterialTheme.colorScheme.primary),
                            strokeColors = styling.titleStroke ?: listOf(),
                            shadowColors = styling.titleShadow ?: Paletting.SP_GRADIENT,
                            size = styling.titleSize,
                            font = styling.titleFont
                        )
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = { colorDialogState.value = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(color.value)),
                            modifier = Modifier.size(24.dp)
                        ) {}
                    }
                },

                supportingContent = {
                    Text(
                        text = stringResource(summary),
                        style = TextStyle(
                            color = styling.summaryColor,
                            fontFamily = styling.summaryFont?.let { FontFamily(it) } ?: FontFamily.Default, fontSize = styling.summarySize.sp,
                        )
                    )
                }
            )

            ColorPickingPopup(colorDialogState, initialColor = HsvColor.from(Color(color.value)), onColorChanged = { hsvColor ->
                scope.launch {
                    writeValue(key, hsvColor.toColor().toArgb())
                }
            }, onDefaultReset = { scope.launch { writeValue(key, defaultValue) } })
        }
    }

    class TextFieldSetting(
        type: SettingType, key: String, summary: StringResource, title: StringResource, defaultValue: String?,
        icon: ImageVector?, enabled: Boolean = true, styling: SettingStyling
    ) : Setting<String>(
        type = type, key = key, summary = summary, title = title, defaultValue = defaultValue,
        icon = icon, enabled = enabled, styling = styling
    ) {
        /** A string setting UI composable function. It has a textfield next to it */
        @Composable
        override fun SettingComposable(modifier: Modifier) {
            val string by getSettingState()
            val scope = rememberCoroutineScope()
            val focusManager = LocalFocusManager.current

            ListItem(
                modifier = modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = Paletting.SP_ORANGE)

                    ) {
                        scope.launch {
                            writeValue(key, string)
                        }
                    },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),

                headlineContent = {
                    Row(verticalAlignment = CenterVertically) {
                        SmartFancyIcon(
                            tintColors = styling.iconTints,
                            shadowColors = styling.iconShadows,
                            icon = icon ?: Icons.Filled.QuestionMark, size = styling.iconSize.toInt(),
                        )
                        FlexibleFancyText(
                            text = stringResource(title),
                            fillingColors = styling.titleFilling ?: listOf(MaterialTheme.colorScheme.primary),
                            strokeColors = styling.titleStroke ?: listOf(),
                            shadowColors = styling.titleShadow ?: Paletting.SP_GRADIENT,
                            size = styling.titleSize,
                            font = styling.titleFont
                        )
                    }
                },
                supportingContent = {
                    Column {
                        Text(
                            text = stringResource(summary),
                            style = TextStyle(
                                color = styling.summaryColor,
                                fontFamily = styling.summaryFont?.let { FontFamily(it) } ?: FontFamily.Default, fontSize = styling.summarySize.sp,
                            )
                        )
                        TextField(

                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            value = string,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            keyboardActions = KeyboardActions(onDone = {
                                focusManager.clearFocus()
                            }),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.primary,
                                unfocusedContainerColor = MaterialTheme.colorScheme.primary,
                                disabledContainerColor = MaterialTheme.colorScheme.primary,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                            ),
                            onValueChange = {
                                scope.launch {
                                    writeValue(key, it)
                                }
                            },
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontFamily = styling.summaryFont?.let { FontFamily(it) } ?: FontFamily.Default,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            ),
                            label = {}
                        )

                    }
                }
            )
        }
    }
}
