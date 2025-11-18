package com.yuroyami.syncplay.managers.preferences

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.compose.resources.StringResource

sealed interface PrefExtraConfig {
    data class PerformAction(
        val onClick: () -> Unit
    ) : PrefExtraConfig

    //Only using this when the boolean change callback is needed
    data class BooleanCallback(
        val onBooleanChanged: (b: Boolean) -> Unit
    ) : PrefExtraConfig

    data class Slider(
        val maxValue: Int = 100,
        val minValue: Int = 0,
        val onValueChanged: ((newValue: Int) -> Unit)? = null
    ) : PrefExtraConfig

    data class MultiChoice(
        val entries: @Composable () -> Map<String, String>,
        val onItemChosen: ((value: String) -> Unit)? = null
    ) : PrefExtraConfig

    data class ShowComposable(
        val composable: @Composable () -> Unit
    ) : PrefExtraConfig

    data object ColorPick : PrefExtraConfig

    data class YesNoDialog(
        val rationale: StringResource,
        val onYes: suspend CoroutineScope.() -> Unit,
        val onNo: suspend CoroutineScope.() -> Unit = {}
    ) : PrefExtraConfig

    data object TextField : PrefExtraConfig
}