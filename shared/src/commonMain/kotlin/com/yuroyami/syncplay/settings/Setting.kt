package com.yuroyami.syncplay.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.ripple.rememberRipple
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.yuroyami.syncplay.compose.ComposeUtils.FlexibleFancyText
import com.yuroyami.syncplay.compose.ComposeUtils.MultiChoiceDialog
import com.yuroyami.syncplay.compose.ComposeUtils.SmartFancyIcon
import com.yuroyami.syncplay.compose.PopupColorPicker.ColorPickingPopup
import com.yuroyami.syncplay.datastore.booleanFlow
import com.yuroyami.syncplay.datastore.datastoreFiles
import com.yuroyami.syncplay.datastore.intFlow
import com.yuroyami.syncplay.datastore.stringFlow
import com.yuroyami.syncplay.datastore.writeBoolean
import com.yuroyami.syncplay.datastore.writeInt
import com.yuroyami.syncplay.datastore.writeString
import com.yuroyami.syncplay.ui.Paletting
import com.yuroyami.syncplay.utils.colorpicker.HsvColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
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
 * @param dependency Key string of another setting that this setting is dependent upon (Optional)
 * @param maxValue Max value of the setting if it works with numbers, 100 by default, this setting is optional.
 * @param minValue Min value of the setting, 0 by default, so this setting is optional.
 * @param entryKeys When working with multi choice setting for example, pass a list of the visible key texts
 * @param entryValues The under-the-hood values for each entryKey. It must be of same size as entryKeys.
 * @param onClick Lambda triggered upon clicking the setting, typically used for [SettingType.OneClickSetting]
 * @param onItemChosen Lambda triggered upon choosing an elemnt for a [SettingType.MultiChoicePopupSetting]
 * @param onValueChanged Lambda triggered upon changing a [SettingType.SliderSetting] value
 * @param popupComposable For a [SettingType.PopupSetting], this is a composable popup to show upon clicking
 * @param isResetDefault For a [SettingType.OneClickSetting], this one prompts the user to clear settings.
 * */
class Setting(
    val datastorekey: String,
    val type: SettingType, val key: String,
    val title: @Composable () -> String, val summary: @Composable () -> String,
    val defaultValue: Any? = null, val icon: ImageVector? = null, val enabled: Boolean = true,
    val styling: SettingStyling = SettingStyling(),
    val dependency: String = "",
    val maxValue: Int = 100, val minValue: Int = 0,
    val entryKeys: @Composable () -> List<String> = { listOf() }, val entryValues: @Composable () ->  List<String> = { listOf() },
    val onClick: (() -> Unit)? = null, val onItemChosen: ((index: Int, value: String) -> Unit)? = null,
    val onValueChanged: ((newValue: Int) -> Unit)? = null,
    val popupComposable: (@Composable (MutableState<Boolean>) -> Unit)? = null,
    val isResetDefault: Boolean = false,
) {

    /** The Composable function that creates the UI element for the setting. */
    @Composable
    fun SettingSingleton(modifier: Modifier = Modifier) {
        when (type) {
            SettingType.OneClickSetting -> OneClickSettingUI(modifier)
            SettingType.PopupSetting -> PopupSettingUI(modifier)
            SettingType.MultiChoicePopupSetting -> ListSettingUI(modifier)
            SettingType.CheckboxSetting -> BooleanSettingUI(modifier = modifier, true)
            SettingType.ToggleSetting -> BooleanSettingUI(modifier = modifier, false)
            SettingType.SliderSetting -> SliderSettingUI(modifier = modifier)
            SettingType.ColorSetting -> ColorSettingUI(modifier = modifier)
            SettingType.TextFieldSetting -> TextFieldSettingUI(modifier = modifier)

        }
    }

    @Composable
    private fun OneClickSettingUI(modifier: Modifier = Modifier) {
        //TODO: val context = LocalContext.current
        //val scope = rememberCoroutineScope()

        ListItem(
            modifier = modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(bounded = true, color = Paletting.SP_ORANGE)
                ) {
                    if (isResetDefault) {
                        /* TODO: Reset dialog
                        val resetdefaultDialog = AlertDialog.Builder(context)
                        val dialogClickListener = DialogInterface.OnClickListener { dialog, which ->
                            dialog.dismiss()
                            if (which == DialogInterface.BUTTON_POSITIVE) {
                                scope.launch(Dispatchers.IO) {
                                    datastorekey
                                        .ds()
                                        .edit { preferences ->
                                            preferences.clear()
                                        }
                                }
                            }
                        }

                        resetdefaultDialog
                            .setMessage(context.getString(R.string.setting_resetdefault_dialog))
                            .setPositiveButton(context.getString(R.string.yes), dialogClickListener)
                            .setNegativeButton(context.getString(R.string.no), dialogClickListener)
                            .show()

                         */
                    }
                    onClick?.let { it() }
                },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                SmartFancyIcon(
                    tintColors = styling.iconTints,
                    icon = icon ?: Icons.Filled.QuestionMark, size = styling.iconSize.toInt()
                )
            },
            headlineContent = {
                FlexibleFancyText(
                    text = title.invoke(),
                    fillingColors = styling.titleFilling ?: listOf(MaterialTheme.colorScheme.primary),
                    strokeColors = styling.titleStroke ?: listOf(),
                    shadowColors = styling.titleShadow ?: Paletting.SP_GRADIENT,
                    size = styling.titleSize,
                    font = styling.titleFont
                )
            },
            supportingContent = {
                Text(
                    text = summary.invoke(),
                    style = TextStyle(
                        color = styling.summaryColor,
                        fontFamily = styling.summaryFont?.let { FontFamily(it) } ?: FontFamily.Default, fontSize = styling.summarySize.sp,
                    )
                )
            }
        )
    }

    @Composable
    private fun PopupSettingUI(modifier: Modifier = Modifier) {
        val popupVisibility = remember { mutableStateOf(false) }

        ListItem(
            modifier = modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(bounded = true, color = Paletting.SP_ORANGE)

                ) {
                    popupVisibility.value = true
                },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                SmartFancyIcon(
                    tintColors = styling.iconTints,
                    shadowColors = styling.iconShadows,
                    icon = icon ?: Icons.Filled.QuestionMark, size = styling.iconSize.toInt(),
                    )
            },
            headlineContent = {
                FlexibleFancyText(
                    text = title.invoke(),
                    fillingColors = styling.titleFilling ?: listOf(MaterialTheme.colorScheme.primary),
                    strokeColors = styling.titleStroke ?: listOf(),
                    shadowColors = styling.titleShadow ?: Paletting.SP_GRADIENT,
                    size = styling.titleSize,
                    font = styling.titleFont
                )
            },
            supportingContent = {
                Text(
                    text = summary.invoke(),
                    style = TextStyle(
                        color = styling.summaryColor,
                        fontFamily = styling.summaryFont?.let { FontFamily(it) } ?: FontFamily.Default, fontSize = styling.summarySize.sp,
                    )
                )
            }
        )


        popupComposable?.invoke(popupVisibility)
    }

    /** A boolean setting UI composable function. This either creates a CheckboxSetting or a ToggleSetting
     * based on the parameter [type] that is passed. If it's true, it is a checkbox setting, otherwise, a toggle button. */
    @Composable
    private fun BooleanSettingUI(modifier: Modifier = Modifier, type: Boolean) {
        val boolean = datastore().booleanFlow(key, defaultValue as Boolean).collectAsState(initial = defaultValue)
        val scope = rememberCoroutineScope { Dispatchers.IO }

        ListItem(
            modifier = modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(bounded = true, color = Paletting.SP_ORANGE)

                ) {
                    scope.launch {
                        datastore().writeBoolean(key, !boolean.value)
                    }
                },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                SmartFancyIcon(
                    tintColors = styling.iconTints,
                    shadowColors = styling.iconShadows,
                    icon = icon ?: Icons.Filled.QuestionMark, size = styling.iconSize.toInt(),
                    )
            },
            trailingContent = {
                if (type) {
                    Checkbox(
                        checked = boolean.value,
                        enabled = enabled,
                        onCheckedChange = { b ->
                            scope.launch {
                                datastore().writeBoolean(key, b)
                            }
                        }
                    )
                } else {
                    Switch(
                        checked = boolean.value,
                        enabled = enabled,
                        onCheckedChange = { b ->
                            scope.launch {
                                datastore().writeBoolean(key, b)
                            }
                        }
                    )
                }
            },
            headlineContent = {
                FlexibleFancyText(
                    text = title.invoke(),
                    fillingColors = styling.titleFilling ?: listOf(MaterialTheme.colorScheme.primary),
                    strokeColors = styling.titleStroke ?: listOf(),
                    shadowColors = styling.titleShadow ?: Paletting.SP_GRADIENT,
                    size = styling.titleSize,
                    font = styling.titleFont
                )
            },
            supportingContent = {
                Text(
                    text = summary.invoke(),
                    style = TextStyle(
                        color = styling.summaryColor,
                        fontFamily = styling.summaryFont?.let { FontFamily(it) } ?: FontFamily.Default, fontSize = styling.summarySize.sp,
                    )
                )
            }
        )
    }

    /** A multi-choice setting which shows a popup dialog when clicked.
     * @exception SettingCreationException When [entryKeys] and [entryValues] are not passed/specified. */
    @Composable
    private fun ListSettingUI(modifier: Modifier = Modifier) {
        val dialogOpen = remember { mutableStateOf(false) }
        val selectedItem = datastore().stringFlow(key, defaultValue as String).collectAsState(initial = defaultValue)
        val scope = rememberCoroutineScope { Dispatchers.IO }

        val renderedValues = entryValues.invoke()

        if (dialogOpen.value) {
            MultiChoiceDialog(
                items = entryKeys.invoke(),
                title = title.invoke(),
                onDismiss = { dialogOpen.value = false },
                selectedItem = renderedValues.indexOf(selectedItem.value),
                onItemClick = { i ->
                    dialogOpen.value = false

                    scope.launch {
                        datastore().writeString(key, renderedValues[i])

                        onItemChosen?.let { it(i, renderedValues[i]) }

                    }
                })
        }

        ListItem(
            modifier = modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(bounded = true, color = Paletting.SP_ORANGE)

                ) {
                    dialogOpen.value = true
                },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                SmartFancyIcon(
                    tintColors = styling.iconTints,
                    shadowColors = styling.iconShadows,
                    icon = icon ?: Icons.Filled.QuestionMark, size = styling.iconSize.toInt(),
                )
            },
            headlineContent = {
                FlexibleFancyText(
                    text = title.invoke(),
                    fillingColors = styling.titleFilling ?: listOf(MaterialTheme.colorScheme.primary),
                    strokeColors = styling.titleStroke ?: listOf(),
                    shadowColors = styling.titleShadow ?: Paletting.SP_GRADIENT,
                    size = styling.titleSize,
                    font = styling.titleFont
                )
            },
            trailingContent = {
                Icon(imageVector = Icons.Default.List, "")
            },
            supportingContent = {
                Text(
                    text = summary.invoke(),
                    style = TextStyle(
                        color = styling.summaryColor,
                        fontFamily = styling.summaryFont?.let { FontFamily(it) } ?: FontFamily.Default, fontSize = styling.summarySize.sp,
                    )
                )
            }
        )
    }

    /** A slider setting which has a draggable seek bar to choose an Integer value
     * @exception SettingCreationException */
    @Composable
    private fun SliderSettingUI(modifier: Modifier = Modifier) {
        val value = datastore().intFlow(key, defaultValue as Int).collectAsState(initial = defaultValue)
        val scope = rememberCoroutineScope { Dispatchers.IO }

        ListItem(
            modifier = modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(bounded = true, color = Paletting.SP_ORANGE)

                ) {},
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                SmartFancyIcon(
                    tintColors = styling.iconTints,
                    shadowColors = styling.iconShadows,
                    icon = icon ?: Icons.Filled.QuestionMark, size = styling.iconSize.toInt()
                )
            },
            headlineContent = {
                FlexibleFancyText(
                    text = title.invoke(),
                    fillingColors = styling.titleFilling ?: listOf(MaterialTheme.colorScheme.primary),
                    strokeColors = styling.titleStroke ?: listOf(),
                    shadowColors = styling.titleShadow ?: Paletting.SP_GRADIENT,
                    size = styling.titleSize,
                    font = styling.titleFont
                )
            },
            supportingContent = {
                Column {
                    Text(
                        text = summary.invoke(),
                        style = TextStyle(
                            color = styling.summaryColor,
                            fontFamily = styling.summaryFont?.let { FontFamily(it) } ?: FontFamily.Default, fontSize = styling.summarySize.sp,
                        )
                    )

                    Row {
                        Slider(
                            value = value.value.toFloat(),
                            enabled = enabled,
                            valueRange = (minValue.toFloat())..(maxValue.toFloat()),
                            onValueChange = { f ->
                                scope.launch {
                                    datastore().writeInt(key, f.roundToInt())
                                }
                                onValueChanged?.invoke(f.roundToInt())
                            }, modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                        )

                        Text(
                            modifier = Modifier.width(84.dp),
                            text = (value.value).toString(),
                            style = TextStyle(
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = styling.summaryFont?.let { FontFamily(it) } ?: FontFamily.Default,
                                fontSize = (13).sp
                            )
                        )
                    }
                }
            }
        )
    }

    /** A color setting UI composable function. Clicking this would displays a popup to the user. */
    @Composable
    private fun ColorSettingUI(modifier: Modifier = Modifier) {
        val color = datastore().intFlow(key, defaultValue as Int).collectAsState(initial = defaultValue)
        val colorDialogState = remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope { Dispatchers.IO }

        ListItem(
            modifier = modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(bounded = true, color = Paletting.SP_ORANGE)

                ) {
                    colorDialogState.value = true
                },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                SmartFancyIcon(
                    tintColors = styling.iconTints,
                    shadowColors = styling.iconShadows,
                    icon = icon ?: Icons.Filled.QuestionMark, size = styling.iconSize.toInt()
                )
            },
            headlineContent = {
                FlexibleFancyText(
                    text = title.invoke(),
                    fillingColors = styling.titleFilling ?: listOf(MaterialTheme.colorScheme.primary),
                    strokeColors = styling.titleStroke ?: listOf(),
                    shadowColors = styling.titleShadow ?: Paletting.SP_GRADIENT,
                    size = styling.titleSize,
                    font = styling.titleFont
                )
            },
            trailingContent = {
                Button(
                    onClick = { colorDialogState.value = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(color.value)),
                    modifier = Modifier.size(24.dp)
                ) {}
            },
            supportingContent = {
                Text(
                    text = summary.invoke(),
                    style = TextStyle(
                        color = styling.summaryColor,
                        fontFamily = styling.summaryFont?.let { FontFamily(it) } ?: FontFamily.Default, fontSize = styling.summarySize.sp,
                    )
                )
            }
        )

        ColorPickingPopup(colorDialogState, initialColor = HsvColor.from(Color(color.value))) { hsvColor ->
            scope.launch {
                datastorekey.writeInt(key, hsvColor.toColor().toArgb())
            }
        }
    }

    /** A string setting UI composable function. It has a textfield next to it */
    @Composable
    private fun TextFieldSettingUI(modifier: Modifier = Modifier) {
        val string by datastore().stringFlow(key, defaultValue as String).collectAsState(initial = defaultValue)
        val scope = rememberCoroutineScope()
        val focusManager = LocalFocusManager.current

        ListItem(
            modifier = modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(bounded = true, color = Paletting.SP_ORANGE)

                ) {
                    scope.launch {
                        datastore().writeString(key, string)
                    }
                },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                SmartFancyIcon(
                    tintColors = styling.iconTints,
                    shadowColors = styling.iconShadows,
                    icon = icon ?: Icons.Filled.QuestionMark, size = styling.iconSize.toInt(),
                    )
            },
            trailingContent = {
                TextField(
                    modifier = Modifier.width(64.dp),
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
                            datastore().writeString(key, it)
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
            },
            headlineContent = {
                FlexibleFancyText(
                    text = title.invoke(),
                    fillingColors = styling.titleFilling ?: listOf(MaterialTheme.colorScheme.primary),
                    strokeColors = styling.titleStroke ?: listOf(),
                    shadowColors = styling.titleShadow ?: Paletting.SP_GRADIENT,
                    size = styling.titleSize,
                    font = styling.titleFont
                )
            },
            supportingContent = {
                Text(
                    text = summary.invoke(),
                    style = TextStyle(
                        color = styling.summaryColor,
                        fontFamily = styling.summaryFont?.let { FontFamily(it) } ?: FontFamily.Default, fontSize = styling.summarySize.sp,
                    )
                )
            }
        )
    }


    /** Returns the global instance of the datastore that stores this setting */
    fun datastore(): DataStore<Preferences> {
        return datastoreFiles[datastorekey]!!
    }
}
