package com.yuroyami.syncplay.managers.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderDefaults.colors
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuroyami.syncplay.managers.preferences.StaticPref
import com.yuroyami.syncplay.managers.preferences.set
import com.yuroyami.syncplay.managers.preferences.watchPref
import com.yuroyami.syncplay.ui.components.FlexibleIcon
import com.yuroyami.syncplay.ui.components.FlexibleText
import com.yuroyami.syncplay.ui.components.MultiChoiceDialog
import com.yuroyami.syncplay.ui.components.sairaFont
import com.yuroyami.syncplay.ui.popups.PopupColorPicker.ColorPickingPopup
import com.yuroyami.syncplay.ui.screens.adam.LocalSettingStyling
import com.yuroyami.syncplay.ui.screens.home.HomeTextField
import com.yuroyami.syncplay.ui.theme.Theming
import com.yuroyami.syncplay.ui.theme.Theming.flexibleGradient
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
 * @property icon Optional icon displayed next to the setting title
 * @property enabled Whether the setting is interactive (default: true)
 */
sealed class Setting<T>(
    val type: SettingType = SettingType.OneClickSettingType, val key: StaticPref<T>,
    val title: StringResource, val summary: StringResource,
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

    @Composable
    fun BaseSettingComposable(
        icon: ImageVector?,
        title: String,
        summary: String,
        trailingElement: @Composable (() -> Unit)? = null,
        supportingElement: @Composable (ColumnScope.() -> Unit)? = null,
        onClick: (() -> Unit)? = null
    ) {
        val styling = LocalSettingStyling.current

        Column(
            modifier = Modifier.fillMaxWidth().clickable(
                interactionSource = null,
                indication = ripple(bounded = true, color = Theming.SP_ORANGE),
                onClick = { onClick?.let { it() } }
            ).padding(styling.paddingUsed.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                verticalAlignment = CenterVertically
            ) {
                FlexibleIcon(
                    modifier = Modifier.align(Alignment.Top),
                    tintColors = listOf(MaterialTheme.colorScheme.primary),
                    shadowColors = flexibleGradient.map { it.copy(alpha = 0.25f) },
                    icon = icon ?: Icons.Filled.Settings, size = styling.iconSize
                )

                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    FlexibleText(
                        text = title,
                        fillingColors = listOf(MaterialTheme.colorScheme.primary),
                        strokeColors = listOf(MaterialTheme.colorScheme.outline),
                        size = styling.titleSize,
                        font = sairaFont
                    )

                    Text(
                        modifier = Modifier,
                        text = summary,
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = styling.summarySize.sp,
                        lineHeight = (styling.summarySize + 2).sp
                    )
                }

                trailingElement?.invoke()
            }

            supportingElement?.invoke(this)
        }
    }

    /**
     * Hidden setting with no UI representation.
     *
     * Useful for storing configuration values that are not directly user-editable
     * but need to persist across app sessions.
     *
     * @property key DataStore key for this setting
     */
    class HeadlessSetting(key: StaticPref<Any>) : Setting<Any>(
        type = SettingType.HeadlessSettingType, key = key, summary = Res.string.okay, title = Res.string.okay,
        icon = null, enabled = true
    ) {
        @Composable
        override fun SettingComposable(modifier: Modifier) {
        }
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
        type: SettingType, key: StaticPref<Any>, summary: StringResource, title: StringResource,
        icon: ImageVector?, enabled: Boolean = true,
        val onClick: (() -> Unit)? = null,
    ) : Setting<Any>(
        type = type, key = key, summary = summary, title = title,
        icon = icon, enabled = enabled
    ) {
        @Composable
        override fun SettingComposable(modifier: Modifier) {
            BaseSettingComposable(
                title = stringResource(title),
                summary = stringResource(summary),
                icon = icon,
                onClick = onClick
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
    @Suppress("AssignedValueIsNeverRead")
    class YesNoDialogSetting(
        type: SettingType, key: StaticPref<Boolean>, summary: StringResource, title: StringResource,
        icon: ImageVector?, enabled: Boolean = true,
        val rationale: StringResource,
        val onYes: (CoroutineScope.() -> Unit)? = null,
        val onNo: (CoroutineScope.() -> Unit)? = null
        //TODO: Show "done" message (i.e: Operation successfully carried out) in a snackbar message
    ) : Setting<Boolean>(
        type = type, key = key, summary = summary, title = title,
        icon = icon, enabled = enabled
    ) {
        @Composable
        override fun SettingComposable(modifier: Modifier) {
            val scope = rememberCoroutineScope { Dispatchers.IO }

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

            BaseSettingComposable(
                title = stringResource(title),
                summary = stringResource(summary),
                icon = icon,
                onClick = {
                    dialog = true
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
        type: SettingType, key: StaticPref<out Any>, summary: StringResource, title: StringResource,
        icon: ImageVector?, enabled: Boolean = true,
        val popupComposable: @Composable ((MutableState<Boolean>) -> Unit)? = null
    ) : Setting<Any>(
        type = type, key = key, summary = summary, title = title,
        icon = icon, enabled = enabled
    ) {
        @Composable
        override fun SettingComposable(modifier: Modifier) {
            val popupVisibility = remember { mutableStateOf(false) }

            BaseSettingComposable(
                title = stringResource(title),
                summary = stringResource(summary),
                icon = icon,
                onClick = {
                    popupVisibility.value = true
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
        type: SettingType, key: StaticPref<Boolean>, summary: StringResource, title: StringResource,
        icon: ImageVector?, enabled: Boolean = true,
        val onBooleanChanged: (Boolean) -> Unit = {},
    ) : Setting<Boolean>(
        type = type, key = key, summary = summary, title = title,
        icon = icon, enabled = enabled
    ) {
        @Composable
        override fun SettingComposable(modifier: Modifier) {
            val boolean by key.watchPref()
            val scope = rememberCoroutineScope { Dispatchers.IO }

            BaseSettingComposable(
                title = stringResource(title),
                summary = stringResource(summary),
                icon = icon,
                onClick = {
                    scope.launch {
                        key.set(!boolean)
                    }
                },
                trailingElement = {
                    if (type == SettingType.CheckboxSettingType) {
                        Checkbox(
                            checked = boolean,
                            enabled = enabled,
                            onCheckedChange = { b ->
                                scope.launch {
                                    key.set(b)
                                }
                                onBooleanChanged.invoke(b)
                            }
                        )
                    } else {
                        Switch(
                            modifier = Modifier.height(22.dp),
                            checked = boolean,
                            enabled = enabled,
                            onCheckedChange = { b ->
                                scope.launch {
                                    key.set(b)
                                }
                                onBooleanChanged.invoke(b)
                            }
                        )
                    }
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
        type: SettingType, key: StaticPref<String>, summary: StringResource, title: StringResource,
        icon: ImageVector?, enabled: Boolean = true,
        val entries: @Composable () -> Map<String, String>,
        val onItemChosen: ((value: String) -> Unit)? = null
    ) : Setting<String>(
        type = type, key = key, summary = summary, title = title,
        icon = icon, enabled = enabled
    ) {
        @Composable
        override fun SettingComposable(modifier: Modifier) {
            val actualEntries = entries.invoke()
            val selectedItem by key.watchPref()
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
                            key.set(item.value)
                            onItemChosen?.let { it(item.value) }
                        }
                    })
            }

            BaseSettingComposable(
                title = stringResource(title),
                summary = stringResource(summary),
                icon = icon,
                onClick = {
                    dialogOpen.value = true
                },
                trailingElement = {
                    Icon(imageVector = Icons.AutoMirrored.Filled.List, "", tint = MaterialTheme.colorScheme.outline)
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
        type: SettingType, key: StaticPref<Int>, summary: StringResource, title: StringResource,
        icon: ImageVector?, enabled: Boolean = true,
        val maxValue: Int = 100,
        val minValue: Int = 0,
        val onValueChanged: ((newValue: Int) -> Unit)? = null,
    ) : Setting<Int>(
        type = type, key = key, summary = summary, title = title,
        icon = icon, enabled = enabled
    ) {
        @Composable
        override fun SettingComposable(modifier: Modifier) {
            val value by key.watchPref()
            val styling = LocalSettingStyling.current
            val scope = rememberCoroutineScope { Dispatchers.IO }

            BaseSettingComposable(
                title = stringResource(title),
                summary = stringResource(summary),
                icon = icon,
                trailingElement = {
                    Text(
                        modifier = Modifier,
                        text = value.toString(),
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = styling.summarySize.sp,
                        lineHeight = (styling.summarySize + 2).sp
                    )
                },
                supportingElement = {
                    Slider(
                        value = value.toFloat(),
                        enabled = enabled,
                        valueRange = (minValue.toFloat())..(maxValue.toFloat()),
                        onValueChange = { f ->
                            if (f != value.toFloat()) {
                                onValueChanged?.invoke(f.roundToInt())
                            }

                            scope.launch {
                                key.set(f.roundToInt())
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        thumb = { state ->
                            SliderDefaults.Thumb(
                                interactionSource = remember { MutableInteractionSource() },
                                sliderState = state,
                                modifier = Modifier,
                                colors = colors(),
                                thumbSize = DpSize(width = 8.dp, height = 24.dp)
                            )
                        },
                        track = { state ->
                            SliderDefaults.Track(
                                sliderState = state,
                                thumbTrackGapSize = 1.dp
                            )
                        }
                    )
                }
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
        type: SettingType, key: StaticPref<Int>, summary: StringResource, title: StringResource,
        icon: ImageVector?, enabled: Boolean = true
    ) : Setting<Int>(
        type = type, key = key, summary = summary, title = title,
        icon = icon, enabled = enabled
    ) {
        @Composable
        override fun SettingComposable(modifier: Modifier) {
            val color by key.watchPref()
            val colorDialogState = remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope { Dispatchers.IO }

            BaseSettingComposable(
                title = stringResource(title),
                summary = stringResource(summary),
                icon = icon,
                onClick = {
                    colorDialogState.value = true
                },
                trailingElement = {
                    Button(
                        onClick = { colorDialogState.value = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(color)),
                        modifier = Modifier.size(24.dp),
                    ) {}
                },
            )

            ColorPickingPopup(
                visibilityState = colorDialogState, initialColor = Color(color), onColorChanged = { color ->
                    scope.launch {
                        key.set(color.toArgb())
                    }
                },
                onDefaultReset = {
                    scope.launch { key.set(key.default) }
                }
            )
        }
    }

    /**
     * Setting with a text input field for entering string values.
     *
     * Displays a text field below the setting where users can type values.
     * Configured for numeric input by default but can be customized for other types.
     */
    class TextFieldSetting(
        type: SettingType, key: StaticPref<String>, summary: StringResource, title: StringResource,
        icon: ImageVector?, enabled: Boolean = true
    ) : Setting<String>(
        type = type, key = key, summary = summary, title = title,
        icon = icon, enabled = enabled
    ) {
        @Composable
        override fun SettingComposable(modifier: Modifier) {
            val string by key.watchPref()
            val scope = rememberCoroutineScope()

            BaseSettingComposable(
                title = stringResource(title),
                summary = stringResource(summary),
                icon = icon,
                onClick = {
                    scope.launch {
                        key.set(string)
                    }
                },
                supportingElement = {
                    HomeTextField(
                        modifier = Modifier.fillMaxWidth(0.5f).align(CenterHorizontally).padding(6.dp),
                        value = string,
                        onValueChange = {
                            scope.launch {
                                key.set(it)
                            }
                        },
                        type = KeyboardType.Number,
                        height = 48.dp,
                        clearFocusWhenDone = true
                    )
                }
            )
        }
    }
}