package app.preferences

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import app.SyncplayViewmodel
import kotlinx.coroutines.CoroutineScope

sealed interface PrefExtraConfig {
    data class PerformAction(
        val onClick: () -> Unit
    ) : PrefExtraConfig

    /** A Boolean pref that also runs a callback when toggled, once, after the write. */
    data class BooleanCallback(
        val onBooleanChanged: (b: Boolean) -> Unit
    ) : PrefExtraConfig

    /**
     * A numeric range. [unit] is shown after the value ("10 s"). [onValueChanged] reaches a live
     * subsystem: it fires on release, and at most once every 60 ms while dragging.
     */
    data class Slider(
        val maxValue: Int = 100,
        val minValue: Int = 0,
        val unit: String = "",
        /** Shows "Off" instead of "0", for sliders where zero switches the feature off. */
        val zeroMeansOff: Boolean = false,
        /** Converts the stored integer to the number that users see and hear. [unit] is appended. */
        val formatValue: (Int) -> String = { it.toString() },
        val onValueChanged: (suspend SyncplayViewmodel.(newValue: Int) -> Unit)? = null,
    ) : PrefExtraConfig

    data class MultiChoice(
        val entries: @Composable () -> Map<String, String>,
        val onItemChosen: ((value: String) -> Unit)? = null
    ) : PrefExtraConfig

    data class ShowComposable(
        val composable: @Composable MutableState<Boolean>.() -> Unit
    ) : PrefExtraConfig

    /** A colour row. The stored value is the colour itself, as ARGB. */
    data object ColorPick : PrefExtraConfig

    /** A page of rows behind one entry: inline in the room's settings panel, a modal elsewhere. */
    data class Nested(val content: @Composable () -> Unit) : PrefExtraConfig

    /**
     * A row that asks yes or no. [destructive] draws the row and the yes action in the
     * destructive style.
     */
    data class YesNoDialog(
        val rationale: Localized,
        val onYes: suspend CoroutineScope.() -> Unit,
        val onNo: suspend CoroutineScope.() -> Unit = {},
        val destructive: Boolean = false,
    ) : PrefExtraConfig

    data class TextField(
        val keyboardType: Int = 0 // 0 = Text, 1 = Number
    ) : PrefExtraConfig
}
