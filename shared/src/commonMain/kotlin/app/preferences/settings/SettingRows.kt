package app.preferences.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import app.uicomponents.controls.Icon
import app.uicomponents.controls.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.LocalGlobalViewmodel
import app.preferences.Pref
import app.preferences.PrefExtraConfig
import app.preferences.Preferences
import app.preferences.LocalPrefsState
import app.preferences.setAny
import app.preferences.watchAny
import app.preferences.watchPref
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.controls.AccentAction
import app.uicomponents.controls.Chevron
import app.uicomponents.controls.ChevronDirection
import app.uicomponents.controls.DestructiveAction
import app.uicomponents.controls.Field
import app.uicomponents.controls.ListRow
import app.uicomponents.controls.Rocker
import app.uicomponents.controls.RowGap
import app.uicomponents.controls.RowLabel
import app.uicomponents.controls.RowValue
import app.uicomponents.controls.ScrubTrack
import app.uicomponents.controls.SecondaryAction
import app.uicomponents.controls.Stepper
import app.uicomponents.controls.Swatch
import app.uicomponents.controls.hex
import app.uicomponents.frames.Modal
import app.uicomponents.frames.ModalSize
import com.kborowy.colorpicker.KolorPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.cancel
import syncplaymobile.shared.generated.resources.done
import syncplaymobile.shared.generated.resources.no
import syncplaymobile.shared.generated.resources.reset_default
import syncplaymobile.shared.generated.resources.save
import syncplaymobile.shared.generated.resources.settings_value_none
import syncplaymobile.shared.generated.resources.settings_value_off
import syncplaymobile.shared.generated.resources.settings_value_on
import syncplaymobile.shared.generated.resources.yes
import kotlin.math.roundToInt
import kotlin.time.TimeSource

/** Semantic density choices a host can vary. Never font sizes. */
@Immutable
data class SettingsDensity(
    val showRowIcons: Boolean = false,
    val showInlineExplanations: Boolean = false,
    val contentMaxWidth: Dp = 560.dp,
)

val LocalSettingsDensity = staticCompositionLocalOf { SettingsDensity() }

/** Which rows show their explanation, keyed by preference; the list reads it to draw the rules. */
val LocalExpandedSettings = staticCompositionLocalOf { mutableStateMapOf<String, Boolean>() }

/**
 * The console row for one entry: label left, value in the fixed column, control after it. The
 * row kind follows the value type and the control, exactly as DESIGN/PREF_SYSTEM lists them.
 */
@Composable
fun SettingEntry.Render(highlighted: Boolean = false) {
    val cfg = pref.config ?: return
    val extra = extra
    // Reading the snapshot here is what makes a dependency flip recompose the dependent row.
    LocalPrefsState.current.value
    val enabled = isEnabled()
    val value by pref.watchAny()
    val scope = rememberCoroutineScope { Dispatchers.IO }
    val density = LocalSettingsDensity.current
    val showDescriptions by Preferences.SHOW_SETTING_DESCRIPTIONS.watchPref()
    val title = stringResource(cfg.title)
    val summary = stringResource(cfg.summary, *cfg.summaryFormatArgs)
    val expanded = LocalExpandedSettings.current
    val explain = expanded[pref.key] == true
    fun toggleExplain() { expanded[pref.key] = !explain }
    val editorOpen = remember { mutableStateOf(false) }

    val icon: (@Composable () -> Unit)? = if (density.showRowIcons) {
        { Icon(cfg.icon, contentDescription = null, tint = palette.inkDim, modifier = Modifier.size(Space.glyph)); RowGap() }
    } else null

    Column(Modifier.fillMaxWidth()) {
        when {
            value is Boolean || extra is PrefExtraConfig.BooleanCallback -> {
                val on = value as? Boolean ?: false
                val flip: (Boolean) -> Unit = { next ->
                    scope.launch {
                        pref.setAny(next)
                        (extra as? PrefExtraConfig.BooleanCallback)?.onBooleanChanged?.invoke(next)
                    }
                }
                ListRow(onClick = { flip(!on) }, onLongClick = ::toggleExplain, enabled = enabled, selected = highlighted) {
                    icon?.invoke()
                    RowLabel(title)
                    RowGap()
                    RowValue(stringResource(if (on) Res.string.settings_value_on else Res.string.settings_value_off), accent = on, width = 36.dp)
                    RowGap()
                    Rocker(on = on, onChange = flip, enabled = enabled)
                }
            }

            extra is PrefExtraConfig.MultiChoice -> {
                val entries = extra.entries()
                val labels = entries.keys.toList()
                val values = entries.values.toList()
                val index = values.indexOf(value)
                if (entries.size < 5 && index >= 0) {
                    ListRow(onLongClick = ::toggleExplain, enabled = enabled, selected = highlighted) {
                        icon?.invoke()
                        RowLabel(title)
                        RowGap()
                        Stepper(labels, index, enabled = enabled, onIndex = { i ->
                            scope.launch { pref.setAny(values[i]); extra.onItemChosen?.invoke(values[i]) }
                        })
                    }
                } else {
                    OpenRow(title, labels.getOrNull(index) ?: value.toString(), enabled, highlighted, icon, onOpen = { editorOpen.value = true })
                    ChoiceModal(editorOpen, title, summary, entries, value as? String) { picked ->
                        scope.launch { pref.setAny(picked); extra.onItemChosen?.invoke(picked) }
                    }
                }
            }

            extra is PrefExtraConfig.Slider -> {
                val current = (value as? Int) ?: (pref.default as? Int ?: 0)
                val vm = LocalGlobalViewmodel.current
                ScrubRow(
                    title = title,
                    value = current,
                    min = extra.minValue,
                    max = extra.maxValue,
                    unit = extra.unit,
                    enabled = enabled,
                    highlighted = highlighted,
                    icon = icon,
                    onLongPress = ::toggleExplain,
                    onLive = { v -> extra.onValueChanged?.let { cb -> scope.launch { cb(vm, v) } } },
                    onCommit = { v ->
                        scope.launch {
                            pref.setAny(v)
                            extra.onValueChanged?.invoke(vm, v)
                        }
                    },
                )
            }

            extra is PrefExtraConfig.ColorPick -> {
                val color = Color((value as? Int) ?: (pref.default as Int))
                ListRow(onClick = { editorOpen.value = true }, onLongClick = ::toggleExplain, enabled = enabled, selected = highlighted) {
                    icon?.invoke()
                    RowLabel(title)
                    RowGap()
                    RowValue(color.hex())
                    RowGap()
                    Swatch(color, onClick = { editorOpen.value = true }, enabled = enabled)
                }
                ColorModal(
                    open = editorOpen,
                    title = title,
                    summary = summary,
                    initial = color,
                    onColor = { c -> scope.launch { pref.setAny(c.toArgb()) } },
                    onReset = { scope.launch { pref.setAny(pref.default as Int) } },
                )
            }

            extra is PrefExtraConfig.TextField || (value is String && extra == null) -> {
                val text = value as? String ?: ""
                val numeric = (extra as? PrefExtraConfig.TextField)?.keyboardType == 1
                OpenRow(title, text.ifBlank { stringResource(Res.string.settings_value_none) }, enabled, highlighted, icon, onOpen = { editorOpen.value = true })
                TextModal(editorOpen, title, summary, text, numeric) { saved -> scope.launch { pref.setAny(saved) } }
            }

            extra is PrefExtraConfig.ShowComposable -> {
                val state = cfg.stateSummary?.invoke(value) ?: ""
                OpenRow(title, state, enabled, highlighted, icon, onOpen = { editorOpen.value = true })
                if (editorOpen.value) extra.composable.invoke(editorOpen)
            }

            extra is PrefExtraConfig.PerformAction -> {
                ActionRow(title, enabled, highlighted, icon, destructive = false, onClick = extra.onClick)
            }

            extra is PrefExtraConfig.YesNoDialog -> {
                ActionRow(title, enabled, highlighted, icon, destructive = extra.destructive, onClick = { editorOpen.value = true })
                AskModal(
                    open = editorOpen,
                    title = title,
                    text = stringResource(extra.rationale),
                    destructive = extra.destructive,
                    onYes = { scope.launch { extra.onYes(this) } },
                    onNo = { scope.launch { extra.onNo(this) } },
                )
            }

            else -> ListRow(enabled = enabled, selected = highlighted) {
                icon?.invoke()
                RowLabel(title)
                RowGap()
                RowValue(value?.toString() ?: "")
            }
        }

        if (showDescriptions || density.showInlineExplanations || explain) {
            Explanation(summary, indent = if (density.showRowIcons) Space.gutter + Space.glyph + Space.gap else Space.gutter)
        }
    }
}

/**
 * The explanation under a row, aligned with the row's label and pulled up under it, shown on a
 * long press or when Show setting descriptions is on. The list around it draws the rules.
 */
@Composable
private fun Explanation(text: String, indent: Dp) {
    Text(
        text = text,
        style = Type.note,
        color = palette.inkDim,
        modifier = Modifier.fillMaxWidth().offset(y = -Space.gapTight).padding(start = indent, end = Space.gutter, bottom = Space.gapTight),
    )
}

@Composable
private fun OpenRow(
    title: String,
    value: String,
    enabled: Boolean,
    highlighted: Boolean,
    icon: (@Composable () -> Unit)?,
    onOpen: () -> Unit,
) {
    ListRow(onClick = onOpen, enabled = enabled, selected = highlighted, horizontalPadding = Space.gutter) {
        icon?.invoke()
        RowLabel(title)
        RowGap()
        RowValue(value)
        RowGap(Space.gapTight)
        Chevron(ChevronDirection.Right)
    }
}

@Composable
private fun ActionRow(
    title: String,
    enabled: Boolean,
    highlighted: Boolean,
    icon: (@Composable () -> Unit)?,
    destructive: Boolean,
    onClick: () -> Unit,
) {
    val p = palette
    ListRow(onClick = onClick, enabled = enabled, selected = highlighted, horizontalPadding = if (destructive) 0.dp else Space.gutter) {
        if (destructive) {
            Box(Modifier.width(2.dp).height(Space.row).background(p.bad))
            Spacer(Modifier.width(Space.gutter - 2.dp))
        }
        icon?.invoke()
        RowLabel(title, color = if (destructive) p.bad else p.ink)
        RowGap()
        Chevron(ChevronDirection.Right, modifier = Modifier.padding(end = if (destructive) Space.gutter else 0.dp))
    }
}

/** Label and value on one line, the track full width beneath. The value sits with its control. */
@Composable
private fun ScrubRow(
    title: String,
    value: Int,
    min: Int,
    max: Int,
    unit: String,
    enabled: Boolean,
    highlighted: Boolean,
    icon: (@Composable () -> Unit)?,
    onLongPress: () -> Unit,
    onLive: (Int) -> Unit,
    onCommit: (Int) -> Unit,
) {
    val p = palette
    val span = (max - min).coerceAtLeast(1)
    var dragging by remember { mutableStateOf(false) }
    var preview by remember { mutableFloatStateOf(0f) }
    val shown = if (dragging) preview else (value - min).toFloat() / span
    val shownValue = (min + (shown * span)).roundToInt()
    var lastLive by remember { mutableStateOf(TimeSource.Monotonic.markNow()) }

    ListRow(onLongClick = onLongPress, enabled = enabled, selected = highlighted, minHeight = Space.rowTall) {
        Column(Modifier.fillMaxWidth().padding(vertical = Space.gapTight)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                icon?.invoke()
                RowLabel(title)
                RowGap()
                Text(
                    text = if (unit.isEmpty()) "$shownValue" else "$shownValue $unit",
                    style = Type.value,
                    color = if (enabled) p.accent else p.disabled,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ScrubTrack(
                value = shown,
                enabled = enabled,
                keyStep = 1f / span,
                describe = { f -> "${(min + f * span).roundToInt()} $unit".trim() },
                name = title,
                hitHeight = 24.dp,
                onValueChange = { f ->
                    dragging = true
                    preview = f
                    val now = TimeSource.Monotonic.markNow()
                    if ((now - lastLive).inWholeMilliseconds >= 60) {
                        lastLive = now
                        onLive((min + f * span).roundToInt())
                    }
                },
                onValueChangeFinished = {
                    dragging = false
                    onCommit((min + preview * span).roundToInt())
                },
            )
        }
    }
}

/* ── Editors ─────────────────────────────────────────────────────────────────────────────── */

@Composable
private fun ChoiceModal(
    open: MutableState<Boolean>,
    title: String,
    summary: String,
    entries: Map<String, String>,
    selected: String?,
    onPick: (String) -> Unit,
) {
    Modal(open = open.value, onDismiss = { open.value = false }, title = title, size = ModalSize.Panel, inset = false) {
        Text(summary, style = Type.note, color = palette.inkDim, modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.gapTight))
        entries.forEach { (label, v) ->
            ListRow(onClick = { onPick(v); open.value = false }, selected = v == selected) {
                RowLabel(label)
            }
        }
    }
}

@Composable
private fun TextModal(
    open: MutableState<Boolean>,
    title: String,
    summary: String,
    initial: String,
    numeric: Boolean,
    onSave: (String) -> Unit,
) {
    if (!open.value) return
    var draft by remember { mutableStateOf(initial) }
    Modal(
        open = true,
        onDismiss = { open.value = false },
        title = title,
        size = ModalSize.Panel,
        actions = {
            SecondaryAction(stringResource(Res.string.cancel), onClick = { open.value = false })
            AccentAction(stringResource(Res.string.save), onClick = { onSave(draft); open.value = false })
        },
    ) {
        Text(summary, style = Type.note, color = palette.inkDim)
        Spacer(Modifier.height(Space.gap))
        Field(
            value = draft,
            onValueChange = { draft = it },
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
            onImeAction = { onSave(draft); open.value = false },
            name = title,
        )
    }
}

@Composable
internal fun ColorModal(
    open: MutableState<Boolean>,
    title: String,
    summary: String,
    initial: Color,
    onColor: (Color) -> Unit,
    onReset: () -> Unit,
) {
    if (!open.value) return
    var draft by remember { mutableStateOf(initial) }
    Modal(
        open = true,
        onDismiss = { open.value = false },
        title = title,
        size = ModalSize.Panel,
        actions = {
            SecondaryAction(stringResource(Res.string.reset_default), onClick = { onReset(); open.value = false })
            AccentAction(stringResource(Res.string.done), onClick = { open.value = false })
        },
    ) {
        Text(summary, style = Type.note, color = palette.inkDim)
        Spacer(Modifier.height(Space.gap))
        KolorPicker(
            modifier = Modifier.fillMaxWidth().height(260.dp),
            initialColor = initial,
            onColorSelected = { c -> draft = c; onColor(c) },
        )
        Spacer(Modifier.height(Space.gap))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Swatch(draft)
            RowGap()
            Text(draft.hex(), style = Type.value, color = palette.inkDim)
        }
    }
}

@Composable
fun AskModal(
    open: MutableState<Boolean>,
    title: String,
    text: String,
    destructive: Boolean,
    onYes: () -> Unit,
    onNo: () -> Unit = {},
) {
    Modal(
        open = open.value,
        onDismiss = { open.value = false; onNo() },
        title = title,
        size = ModalSize.Ask,
        actions = {
            SecondaryAction(stringResource(Res.string.no), onClick = { open.value = false; onNo() })
            if (destructive) DestructiveAction(stringResource(Res.string.yes), onClick = { open.value = false; onYes() })
            else AccentAction(stringResource(Res.string.yes), onClick = { open.value = false; onYes() })
        },
    ) {
        Text(text, style = Type.note, color = palette.inkDim)
    }
}

/** Renders a bare pref as a row, for hosts that hold prefs rather than categories. */
@Composable
fun Pref<*>.SettingRow() = SettingEntry(this).Render()
