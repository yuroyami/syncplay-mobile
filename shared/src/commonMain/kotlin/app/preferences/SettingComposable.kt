package app.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.LocalGlobalViewmodel
import app.LocalSettingStyling
import app.home.components.HomeTextField
import app.preferences.settings.PopupColorPicker.ColorPickingPopup
import app.theme.Theming
import app.uicomponents.MultiChoiceDialog
import app.uicomponents.tvFocusable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.no
import syncplaymobile.shared.generated.resources.yes
import kotlin.math.roundToInt

@Composable
internal inline fun <reified T> Pref<T>.SettingComposable() {
    val cfg = config ?: return
    val viewmodel = LocalGlobalViewmodel.current

    val scope = rememberCoroutineScope { Dispatchers.IO }
    val styling = LocalSettingStyling.current

    val value by watchPref<T>()

    val renderableComposableState = remember { mutableStateOf(false) }

    val extra = cfg.extraConfig
    val actionConfig = extra as? PrefExtraConfig.PerformAction
    val booleanCallbackConfig = extra as? PrefExtraConfig.BooleanCallback
    val multiChoiceConfig = extra as? PrefExtraConfig.MultiChoice
    val sliderConfig = extra as? PrefExtraConfig.Slider
    val textfieldConfig = (extra as? PrefExtraConfig.TextField)
        ?: if (value is String && extra == null) PrefExtraConfig.TextField() else null
    val showColorConfig = extra as? PrefExtraConfig.ColorPick
    val showYesNoPopup = extra as? PrefExtraConfig.YesNoDialog
    val showExtraComposable = extra as? PrefExtraConfig.ShowComposable

    val isEnabled = cfg.dependencyEnable()
    val isBooleanSetting = value is Boolean || booleanCallbackConfig != null

    /** Base Composable */
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = null,
                indication = ripple(bounded = true),
                enabled = isEnabled,
                onClick = {
                    actionConfig?.onClick()

                    if (isBooleanSetting) {
                        scope.launch {
                            set(!(value as Boolean) as T)
                        }
                        booleanCallbackConfig?.onBooleanChanged(!(value as Boolean))
                    }

                    if (multiChoiceConfig != null || showColorConfig != null || showYesNoPopup != null || showExtraComposable != null) {
                        renderableComposableState.value = true
                    }
                }
            )
            .tvFocusable(enabled = isEnabled, addFocusable = false)
            .alpha(if (isEnabled) 1f else 0.38f)
            .padding(horizontal = (styling.paddingUsed + 4).dp, vertical = styling.paddingUsed.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            verticalAlignment = CenterVertically
        ) {
            Icon(
                imageVector = cfg.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(styling.iconSize.dp)
            )

            Spacer(Modifier.width(Theming.SpaceLG))

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(cfg.title),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = styling.titleSize.sp,
                        lineHeight = (styling.titleSize + 6).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = stringResource(cfg.summary, *cfg.summaryFormatArgs),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = styling.summarySize.sp,
                        lineHeight = (styling.summarySize + 4).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            /** Trailing Content */
            when {
                isBooleanSetting -> Switch(
                    modifier = Modifier.padding(start = Theming.SpaceSM),
                    checked = value as Boolean,
                    enabled = isEnabled,
                    onCheckedChange = { b ->
                        scope.launch {
                            set(b as T)
                        }
                        booleanCallbackConfig?.onBooleanChanged(b)
                    }
                )

                textfieldConfig != null -> HomeTextField(
                    modifier = Modifier.weight(1f).padding(start = Theming.SpaceSM),
                    value = value as String,
                    onValueChange = {
                        scope.launch {
                            set(it as T)
                        }
                    },
                    enabled = isEnabled,
                    type = if (textfieldConfig.keyboardType == 1) KeyboardType.Number else KeyboardType.Text,
                    height = 48.dp,
                    clearFocusWhenDone = true
                )

                multiChoiceConfig != null -> {
                    val currentChoiceLabel = multiChoiceConfig.entries.invoke()
                        .entries.firstOrNull { it.value == value }?.key ?: ""
                    Text(
                        text = currentChoiceLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.End,
                        modifier = Modifier.padding(start = Theming.SpaceSM).widthIn(max = 140.dp)
                    )
                }

                sliderConfig != null -> Text(
                    modifier = Modifier.padding(start = Theming.SpaceSM),
                    text = value.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.End
                )

                showColorConfig != null -> Box(
                    modifier = Modifier
                        .padding(start = Theming.SpaceSM)
                        .size(28.dp)
                        .background(color = Color(value as Int), shape = CircleShape)
                        .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant, shape = CircleShape)
                )
            }
        }

        /** Supporting Content beneath */
        when {
            sliderConfig != null -> {
                val sliderInteractionSource = remember { MutableInteractionSource() }
                Slider(
                    value = (value as Int).toFloat(),
                    enabled = isEnabled,
                    valueRange = (sliderConfig.minValue.toFloat())..(sliderConfig.maxValue.toFloat()),
                    interactionSource = sliderInteractionSource,
                    // Default M3 expressive thumb is a 4x44dp pill that dwarfs a settings row;
                    // shrink it to a modest handle.
                    thumb = {
                        SliderDefaults.Thumb(
                            interactionSource = sliderInteractionSource,
                            enabled = isEnabled,
                            thumbSize = DpSize(width = 4.dp, height = 24.dp)
                        )
                    },
                    onValueChange = { f ->
                        scope.launch {
                            if (f != (value as Int).toFloat()) {
                                sliderConfig.onValueChanged?.invoke(viewmodel, f.roundToInt())
                            }

                            set(f.roundToInt() as T)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Theming.SpaceMD)
                )
            }
        }
    }

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
                    title = stringResource(cfg.title),
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
                showExtraComposable.composable.invoke(renderableComposableState)
            }
        }
    }
}
