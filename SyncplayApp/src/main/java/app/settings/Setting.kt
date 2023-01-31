package app.settings

import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import app.R
import app.compose.PopupColorPicker.ColorPickingPopup
import app.datastore.DataStoreUtils
import app.datastore.DataStoreUtils.booleanFlow
import app.datastore.DataStoreUtils.ds
import app.datastore.DataStoreUtils.intFlow
import app.datastore.DataStoreUtils.stringFlow
import app.datastore.DataStoreUtils.writeBoolean
import app.datastore.DataStoreUtils.writeInt
import app.datastore.DataStoreUtils.writeString
import app.ui.Paletting
import app.utils.ComposeUtils.FlexibleFancyText
import app.utils.ComposeUtils.MultiChoiceDialog
import app.utils.ComposeUtils.SmartFancyIcon
import com.godaddy.android.colorpicker.HsvColor
import com.godaddy.android.colorpicker.toColorInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalTextApi::class)
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
    val type: SettingType, val key: String, val title: String, val summary: String,
    val defaultValue: Any? = null, val icon: ImageVector? = null, val enabled: Boolean = true,
    val styling: SettingStyling = SettingStyling(),
    val dependency: String = "",
    val maxValue: Int = 100, val minValue: Int = 0,
    val entryKeys: List<String> = listOf(), val entryValues: List<String> = listOf(),
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
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        ConstraintLayout(
            modifier = modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(bounded = true, color = Paletting.SP_ORANGE)
                ) {
                    if (isResetDefault) {
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
                    }
                    onClick?.let { it() }
                }
        ) {
            val (ic, ttl, smry, spacer) = createRefs()

            SmartFancyIcon(
                tintColors = styling.iconTints,
                icon = icon ?: Icons.Filled.QuestionMark, size = styling.iconSize.toInt(),
                modifier = Modifier.constrainAs(ic) {
                    top.linkTo(parent.top, styling.paddingUsed.dp)
                    start.linkTo(parent.start, styling.paddingUsed.dp)
                })

            FlexibleFancyText(
                modifier = Modifier.constrainAs(ttl) {
                    top.linkTo(ic.top)
                    start.linkTo(ic.end, 6.dp)
                    end.linkTo(parent.end, styling.paddingUsed.dp)
                    width = Dimension.fillToConstraints
                },
                text = title,
                fillingColors = styling.titleFilling ?: listOf(MaterialTheme.colorScheme.primary),
                strokeColors = styling.titleStroke ?: listOf(),
                shadowColors = styling.titleShadow ?: Paletting.SP_GRADIENT,
                size = styling.titleSize,
                font = styling.titleFont
            )

            Text(
                modifier = Modifier.constrainAs(smry) {
                    top.linkTo(ttl.bottom)
                    start.linkTo(ttl.start)
                    end.linkTo(parent.end, styling.paddingUsed.dp)
                    width = Dimension.fillToConstraints
                },
                text = summary,
                style = TextStyle(
                    color = styling.summaryColor,
                    fontFamily = FontFamily(styling.summaryFont), fontSize = styling.summarySize.sp,
                )
            )

            Spacer(modifier = Modifier
                .height(styling.paddingUsed.dp)
                .constrainAs(spacer) {
                    top.linkTo(smry.bottom)
                })
        }
    }

    @Composable
    private fun PopupSettingUI(modifier: Modifier = Modifier) {
        val popupVisibility = remember { mutableStateOf(false) }

        ConstraintLayout(
            modifier = modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(bounded = true, color = Paletting.SP_ORANGE)

                ) {
                    popupVisibility.value = true
                }
        ) {
            val (ic, ttl, smry, spacer) = createRefs()

            SmartFancyIcon(
                tintColors = styling.iconTints,
                shadowColors = styling.iconShadows,
                icon = icon ?: Icons.Filled.QuestionMark, size = styling.iconSize.toInt(),
                modifier = Modifier.constrainAs(ic) {
                    top.linkTo(parent.top, styling.paddingUsed.dp)
                    start.linkTo(parent.start, styling.paddingUsed.dp)
                })

            FlexibleFancyText(
                modifier = Modifier.constrainAs(ttl) {
                    top.linkTo(ic.top)
                    start.linkTo(ic.end, 6.dp)
                    end.linkTo(parent.end, styling.paddingUsed.dp)
                    width = Dimension.fillToConstraints
                },
                text = title,
                fillingColors = styling.titleFilling ?: listOf(MaterialTheme.colorScheme.primary),
                strokeColors = styling.titleStroke ?: listOf(),
                shadowColors = styling.titleShadow ?: Paletting.SP_GRADIENT,
                size = styling.titleSize,
                font = styling.titleFont
            )

            Text(
                modifier = Modifier.constrainAs(smry) {
                    top.linkTo(ttl.bottom)
                    start.linkTo(ttl.start)
                    end.linkTo(parent.end, styling.paddingUsed.dp)
                    width = Dimension.fillToConstraints
                },
                text = summary,
                style = TextStyle(
                    color = styling.summaryColor,
                    fontFamily = FontFamily(styling.summaryFont), fontSize = styling.summarySize.sp,
                )
            )

            Spacer(modifier = Modifier
                .height(styling.paddingUsed.dp)
                .constrainAs(spacer) {
                    top.linkTo(smry.bottom)
                })
        }


        popupComposable?.invoke(popupVisibility)
    }

    /** A boolean setting UI composable function. This either creates a CheckboxSetting or a ToggleSetting
     * based on the parameter [type] that is passed. If it's true, it is a checkbox setting, otherwise, a toggle button. */
    @Composable
    private fun BooleanSettingUI(modifier: Modifier = Modifier, type: Boolean) {
        val boolean = datastore().booleanFlow(key, defaultValue as Boolean).collectAsState(initial = defaultValue)
        val scope = rememberCoroutineScope()

        ConstraintLayout(
            modifier = modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(bounded = true, color = Paletting.SP_ORANGE)

                ) {
                    scope.launch {
                        datastore().writeBoolean(key, !boolean.value)
                    }
                }
        ) {

            val (ic, ttl, smry, utility, spacer) = createRefs()

            SmartFancyIcon(
                tintColors = styling.iconTints,
                shadowColors = styling.iconShadows,
                icon = icon ?: Icons.Filled.QuestionMark, size = styling.iconSize.toInt(),
                modifier = Modifier.constrainAs(ic) {
                    top.linkTo(parent.top, styling.paddingUsed.dp)
                    start.linkTo(parent.start, styling.paddingUsed.dp)
                })

            val m = Modifier.constrainAs(utility) {
                top.linkTo(ttl.top)
                end.linkTo(parent.end, styling.paddingUsed.dp)
                bottom.linkTo(smry.bottom)
            }

            if (type) {
                Checkbox(
                    checked = boolean.value,
                    enabled = enabled,
                    onCheckedChange = { b ->
                        scope.launch {
                            datastore().writeBoolean(key, b)
                        }
                    },
                    modifier = m
                )
            } else {
                Switch(
                    checked = boolean.value,
                    enabled = enabled,
                    onCheckedChange = { b ->
                        scope.launch {
                            datastore().writeBoolean(key, b)
                        }
                    },
                    modifier = m
                )
            }

            FlexibleFancyText(
                modifier = Modifier.constrainAs(ttl) {
                    top.linkTo(ic.top)
                    start.linkTo(ic.end, 6.dp)
                    end.linkTo(utility.start, 6.dp)
                    width = Dimension.fillToConstraints
                },
                text = title,
                fillingColors = styling.titleFilling ?: listOf(MaterialTheme.colorScheme.primary),
                strokeColors = styling.titleStroke ?: listOf(),
                shadowColors = styling.titleShadow ?: Paletting.SP_GRADIENT,
                size = styling.titleSize,
                font = styling.titleFont
            )

            Text(
                modifier = Modifier.constrainAs(smry) {
                    top.linkTo(ttl.bottom)
                    start.linkTo(ttl.start)
                    end.linkTo(utility.start, 6.dp)
                    width = Dimension.fillToConstraints
                },
                text = summary,
                style = TextStyle(
                    color = styling.summaryColor,
                    fontFamily = FontFamily(styling.summaryFont), fontSize = styling.summarySize.sp,
                )
            )

            Spacer(modifier = Modifier
                .height(styling.paddingUsed.dp)
                .constrainAs(spacer) {
                    top.linkTo(smry.bottom)
                })
        }
    }

    /** A multi-choice setting which shows a popup dialog when clicked.
     * @exception SettingCreationException When [entryKeys] and [entryValues] are not passed/specified. */
    @Composable
    private fun ListSettingUI(modifier: Modifier = Modifier) {
        val dialogOpen = remember { mutableStateOf(false) }
        val selectedItem = datastore().stringFlow(key, defaultValue as String).collectAsState(initial = defaultValue)
        val scope = rememberCoroutineScope()

        if (dialogOpen.value) {
            MultiChoiceDialog(
                items = entryKeys,
                title = title,
                onDismiss = { dialogOpen.value = false },
                selectedItem = entryValues.indexOf(selectedItem.value),
                onItemClick = { i ->
                    onItemChosen?.let { it(i, entryValues[i]) }
                    scope.launch {
                        datastore().writeString(key, entryValues[i])
                    }
                })
        }

        ConstraintLayout(
            modifier = modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(bounded = true, color = Paletting.SP_ORANGE)

                ) {
                    dialogOpen.value = true
                }
        ) {

            val (ic, ttl, smry, utility, spacer) = createRefs()

            SmartFancyIcon(
                tintColors = styling.iconTints,
                shadowColors = styling.iconShadows,
                icon = icon ?: Icons.Filled.QuestionMark, size = styling.iconSize.toInt(),
                modifier = Modifier.constrainAs(ic) {
                    top.linkTo(parent.top, styling.paddingUsed.dp)
                    start.linkTo(parent.start, styling.paddingUsed.dp)
                })

            FlexibleFancyText(
                modifier = Modifier.constrainAs(ttl) {
                    top.linkTo(ic.top)
                    start.linkTo(ic.end, 6.dp)
                    end.linkTo(utility.start, 6.dp)
                    width = Dimension.fillToConstraints
                },
                text = title,
                fillingColors = styling.titleFilling ?: listOf(MaterialTheme.colorScheme.primary),
                strokeColors = styling.titleStroke ?: listOf(),
                shadowColors = styling.titleShadow ?: Paletting.SP_GRADIENT,
                size = styling.titleSize,
                font = styling.titleFont
            )

            Icon(imageVector = Icons.Default.List, "", modifier = Modifier.constrainAs(utility) {
                top.linkTo(ttl.top)
                end.linkTo(parent.end, styling.paddingUsed.dp)
                bottom.linkTo(smry.bottom)
            })


            Text(
                modifier = Modifier.constrainAs(smry) {
                    top.linkTo(ttl.bottom)
                    start.linkTo(ttl.start)
                    end.linkTo(utility.start, 6.dp)
                    width = Dimension.fillToConstraints
                },
                text = summary,
                style = TextStyle(
                    color = styling.summaryColor,
                    fontFamily = FontFamily(styling.summaryFont), fontSize = styling.summarySize.sp,
                )
            )

            Spacer(modifier = Modifier
                .height(styling.paddingUsed.dp)
                .constrainAs(spacer) {
                    top.linkTo(smry.bottom)
                })
        }
    }

    /** A slider setting which has a draggable seek bar to choose an Integer value
     * @exception SettingCreationException */
    @Composable
    private fun SliderSettingUI(modifier: Modifier = Modifier) {
        val value = datastore().intFlow(key, defaultValue as Int).collectAsState(initial = defaultValue)
        val scope = rememberCoroutineScope()

        ConstraintLayout(
            modifier = modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(bounded = true, color = Paletting.SP_ORANGE)

                ) {

                }
        ) {

            val (ic, ttl, smry, slider, slidervalue, spacer) = createRefs()

            SmartFancyIcon(
                tintColors = styling.iconTints,
                shadowColors = styling.iconShadows,
                icon = icon ?: Icons.Filled.QuestionMark, size = styling.iconSize.toInt(),
                modifier = Modifier.constrainAs(ic) {
                    top.linkTo(parent.top, styling.paddingUsed.dp)
                    start.linkTo(parent.start, styling.paddingUsed.dp)
                })

            FlexibleFancyText(
                modifier = Modifier.constrainAs(ttl) {
                    top.linkTo(ic.top)
                    start.linkTo(ic.end, 6.dp)
                    end.linkTo(parent.end, styling.paddingUsed.dp)
                    width = Dimension.fillToConstraints
                },
                text = title,
                fillingColors = styling.titleFilling ?: listOf(MaterialTheme.colorScheme.primary),
                strokeColors = styling.titleStroke ?: listOf(),
                shadowColors = styling.titleShadow ?: Paletting.SP_GRADIENT,
                size = styling.titleSize,
                font = styling.titleFont
            )

            Text(
                modifier = Modifier.constrainAs(smry) {
                    top.linkTo(ttl.bottom)
                    start.linkTo(ttl.start)
                    end.linkTo(parent.end, styling.paddingUsed.dp)
                    width = Dimension.fillToConstraints
                },
                text = summary,
                style = TextStyle(
                    color = styling.summaryColor,
                    fontFamily = FontFamily(styling.summaryFont), fontSize = styling.summarySize.sp,
                )
            )

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
                    .constrainAs(slider) {
                        top.linkTo(smry.bottom)
                        start.linkTo(parent.start, styling.paddingUsed.dp)
                        end.linkTo(slidervalue.start, styling.paddingUsed.dp)
                        width = Dimension.fillToConstraints
                    })

            Text(
                modifier = Modifier
                    .width(84.dp)
                    .constrainAs(slidervalue) {
                        top.linkTo(slider.top)
                        bottom.linkTo(slider.bottom)
                        end.linkTo(parent.end)
                    },
                text = (value.value).toString(),
                style = TextStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily(Font(R.font.directive4bold)), fontSize = (13).sp
                )
            )

            Spacer(modifier = Modifier
                .height(styling.paddingUsed.dp)
                .constrainAs(spacer) {
                    top.linkTo(slider.bottom)
                })
        }
    }

    /** A color setting UI composable function. Clicking this would displays a popup to the user. */
    @Composable
    private fun ColorSettingUI(modifier: Modifier = Modifier) {
        val color = datastore().intFlow(key, defaultValue as Int).collectAsState(initial = defaultValue)
        val scope = rememberCoroutineScope()
        val colorDialogState = remember { mutableStateOf(false) }

        ConstraintLayout(
            modifier = modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(bounded = true, color = Paletting.SP_ORANGE)

                ) {
                    colorDialogState.value = true
                }
        ) {

            val (ic, ttl, smry, utility, spacer) = createRefs()

            SmartFancyIcon(
                tintColors = styling.iconTints,
                shadowColors = styling.iconShadows,
                icon = icon ?: Icons.Filled.QuestionMark, size = styling.iconSize.toInt(),
                modifier = Modifier.constrainAs(ic) {
                    top.linkTo(parent.top, styling.paddingUsed.dp)
                    start.linkTo(parent.start, styling.paddingUsed.dp)
                })

            FlexibleFancyText(
                modifier = Modifier.constrainAs(ttl) {
                    top.linkTo(ic.top)
                    start.linkTo(ic.end, 6.dp)
                    end.linkTo(utility.start, 6.dp)
                    width = Dimension.fillToConstraints
                },
                text = title,
                fillingColors = styling.titleFilling ?: listOf(MaterialTheme.colorScheme.primary),
                strokeColors = styling.titleStroke ?: listOf(),
                shadowColors = styling.titleShadow ?: Paletting.SP_GRADIENT,
                size = styling.titleSize,
                font = styling.titleFont
            )

            Button(onClick = { colorDialogState.value = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(color.value)),
                modifier = Modifier
                    .size(24.dp)
                    .constrainAs(utility) {
                        top.linkTo(ttl.top)
                        bottom.linkTo(smry.bottom)
                        end.linkTo(parent.end, styling.paddingUsed.dp)
                    }) {

            }

            Text(
                modifier = Modifier.constrainAs(smry) {
                    top.linkTo(ttl.bottom)
                    start.linkTo(ttl.start)
                    end.linkTo(utility.start, 6.dp)
                    width = Dimension.fillToConstraints
                },
                text = summary,
                style = TextStyle(
                    color = styling.summaryColor,
                    fontFamily = FontFamily(styling.summaryFont), fontSize = styling.summarySize.sp,
                )
            )

            Spacer(modifier = Modifier
                .height(styling.paddingUsed.dp)
                .constrainAs(spacer) {
                    top.linkTo(smry.bottom)
                })
        }

        ColorPickingPopup(colorDialogState, initialColor = HsvColor.from(Color(color.value))) { hsvColor ->
            scope.launch {
                datastorekey.writeInt(key, hsvColor.toColorInt())
            }
        }
    }


    /** A string setting UI composable function. It has a textfield next to it */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun TextFieldSettingUI(modifier: Modifier = Modifier) {
        val string by datastore().stringFlow(key, defaultValue as String).collectAsState(initial = defaultValue)
        val scope = rememberCoroutineScope()
        val focusManager = LocalFocusManager.current

        ConstraintLayout(
            modifier = modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(bounded = true, color = Paletting.SP_ORANGE)

                ) {
                    scope.launch {
                        datastore().writeString(key, string)
                    }
                }
        ) {

            val (ic, ttl, smry, utility, spacer) = createRefs()

            SmartFancyIcon(
                tintColors = styling.iconTints,
                shadowColors = styling.iconShadows,
                icon = icon ?: Icons.Filled.QuestionMark, size = styling.iconSize.toInt(),
                modifier = Modifier.constrainAs(ic) {
                    top.linkTo(parent.top, styling.paddingUsed.dp)
                    start.linkTo(parent.start, styling.paddingUsed.dp)
                })

            TextField(
                modifier = Modifier
                    .width(64.dp)
                    .constrainAs(utility) {
                        top.linkTo(ttl.top)
                        end.linkTo(parent.end, styling.paddingUsed.dp)
                        bottom.linkTo(smry.bottom)
                    },
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                value = string,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                }),
                colors = TextFieldDefaults.textFieldColors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                onValueChange = {
                    scope.launch {
                        datastore().writeString(key, it)
                    }
                },
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontFamily = FontFamily(Font(R.font.inter)),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                ),
                label = {}
            )

            FlexibleFancyText(
                modifier = Modifier.constrainAs(ttl) {
                    top.linkTo(ic.top)
                    start.linkTo(ic.end, 6.dp)
                    end.linkTo(utility.start, 6.dp)
                    width = Dimension.fillToConstraints
                },
                text = title,
                fillingColors = styling.titleFilling ?: listOf(MaterialTheme.colorScheme.primary),
                strokeColors = styling.titleStroke ?: listOf(),
                shadowColors = styling.titleShadow ?: Paletting.SP_GRADIENT,
                size = styling.titleSize,
                font = styling.titleFont
            )

            Text(
                modifier = Modifier.constrainAs(smry) {
                    top.linkTo(ttl.bottom)
                    start.linkTo(ttl.start)
                    end.linkTo(utility.start, 6.dp)
                    width = Dimension.fillToConstraints
                },
                text = summary,
                style = TextStyle(
                    color = styling.summaryColor,
                    fontFamily = FontFamily(styling.summaryFont), fontSize = styling.summarySize.sp,
                )
            )

            Spacer(modifier = Modifier
                .height(styling.paddingUsed.dp)
                .constrainAs(spacer) {
                    top.linkTo(smry.bottom)
                })
        }
    }


    /** Returns the global instance of the datastore that stores this setting */
    fun datastore(): DataStore<Preferences> {
        return DataStoreUtils.datastores[datastorekey]!!
    }
}
