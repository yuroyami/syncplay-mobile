package com.yuroyami.syncplay.managers.settings

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
import com.yuroyami.syncplay.managers.datastore.valueAsState
import com.yuroyami.syncplay.managers.datastore.writeValue
import com.yuroyami.syncplay.ui.components.FlexibleIcon
import com.yuroyami.syncplay.ui.components.FlexibleText
import com.yuroyami.syncplay.ui.components.MultiChoiceDialog
import com.yuroyami.syncplay.ui.popups.PopupColorPicker.ColorPickingPopup
import com.yuroyami.syncplay.ui.screens.adam.LocalSettingStyling
import com.yuroyami.syncplay.ui.theme.Theming
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.no
import syncplaymobile.shared.generated.resources.okay
import syncplaymobile.shared.generated.resources.yes
import kotlin.math.roundToInt

/**
 * Base class for creating configurable settings with UI representations.
 *
 * Provides a type-safe framework for building settings screens with various input types
 * including toggles, sliders, text fields, color pickers, and multi-choice dialogs.
 * All settings automatically persist to DataStore and reactively update the UI.
 *
 * ## Setting Types
 * - [HeadlessSetting] - No UI, just stores a value
 * - [OneClickSetting] - Simple clickable setting that triggers an action
 * - [YesNoDialogSetting] - Shows a confirmation dialog before executing an action
 * - [PopupSetting] - Opens a custom popup when clicked
 * - [BooleanSetting] - Checkbox or toggle switch for boolean values
 * - [MultiChoiceSetting] - Dropdown/dialog for selecting from multiple options
 * - [SliderSetting] - Slider for numeric values within a range
 * - [ColorSetting] - Color picker for choosing colors
 * - [TextFieldSetting] - Text input field for string values
 *
 * @param T The type of value this setting stores
 * @property type The specific setting type (determines UI representation)
 * @property key Unique identifier for persisting this setting to storage
 * @property title Display title shown to the user (localized string resource)
 * @property summary Description explaining what the setting does (localized string resource)
 * @property defaultValue The default value if no stored value exists
 * @property icon Optional icon displayed next to the setting title
 * @property enabled Whether the setting is interactive (default: true)
 */
sealed class Setting<T>(
    val type: SettingType = SettingType.OneClickSettingType, val key: String = "",
    val title: StringResource, val summary: StringResource,
    val defaultValue: T,
    val icon: ImageVector? = null, val enabled: Boolean = true,
) {
    /**
     * Renders the Compose UI for this setting.
     *
     * Each setting type implements this to provide its specific UI representation.
     * The composable reads from and writes to DataStore automatically.
     *
     * @param modifier Compose modifier for styling and layout
     */
    @Composable
    abstract fun SettingComposable(modifier: Modifier)

    /**
     * Hidden setting with no UI representation.
     *
     * Useful for storing configuration values that are not directly user-editable
     * but need to persist across app sessions.
     *
     * @property key DataStore key for this setting
     * @property defaultValue The default value to use
     */
    class HeadlessSetting(key: String, defaultValue: Any): Setting<Any>(
        type = SettingType.HeadlessSettingType, key = key, summary = Res.string.okay, title = Res.string.okay, defaultValue = defaultValue,
        icon = null, enabled = true
    ) {
        @Composable
        override fun SettingComposable(modifier: Modifier) {}
    }

    /**
     * Simple clickable setting that triggers a callback when tapped.
     *
     * Displays as a list item with title, summary, and optional icon. Does not
     * store any value - purely for triggering actions like navigation or dialogs.
     *
     * @property onClick Callback invoked when the setting is tapped
     */
    class OneClickSetting(
        type: SettingType, key: String, summary: StringResource, title: StringResource, defaultValue: Any = Any(),
        icon: ImageVector?, enabled: Boolean = true,
        val onClick: (() -> Unit)? = null,
    ) : Setting<Any>(
        type = type, key = key, summary = summary, title = title, defaultValue = defaultValue,
        icon = icon, enabled = enabled
    ) {
        @Composable
        override fun SettingComposable(modifier: Modifier) {
            val styling = LocalSettingStyling.current

            ListItem(
                modifier = modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = Theming.SP_ORANGE)
                    ) {
                        onClick?.let { it() }
                    },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),

                headlineContent = {
                    Row(verticalAlignment = CenterVertically) {
                        FlexibleIcon(
                            tintColors = styling.iconTints,
                            icon = icon ?: Icons.Filled.QuestionMark, size = styling.iconSize.toInt()
                        )
                        FlexibleText(
                            text = stringResource(title),
                            fillingColors = styling.titleFilling ?: listOf(MaterialTheme.colorScheme.primary),
                            strokeColors = styling.titleStroke ?: listOf(),
                            shadowColors = styling.titleShadow ?: Theming.SP_GRADIENT,
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

    /**
     * Setting that shows a Yes/No confirmation dialog before executing an action.
     *
     * Useful for destructive or important actions that require user confirmation.
     * Displays a rationale message explaining what will happen.
     *
     * @property rationale Message explaining the action (shown in the dialog)
     * @property onYes Callback invoked when user confirms with "Yes"
     * @property onNo Callback invoked when user cancels with "No"
     */
    class YesNoDialogSetting(
        type: SettingType, key: String, summary: StringResource, title: StringResource, defaultValue: Any = Any(),
        icon: ImageVector?, enabled: Boolean = true,
        val rationale: StringResource,
        val onYes: (CoroutineScope.() -> Unit)? = null,
        val onNo: (CoroutineScope.() -> Unit)? = null
        //TODO: Show "done" message (i.e: Operation successfully carried out) in a snackbar message
    ) : Setting<Any>(
        type = type, key = key, summary = summary, title = title, defaultValue = defaultValue,
        icon = icon, enabled = enabled
    ) {
        @Composable
        override fun SettingComposable(modifier: Modifier) {
            val scope = rememberCoroutineScope { Dispatchers.IO }
            val styling = LocalSettingStyling.current

            var dialog by remember { mutableStateOf(false) }
            if (dialog) {
                AlertDialog(
                    onDismissRequest = { dialog = false },
                    confirmButton = {
                        TextButton(onClick = {
                            dialog = false
                            onYes?.invoke(scope)
                        }) { Text(stringResource(Res.string.yes)) }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            dialog = false
                            onNo?.invoke(scope)
                        }) { Text(stringResource(Res.string.no)) }
                    },
                    text = { Text(stringResource(rationale)) }
                )
            }
            ListItem(
                modifier = modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = Theming.SP_ORANGE)
                    ) {
                        dialog = true
                    },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = {
                    Row(
                        verticalAlignment = CenterVertically
                    ){
                        FlexibleIcon(
                            tintColors = styling.iconTints,
                            icon = icon ?: Icons.Filled.QuestionMark, size = styling.iconSize.toInt()
                        )
                        FlexibleText(
                            text = stringResource(title),
                            fillingColors = styling.titleFilling ?: listOf(MaterialTheme.colorScheme.primary),
                            strokeColors = styling.titleStroke ?: listOf(),
                            shadowColors = styling.titleShadow ?: Theming.SP_GRADIENT,
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

    /**
     * Setting that displays a custom popup when clicked.
     *
     * Provides maximum flexibility by allowing you to define any composable
     * as the popup content. Useful for complex configuration UIs.
     *
     * @property popupComposable The composable to show as a popup, receives visibility state
     */
    class PopupSetting(
        type: SettingType, key: String, summary: StringResource, title: StringResource, defaultValue: Any = Any(),
        icon: ImageVector?, enabled: Boolean = true,
        val popupComposable: @Composable ((MutableState<Boolean>) -> Unit)? = null
    ) : Setting<Any>(
        type = type, key = key, summary = summary, title = title, defaultValue = defaultValue,
        icon = icon, enabled = enabled
    ) {
        @Composable
        override fun SettingComposable(modifier: Modifier) {
            val styling = LocalSettingStyling.current
            val popupVisibility = remember { mutableStateOf(false) }

            ListItem(
                modifier = modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = Theming.SP_ORANGE)

                    ) {
                        popupVisibility.value = true
                    },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),

                headlineContent = {
                    Row(
                        verticalAlignment = CenterVertically
                    ){
                        FlexibleIcon(
                            tintColors = styling.iconTints,
                            shadowColors = styling.iconShadows,
                            icon = icon ?: Icons.Filled.QuestionMark, size = styling.iconSize.toInt(),
                        )
                        FlexibleText(
                            text = stringResource(title),
                            fillingColors = styling.titleFilling ?: listOf(MaterialTheme.colorScheme.primary),
                            strokeColors = styling.titleStroke ?: listOf(),
                            shadowColors = styling.titleShadow ?: Theming.SP_GRADIENT,
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

    /**
     * Boolean setting rendered as either a checkbox or toggle switch.
     *
     * Automatically persists the boolean value to DataStore and updates reactively.
     * The UI type (checkbox vs switch) is determined by the [type] parameter.
     *
     * @property onBooleanChanged Callback invoked when the value changes
     */
    class BooleanSetting(
        type: SettingType, key: String, summary: StringResource, title: StringResource, defaultValue: Boolean,
        icon: ImageVector?, enabled: Boolean = true,
        val onBooleanChanged: (Boolean) -> Unit = {},
    ) : Setting<Boolean>(
        type = type, key = key, summary = summary, title = title, defaultValue = defaultValue,
        icon = icon, enabled = enabled
    ) {
        @Composable
        override fun SettingComposable(modifier: Modifier) {
            val boolean by key.valueAsState(defaultValue)

            val styling = LocalSettingStyling.current
            val scope = rememberCoroutineScope { Dispatchers.IO }

            ListItem(
                modifier = modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = Theming.SP_ORANGE)

                    ) {
                        scope.launch {
                            writeValue(key, !boolean)
                        }
                    },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),

                trailingContent = {
                    if (type == SettingType.CheckboxSettingType) {
                        Checkbox(
                            checked = boolean,
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
                            checked = boolean,
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
                        FlexibleIcon(
                            tintColors = styling.iconTints,
                            shadowColors = styling.iconShadows,
                            icon = icon ?: Icons.Filled.QuestionMark, size = styling.iconSize.toInt(),
                        )
                        FlexibleText(
                            text = stringResource(title),
                            fillingColors = styling.titleFilling ?: listOf(MaterialTheme.colorScheme.primary),
                            strokeColors = styling.titleStroke ?: listOf(),
                            shadowColors = styling.titleShadow ?: Theming.SP_GRADIENT,
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

    /**
     * Setting that displays a dialog for selecting from multiple options.
     *
     * Shows a list of choices in a dialog, allowing the user to pick one option.
     * The selected value is persisted automatically. Entries are provided dynamically
     * via a composable lambda to support runtime-generated options.
     *
     * @property entries Composable lambda returning a map of key-value pairs (key = internal value, value = display text)
     * @property onItemChosen Callback invoked when a new option is selected
     */
    class MultiChoiceSetting(
        type: SettingType, key: String, summary: StringResource, title: StringResource, defaultValue: String,
        icon: ImageVector?, enabled: Boolean = true,
        val entries: @Composable () -> Map<String, String>,
        val onItemChosen: ((value: String) -> Unit)? = null
    ) : Setting<String>(
        type = type, key = key, summary = summary, title = title, defaultValue = defaultValue,
        icon = icon, enabled = enabled
    ) {
        @Composable
        override fun SettingComposable(modifier: Modifier) {
            val actualEntries = entries.invoke()

            val selectedItem by key.valueAsState(defaultValue)

            val styling = LocalSettingStyling.current
            val dialogOpen = remember { mutableStateOf(false) }

            val scope = rememberCoroutineScope { Dispatchers.IO }

            if (dialogOpen.value) {
                MultiChoiceDialog(
                    items = actualEntries,
                    title = stringResource(title),
                    onDismiss = { dialogOpen.value = false },
                    selectedItem = actualEntries.entries.first { it.value == selectedItem },
                    onItemClick = { item ->
                        dialogOpen.value = false

                        scope.launch {
                            writeValue(key, item.value)
                            onItemChosen?.let { it(item.value) }
                        }
                    })
            }

            ListItem(
                modifier = modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = Theming.SP_ORANGE)

                    ) {
                        dialogOpen.value = true
                    },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = {
                    Row(verticalAlignment = CenterVertically) {
                        FlexibleIcon(
                            tintColors = styling.iconTints,
                            shadowColors = styling.iconShadows,
                            icon = icon ?: Icons.Filled.QuestionMark, size = styling.iconSize.toInt(),
                        )
                        FlexibleText(
                            text = stringResource(title),
                            fillingColors = styling.titleFilling ?: listOf(MaterialTheme.colorScheme.primary),
                            strokeColors = styling.titleStroke ?: listOf(),
                            shadowColors = styling.titleShadow ?: Theming.SP_GRADIENT,
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

    /**
     * Setting with a slider for selecting numeric values within a range.
     *
     * Displays a slider with minimum and maximum bounds. The current value is
     * shown next to the title and updates in real-time as the slider moves.
     *
     * @property maxValue Maximum slider value (inclusive)
     * @property minValue Minimum slider value (inclusive)
     * @property onValueChanged Callback invoked when the value changes
     */
    class SliderSetting(
        type: SettingType, key: String, summary: StringResource, title: StringResource, defaultValue: Int,
        icon: ImageVector?, enabled: Boolean = true,
        val maxValue: Int = 100,
        val minValue: Int = 0,
        val onValueChanged: ((newValue: Int) -> Unit)? = null,
    ) : Setting<Int>(
        type = type, key = key, summary = summary, title = title, defaultValue = defaultValue,
        icon = icon, enabled = enabled
    ) {
        @Composable
        override fun SettingComposable(modifier: Modifier) {
            val value by key.valueAsState(defaultValue)

            val styling = LocalSettingStyling.current
            val scope = rememberCoroutineScope { Dispatchers.IO }

            ListItem(
                modifier = modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = Theming.SP_ORANGE)

                    ) {},
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = {
                    Row(verticalAlignment = CenterVertically) {
                        FlexibleIcon(
                            tintColors = styling.iconTints,
                            shadowColors = styling.iconShadows,
                            icon = icon ?: Icons.Filled.QuestionMark, size = styling.iconSize.toInt()
                        )

                        FlexibleText(
                            text = stringResource(title),
                            fillingColors = styling.titleFilling ?: listOf(MaterialTheme.colorScheme.primary),
                            strokeColors = styling.titleStroke ?: listOf(),
                            shadowColors = styling.titleShadow ?: Theming.SP_GRADIENT,
                            size = styling.titleSize,
                            font = styling.titleFont
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = (value).toString(),
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
                            value = value.toFloat(),
                            enabled = enabled,
                            valueRange = (minValue.toFloat())..(maxValue.toFloat()),
                            onValueChange = { f ->
                                if (f != value.toFloat()) {
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

    /**
     * Setting with a color picker for selecting colors.
     *
     * Displays a colored button showing the current color. Clicking opens a
     * color picker popup where the user can choose a new color. The color
     * is stored as an ARGB integer.
     */
    class ColorSetting(
        type: SettingType, key: String, summary: StringResource, title: StringResource, defaultValue: Int,
        icon: ImageVector?, enabled: Boolean = true
    ) : Setting<Int>(
        type = type, key = key, summary = summary, title = title, defaultValue = defaultValue,
        icon = icon, enabled = enabled
    ) {
        @Composable
        override fun SettingComposable(modifier: Modifier) {
            val color by key.valueAsState(defaultValue)

            val styling = LocalSettingStyling.current
            val colorDialogState = remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope { Dispatchers.IO }

            ListItem(
                modifier = modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = Theming.SP_ORANGE)

                    ) {
                        colorDialogState.value = true
                    },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = {
                    Row(verticalAlignment = CenterVertically) {
                        FlexibleIcon(
                            tintColors = styling.iconTints,
                            shadowColors = styling.iconShadows,
                            icon = icon ?: Icons.Filled.QuestionMark, size = styling.iconSize.toInt()
                        )
                        FlexibleText(
                            text = stringResource(title),
                            fillingColors = styling.titleFilling ?: listOf(MaterialTheme.colorScheme.primary),
                            strokeColors = styling.titleStroke ?: listOf(),
                            shadowColors = styling.titleShadow ?: Theming.SP_GRADIENT,
                            size = styling.titleSize,
                            font = styling.titleFont
                        )
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = { colorDialogState.value = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(color)),
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

            ColorPickingPopup(colorDialogState, initialColor = Color(color), onColorChanged = { color ->
                scope.launch {
                    writeValue(key, color.toArgb())
                }
            }, onDefaultReset = { scope.launch { writeValue(key, defaultValue) } })
        }
    }

    /**
     * Setting with a text input field for entering string values.
     *
     * Displays a text field below the setting where users can type values.
     * Configured for numeric input by default but can be customized for other types.
     */
    class TextFieldSetting(
        type: SettingType, key: String, summary: StringResource, title: StringResource, defaultValue: String,
        icon: ImageVector?, enabled: Boolean = true
    ) : Setting<String>(
        type = type, key = key, summary = summary, title = title, defaultValue = defaultValue,
        icon = icon, enabled = enabled
    ) {
        @Composable
        override fun SettingComposable(modifier: Modifier) {
            val string by key.valueAsState(defaultValue)

            val styling = LocalSettingStyling.current
            val scope = rememberCoroutineScope()
            val focusManager = LocalFocusManager.current

            ListItem(
                modifier = modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = Theming.SP_ORANGE)

                    ) {
                        scope.launch {
                            writeValue(key, string)
                        }
                    },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),

                headlineContent = {
                    Row(verticalAlignment = CenterVertically) {
                        FlexibleIcon(
                            tintColors = styling.iconTints,
                            shadowColors = styling.iconShadows,
                            icon = icon ?: Icons.Filled.QuestionMark, size = styling.iconSize.toInt(),
                        )
                        FlexibleText(
                            text = stringResource(title),
                            fillingColors = styling.titleFilling ?: listOf(MaterialTheme.colorScheme.primary),
                            strokeColors = styling.titleStroke ?: listOf(),
                            shadowColors = styling.titleShadow ?: Theming.SP_GRADIENT,
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