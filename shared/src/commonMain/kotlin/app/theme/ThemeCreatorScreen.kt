package app.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import app.uicomponents.controls.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import app.LocalGlobalViewmodel
import app.LocalTheme
import app.preferences.settings.ColorModal
import app.uicomponents.controls.Field
import app.uicomponents.controls.GroupHeading
import app.uicomponents.controls.ListRow
import app.uicomponents.controls.PrimaryAction
import app.uicomponents.controls.Rocker
import app.uicomponents.controls.RowGap
import app.uicomponents.controls.RowLabel
import app.uicomponents.controls.RowValue
import app.uicomponents.controls.ScrubTrack
import app.uicomponents.controls.SecondaryAction
import app.uicomponents.controls.Stepper
import app.uicomponents.controls.Swatch
import app.uicomponents.controls.hex
import app.uicomponents.frames.NoticeHost
import app.uicomponents.frames.NoticeQueue
import app.uicomponents.frames.NoticeSeverity
import app.uicomponents.frames.ScreenFrame
import com.materialkolor.PaletteStyle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.save
import syncplaymobile.shared.generated.resources.theme_auto
import syncplaymobile.shared.generated.resources.theme_customize_already_exists_warning
import syncplaymobile.shared.generated.resources.theme_customize_contrast
import syncplaymobile.shared.generated.resources.theme_customize_dark
import syncplaymobile.shared.generated.resources.theme_customize_is_amoled
import syncplaymobile.shared.generated.resources.theme_customize_name
import syncplaymobile.shared.generated.resources.theme_customize_neutral_color
import syncplaymobile.shared.generated.resources.theme_customize_neutral_variant_color
import syncplaymobile.shared.generated.resources.theme_customize_palette_style
import syncplaymobile.shared.generated.resources.theme_customize_primary_color
import syncplaymobile.shared.generated.resources.theme_customize_secondary_color
import syncplaymobile.shared.generated.resources.theme_customize_tertiary_color
import syncplaymobile.shared.generated.resources.theme_customize_title
import syncplaymobile.shared.generated.resources.theme_save_as_new
import kotlin.math.roundToInt

/**
 * The theme creator: controls on one side, the live miniature on the other, and the whole
 * screen re-themed on every edit through the token layer. The scheme is resolved once per
 * theme value. Saving an edit replaces the old copy in one write.
 */
@Composable
fun ThemeCreatorScreenUI(themeToEdit: SaveableTheme? = null) {
    val globalViewmodel = LocalGlobalViewmodel.current
    var theme by remember { mutableStateOf(themeToEdit ?: globalViewmodel.currentTheme.value) }
    val scheme = remember(theme) { theme.dynamicScheme }
    val livePalette = remember(theme) { Palette.from(scheme, theme) }
    val notices = remember { NoticeQueue() }
    val exists = stringResource(Res.string.theme_customize_already_exists_warning)

    fun close() = globalViewmodel.backstack.removeAt(globalViewmodel.backstack.lastIndex)

    fun save(asNew: Boolean) {
        globalViewmodel.viewModelScope.launch {
            val saved = if (themeToEdit != null && !asNew) globalViewmodel.replaceTheme(themeToEdit, theme) else globalViewmodel.saveNewTheme(theme)
            if (saved) close() else notices.post(exists, NoticeSeverity.Warn, holdMs = 3000L)
        }
    }

    CompositionLocalProvider(LocalTheme provides theme, LocalPalette provides livePalette) {
        run {
            ScreenFrame(title = stringResource(Res.string.theme_customize_title), onBack = { close() }) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val wide = maxWidth >= 720.dp
                    if (wide) {
                        Row(Modifier.fillMaxSize()) {
                            Controls(theme, onTheme = { theme = it }, editing = themeToEdit != null, onSave = ::save, modifier = Modifier.weight(1f).fillMaxHeight())
                            ThemeMiniature(theme, Modifier.weight(1f).fillMaxHeight().padding(Space.gutter).clip(Radius.panelShape))
                        }
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            ThemeMiniature(theme, Modifier.fillMaxWidth().height(160.dp).padding(horizontal = Space.gutter, vertical = Space.gap).clip(Radius.panelShape))
                            Controls(theme, onTheme = { theme = it }, editing = themeToEdit != null, onSave = ::save, modifier = Modifier.weight(1f).fillMaxWidth())
                        }
                    }
                    NoticeHost(notices, overVideo = false, modifier = Modifier.align(Alignment.BottomCenter).padding(Space.gutter))
                }
            }
        }
    }
}

@Composable
private fun Controls(
    theme: SaveableTheme,
    onTheme: (SaveableTheme) -> Unit,
    editing: Boolean,
    onSave: (asNew: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = palette
    Column(modifier.verticalScroll(rememberScrollState()).imePadding()) {
        Box(Modifier.padding(horizontal = Space.gutter, vertical = Space.gap)) {
            Field(
                value = theme.name,
                onValueChange = { onTheme(theme.copy(name = it)) },
                placeholder = stringResource(Res.string.theme_customize_name),
                name = stringResource(Res.string.theme_customize_name),
            )
        }

        GroupHeading(stringResource(Res.string.theme_customize_palette_style))
        ColorRow(stringResource(Res.string.theme_customize_primary_color), Color(theme.primaryColor), onColor = { onTheme(theme.copy(primaryColor = it.toArgb())) })
        ColorRow(stringResource(Res.string.theme_customize_secondary_color), theme.secondaryColor?.let(::Color), onColor = { onTheme(theme.copy(secondaryColor = it.toArgb())) }, onReset = { onTheme(theme.copy(secondaryColor = null)) })
        ColorRow(stringResource(Res.string.theme_customize_tertiary_color), theme.tertiaryColor?.let(::Color), onColor = { onTheme(theme.copy(tertiaryColor = it.toArgb())) }, onReset = { onTheme(theme.copy(tertiaryColor = null)) })
        ColorRow(stringResource(Res.string.theme_customize_neutral_color), theme.neutralColor?.let(::Color), onColor = { onTheme(theme.copy(neutralColor = it.toArgb())) }, onReset = { onTheme(theme.copy(neutralColor = null)) })
        ColorRow(stringResource(Res.string.theme_customize_neutral_variant_color), theme.neutralVariantColor?.let(::Color), onColor = { onTheme(theme.copy(neutralVariantColor = it.toArgb())) }, onReset = { onTheme(theme.copy(neutralVariantColor = null)) })

        ListRow {
            RowLabel(stringResource(Res.string.theme_customize_palette_style))
            val styles = PaletteStyle.entries
            Stepper(options = styles.map { it.name }, index = styles.indexOf(theme.style).coerceAtLeast(0), onIndex = { onTheme(theme.copy(style = styles[it])) }, wrap = true)
        }
        ListRow {
            RowLabel(stringResource(Res.string.theme_customize_dark))
            Rocker(on = theme.isDark, onChange = { onTheme(theme.copy(isDark = it)) }, name = stringResource(Res.string.theme_customize_dark))
        }
        ListRow(enabled = theme.isDark) {
            RowLabel(stringResource(Res.string.theme_customize_is_amoled))
            Rocker(on = theme.isAMOLED, onChange = { onTheme(theme.copy(isAMOLED = it)) }, enabled = theme.isDark, name = stringResource(Res.string.theme_customize_is_amoled))
        }
        // Contrast starts from the theme's stored value, never from zero.
        Column(Modifier.padding(horizontal = Space.gutter, vertical = Space.gapTight)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.theme_customize_contrast), style = Type.label, color = p.ink, modifier = Modifier.weight(1f))
                Text(((theme.contrast * 10).roundToInt() / 10.0).toString(), style = Type.value, color = p.inkDim)
            }
            ScrubTrack(
                value = ((theme.contrast + 1.0) / 2.0).toFloat().coerceIn(0f, 1f),
                onValueChange = { onTheme(theme.copy(contrast = (it * 2.0 - 1.0))) },
                name = stringResource(Res.string.theme_customize_contrast),
            )
        }

        Column(Modifier.padding(Space.gutter), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.gap)) {
            PrimaryAction(stringResource(Res.string.save), onClick = { onSave(false) }, modifier = Modifier.fillMaxWidth())
            if (editing) SecondaryAction(stringResource(Res.string.theme_save_as_new), onClick = { onSave(true) }, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(Space.gutter))
    }
}

/** A colour row: the hex in the value column, the swatch after it, the shared colour modal. */
@Composable
private fun ColorRow(label: String, color: Color?, onColor: (Color) -> Unit, onReset: (() -> Unit)? = null) {
    val open = remember { mutableStateOf(false) }
    val p = palette
    ListRow(onClick = { open.value = true }) {
        RowLabel(label)
        RowValue(color?.hex() ?: stringResource(Res.string.theme_auto), width = 90.dp)
        RowGap(Space.gapTight)
        Swatch(color ?: p.panel, name = label)
    }
    ColorModal(
        open = open,
        title = label,
        summary = "",
        initial = color ?: p.accent,
        onColor = onColor,
        onReset = { onReset?.invoke() },
    )
}
