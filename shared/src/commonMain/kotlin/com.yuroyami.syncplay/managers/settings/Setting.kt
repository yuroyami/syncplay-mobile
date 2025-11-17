package com.yuroyami.syncplay.managers.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderDefaults.colors
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuroyami.syncplay.managers.preferences.Pref
import com.yuroyami.syncplay.managers.preferences.StaticPref
import com.yuroyami.syncplay.managers.preferences.set
import com.yuroyami.syncplay.managers.preferences.watchPref
import com.yuroyami.syncplay.ui.components.FlexibleIcon
import com.yuroyami.syncplay.ui.components.FlexibleText
import com.yuroyami.syncplay.ui.components.MultiChoiceDialog
import com.yuroyami.syncplay.ui.components.sairaFont
import com.yuroyami.syncplay.ui.screens.adam.LocalSettingStyling
import com.yuroyami.syncplay.ui.screens.home.HomeTextField
import com.yuroyami.syncplay.ui.theme.Theming
import com.yuroyami.syncplay.ui.theme.Theming.flexibleGradient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

sealed class Setting<T>(
    val config: SettingConfig<T>
) {
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

    @Composable
    fun Pref<Any>.oneClickSetting(onClick: () -> Unit) {
        val config = settingConfig!!

        BaseSettingComposable(
            title = stringResource(config.title),
            summary = stringResource(config.summary),
            icon = config.icon,
            onClick = onClick
        )
    }

    @Composable
    fun Pref<Any>.ActionSetting(
        onClick: () -> Unit,
        render: @Composable (() -> Unit)? = null
    ) {
        val config = settingConfig!!
        val extraConfig = config.extraSettingConfig!! as SliderSettingConfig



        render?.invoke()
    }



    @Composable
    fun Pref<Boolean>.CheckboxSetting() {
        val config = settingConfig!!
        val key = config.key as StaticPref<Boolean>

        val boolean by key.watchPref()
        val scope = rememberCoroutineScope { Dispatchers.IO }

        BaseSettingComposable(
            title = stringResource(config.title),
            summary = stringResource(config.summary),
            icon = config.icon,
            onClick = {
                scope.launch {
                    key.set(!boolean)
                }
            },
            trailingElement = {
                Checkbox(
                    checked = boolean,
                    onCheckedChange = { b ->
                        scope.launch {
                            key.set(b)
                        }
                        //onBooleanChanged.invoke(b)
                    }
                )
            }
        )
    }

    @Composable
    fun Pref<String>.MultichoiceSetting() {
        val config = settingConfig!!
        val extraConfig = config.extraSettingConfig!! as MultiChoiceSettingConfig
        val key = config.key as StaticPref<String>

        val actualEntries = extraConfig.entries.invoke()
        val selectedItem by key.watchPref()
        val dialogOpen = remember { mutableStateOf(false) }

        val scope = rememberCoroutineScope { Dispatchers.IO }

        if (dialogOpen.value) {
            MultiChoiceDialog(
                items = actualEntries,
                title = stringResource(config.title),
                onDismiss = { dialogOpen.value = false },
                selectedItem = actualEntries.entries.first { it.value == selectedItem },
                onItemClick = { item ->
                    dialogOpen.value = false

                    scope.launch {
                        key.set(item.value)
                        extraConfig.onItemChosen?.let { it(item.value) }
                    }
                })
        }

        BaseSettingComposable(
            title = stringResource(config.title),
            summary = stringResource(config.summary),
            icon = config.icon,
            onClick = {
                dialogOpen.value = true
            },
            trailingElement = {
                Icon(imageVector = Icons.AutoMirrored.Filled.List, "", tint = MaterialTheme.colorScheme.outline)
            }
        )
    }

    @Composable
    fun Pref<Int>.SliderSetting() {
        val config = settingConfig!!
        val extraConfig = config.extraSettingConfig!! as SliderSettingConfig
        val key = config.key as StaticPref<Int>

        val value by key.watchPref()
        val styling = LocalSettingStyling.current
        val scope = rememberCoroutineScope { Dispatchers.IO }

        BaseSettingComposable(
            title = stringResource(config.title),
            summary = stringResource(config.summary),
            icon = config.icon,
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
                    valueRange = (extraConfig.minValue.toFloat())..(extraConfig.maxValue.toFloat()),
                    onValueChange = { f ->
                        if (f != value.toFloat()) {
                            extraConfig.onValueChanged?.invoke(f.roundToInt())
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

//    @Composable
//    fun SettingColorpick() {
//        ColorPickingPopup(
//            visibilityState = colorDialogState, initialColor = Color(color), onColorChanged = { color ->
//                scope.launch {
//                    key.set(color.toArgb())
//                }
//            },
//            onDefaultReset = {
//                scope.launch { key.set(staticKey.default) }
//            }
//        )
//    }

    @Composable
    fun Pref<String>.TextFieldSetting() {
        val config = settingConfig!!
        val key = config.key as StaticPref<String>
        val string by key.watchPref()
        val scope = rememberCoroutineScope()

        BaseSettingComposable(
            title = stringResource(config.title),
            summary = stringResource(config.summary),
            icon = config.icon,
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