package com.yuroyami.syncplay.managers.settings

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.compose.resources.StringResource

sealed interface ExtraConfig {
    data class ActionSettingConfig(
        val onClick: () -> Unit
    ) : ExtraConfig

    data class SliderSettingConfig(
        val maxValue: Int = 100,
        val minValue: Int = 0,
        val onValueChanged: ((newValue: Int) -> Unit)? = null
    ) : ExtraConfig

    data class MultiChoiceSettingConfig(
        val entries: @Composable () -> Map<String, String>,
        val onItemChosen: ((value: String) -> Unit)? = null
    ) : ExtraConfig

    data class ShowComposableSettingConfig(
        val composable: @Composable () -> Unit
    ) : ExtraConfig

    data object ShowColorPickerSettingConfig : ExtraConfig

    data class ShowYesNoPickerSettingConfig(
        val rationale: StringResource,
        val onYes: suspend CoroutineScope.() -> Unit,
        val onNo: suspend CoroutineScope.() -> Unit = {}
    ) : ExtraConfig

    data object TextFieldSettingConfig : ExtraConfig
}