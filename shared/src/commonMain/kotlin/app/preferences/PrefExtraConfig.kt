package app.preferences

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import app.SyncplayViewmodel
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.compose.resources.StringResource

sealed interface PrefExtraConfig {
    data class PerformAction(
        val onClick: () -> Unit
    ) : PrefExtraConfig

    /** Boolean pref that also fires a side-effect callback on toggle, once, after the write. */
    data class BooleanCallback(
        val onBooleanChanged: (b: Boolean) -> Unit
    ) : PrefExtraConfig

    /**
     * A numeric range. [unit] is shown after the value ("10 s"). [onValueChanged] reaches a live
     * subsystem: it fires on release and at most a few times a second while dragging.
     */
    data class Slider(
        val maxValue: Int = 100,
        val minValue: Int = 0,
        val unit: String = "",
        val onValueChanged: (suspend SyncplayViewmodel.(newValue: Int) -> Unit)? = null
    ) : PrefExtraConfig

    data class MultiChoice(
        val entries: @Composable () -> Map<String, String>,
        val onItemChosen: ((value: String) -> Unit)? = null
    ) : PrefExtraConfig

    data class ShowComposable(
        val composable: @Composable MutableState<Boolean>.() -> Unit
    ) : PrefExtraConfig

    data object ColorPick : PrefExtraConfig

    /** [destructive] draws the row and the confirming action in the destructive treatment. */
    data class YesNoDialog(
        val rationale: StringResource,
        val onYes: suspend CoroutineScope.() -> Unit,
        val onNo: suspend CoroutineScope.() -> Unit = {},
        val destructive: Boolean = false,
    ) : PrefExtraConfig

    data class TextField(
        val keyboardType: Int = 0 // 0 = Text, 1 = Number
    ) : PrefExtraConfig
}
