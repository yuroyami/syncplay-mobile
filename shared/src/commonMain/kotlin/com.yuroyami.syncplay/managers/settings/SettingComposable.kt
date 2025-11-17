package com.yuroyami.syncplay.managers.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderDefaults.colors
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuroyami.syncplay.managers.preferences.Pref
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.no
import syncplaymobile.shared.generated.resources.yes
import kotlin.math.roundToInt

@Composable
inline fun <reified T> Pref<T>.SettingComposable() {
    val scope = rememberCoroutineScope { Dispatchers.IO }
    val styling = LocalSettingStyling.current

    val value by watchPref<T>()

    val renderableComposableState = remember { mutableStateOf(false) }

    val actionConfig = config?.extraConfig as? ExtraConfig.ActionSettingConfig
    val multiChoiceConfig = config?.extraConfig as? ExtraConfig.MultiChoiceSettingConfig
    val sliderConfig = config?.extraConfig as? ExtraConfig.SliderSettingConfig
    val textfieldConfig = config?.extraConfig as? ExtraConfig.TextFieldSettingConfig
    val showColorConfig = config?.extraConfig as? ExtraConfig.ShowColorPickerSettingConfig
    val showYesNoPopup = config?.extraConfig as? ExtraConfig.ShowYesNoPickerSettingConfig
    val showExtraComposable = config?.extraConfig as? ExtraConfig.ShowComposableSettingConfig

    /** Base Composable */
    Column(
        modifier = Modifier.fillMaxWidth().clickable(
            interactionSource = null,
            indication = ripple(bounded = true, color = Theming.SP_ORANGE),
            onClick = {
                actionConfig?.onClick() //TODO only action config?
            }
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
                icon = config!!.icon, size = styling.iconSize
            )

            Column(
                modifier = Modifier.weight(1f),
            ) {
                FlexibleText(
                    text = stringResource(config!!.title),
                    fillingColors = listOf(MaterialTheme.colorScheme.primary),
                    strokeColors = listOf(MaterialTheme.colorScheme.outline),
                    size = styling.titleSize,
                    font = sairaFont
                )

                Text(
                    modifier = Modifier,
                    text = stringResource(config!!.summary),
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = styling.summarySize.sp,
                    lineHeight = (styling.summarySize + 2).sp
                )
            }

            /** Trailing Content */
            when {
                //If boolean setting, show checkbox as trailing
                value is Boolean -> Checkbox(
                    checked = value as Boolean,
                    onCheckedChange = { b ->
                        scope.launch {
                            set(b as T)
                        }
                        //onBooleanChanged.invoke(b) TODO Is this ever needed ?
                    }
                )

                //If textfield setting, show textfield as trailing
                textfieldConfig != null -> HomeTextField(
                    modifier = Modifier.fillMaxWidth(0.5f).padding(6.dp),
                    value = value as String,
                    onValueChange = {
                        scope.launch {
                            set(it as T)
                        }
                    },
                    type = KeyboardType.Number,
                    height = 48.dp,
                    clearFocusWhenDone = true
                )

                //If multi-choice list, show list icon as trailing
                multiChoiceConfig != null -> Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = "",
                    tint = MaterialTheme.colorScheme.outline
                )

                //If slider, show value as trailing
                sliderConfig != null -> Text(
                    modifier = Modifier,
                    text = value.toString(),
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = styling.summarySize.sp,
                    lineHeight = (styling.summarySize + 2).sp
                )

            }
        }


        /** Supporting Content beneath */
        when {
            sliderConfig != null -> {
                Slider(
                    value = (value as Int).toFloat(),
                    valueRange = (sliderConfig.minValue.toFloat())..(sliderConfig.maxValue.toFloat()),
                    onValueChange = { f ->
                        if (f != (value as Int).toFloat()) {
                            sliderConfig.onValueChanged?.invoke(f.roundToInt())
                        }

                        scope.launch {
                            set(f.roundToInt() as T)
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
        }
    }

    //Show color picker if it's color setting, it handles visibility internally
    showColorConfig?.let {
        ColorPickingPopup(
            visibilityState = renderableComposableState,
            initialColor = Color(value as Int),
            onColorChanged = { color ->
                scope.launch {
                    set(color.toArgb() as T)
                }
            },
            onDefaultReset = {
                scope.launch { set(default) }
            }
        )
    }

    if (renderableComposableState.value) {
        when {
            multiChoiceConfig != null -> {
                val actualEntries = multiChoiceConfig.entries.invoke()

                MultiChoiceDialog(
                    items = actualEntries,
                    title = stringResource(config!!.title),
                    onDismiss = { renderableComposableState.value = false },
                    selectedItem = actualEntries.entries.first { it.value == value },
                    onItemClick = { item ->
                        renderableComposableState.value = false

                        scope.launch {
                            set(item.value as T)
                            multiChoiceConfig.onItemChosen?.let { it(item.value) }
                        }
                    }
                )
            }

            showYesNoPopup != null -> {
                AlertDialog(
                    onDismissRequest = { renderableComposableState.value = false },
                    confirmButton = {
                        TextButton(onClick = {
                            renderableComposableState.value = false
                            scope.launch { showYesNoPopup.onYes(this) }
                        }) { Text(stringResource(Res.string.yes)) }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            renderableComposableState.value = false
                            scope.launch { showYesNoPopup.onNo(this) }
                        }) { Text(stringResource(Res.string.no)) }
                    },
                    text = { Text(stringResource(showYesNoPopup.rationale)) }
                )
            }

            showExtraComposable != null -> {
                showExtraComposable.composable.invoke()
            }
        }
    }
}