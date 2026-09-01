package app.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DoneOutline
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.LocalGlobalViewmodel
import app.LocalTheme
import app.Screen
import app.uicomponents.solidOverlay
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.cancel
import syncplaymobile.shared.generated.resources.delete
import syncplaymobile.shared.generated.resources.edit
import syncplaymobile.shared.generated.resources.theme_popup_builtin_themes
import syncplaymobile.shared.generated.resources.theme_popup_subtitle
import syncplaymobile.shared.generated.resources.theme_popup_custom_themes
import syncplaymobile.shared.generated.resources.theme_popup_customize_button
import syncplaymobile.shared.generated.resources.theme_popup_select_a_theme
import app.uicomponents.DialogBackdropBlur
import app.uicomponents.glassSurface

val availableThemes = listOf(TRINITY, DAYLIGHT, SILVER_LAKE, PYNCSLAY, GrayOLED, ALLEY_LAMP)

val themeCardSize = 72.dp

@Composable
fun ThemeMenu(visible: Boolean, onDismiss: () -> Unit) {
    val globalViewmodel = LocalGlobalViewmodel.current

    if (visible) {
        BasicAlertDialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            DialogBackdropBlur()

            val viewmodel = LocalGlobalViewmodel.current
            val currentTheme = LocalTheme.current
            val allCustomThemes by viewmodel.customThemes.collectAsStateWithLifecycle()

            var themeToEditOrDelete by remember { mutableStateOf<SaveableTheme?>(null) }

            Column(
                modifier = Modifier
                    .padding(horizontal = Theming.SpaceLG)
                    .glassSurface()
                    .padding(Theming.SpaceLG),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(Res.string.theme_popup_select_a_theme),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(Res.string.theme_popup_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp, bottom = Theming.SpaceMD)
                )

                ThemeSection(title = stringResource(Res.string.theme_popup_builtin_themes)) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(Theming.SpaceXS),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Theming.SpaceMD),
                    ) {
                        items(availableThemes.size) { index ->
                            val theme = availableThemes[index]
                            ThemeEntry(
                                modifier = Modifier.clickable(
                                    interactionSource = null,
                                    indication = ripple(),
                                    onClick = {
                                        themeToEditOrDelete = null
                                        viewmodel.changeTheme(theme)
                                    }
                                ),
                                theme = theme,
                                isSelected = currentTheme == theme,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(Theming.SpaceSM))

                ThemeSection(title = stringResource(Res.string.theme_popup_custom_themes)) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Theming.SpaceXS),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Theming.SpaceMD),
                    ) {
                        item {
                            AddCustomizedThemeButton(
                                onClick = {
                                    themeToEditOrDelete = null
                                    globalViewmodel.backstack.add(Screen.ThemeCreator())
                                }
                            )
                        }

                        items(allCustomThemes.size) { index ->
                            val theme = allCustomThemes[allCustomThemes.size - 1 - index]

                            ThemeEntry(
                                modifier = Modifier
                                    .combinedClickable(
                                        interactionSource = null,
                                        indication = ripple(),
                                        onClick = {
                                            themeToEditOrDelete = null
                                            viewmodel.changeTheme(theme)
                                        },
                                        onLongClick = {
                                            themeToEditOrDelete = theme
                                        }
                                    ).run {
                                        if (themeToEditOrDelete == theme) {
                                            this.border(
                                                width = 1.dp,
                                                brush = Brush.linearGradient(colors = Theming.SP_GRADIENT),
                                                shape = MaterialTheme.shapes.small
                                            )
                                        } else this
                                    },
                                theme = theme,
                                isSelected = currentTheme == theme
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = themeToEditOrDelete != null,
                        enter = expandVertically(animationSpec = keyframes { durationMillis = 100 }),
                        exit = shrinkVertically(animationSpec = keyframes { durationMillis = 100 })
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            TextButton(
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                                onClick = {
                                    themeToEditOrDelete = null
                                }
                            ) {
                                Icon(Icons.Filled.Close, null)
                                Text(stringResource(Res.string.cancel))
                            }

                            TextButton(
                                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                                onClick = {
                                    viewmodel.deleteTheme(themeToEditOrDelete!!)
                                    themeToEditOrDelete = null
                                }
                            ) {
                                Icon(Icons.Filled.Delete, null)
                                Text(stringResource(Res.string.delete))
                            }

                            TextButton(
                                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                                onClick = {
                                    globalViewmodel.backstack.add(Screen.ThemeCreator(themeToEditOrDelete))
                                    themeToEditOrDelete = null
                                }
                            ) {
                                Icon(Icons.Filled.Edit, null)
                                Text(stringResource(Res.string.edit))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One titled section of the theme picker (built-in / custom themes). */
@Composable
private fun ThemeSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(vertical = Theming.SpaceXS)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Theming.SpaceSM)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Start).padding(start = Theming.SpaceSM)
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = Theming.SpaceSM, vertical = Theming.SpaceXS),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            content()
        }
    }
}

@Composable
fun ThemeEntry(modifier: Modifier, theme: SaveableTheme, isSelected: Boolean) {
    val dynamicScheme = remember { theme.dynamicScheme }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(themeCardSize).padding(vertical = Theming.SpaceSM)
    ) {
        Card(
            modifier = modifier
                .size(themeCardSize)
                .solidOverlay(
                    if (isSelected) Color.Transparent else Color.Black.copy(alpha = 0.2f)
                ),
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(
                width = if (isSelected) 2.dp else Dp.Hairline,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().size(themeCardSize)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                dynamicScheme.primary, dynamicScheme.tertiaryContainer, dynamicScheme.background
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Outlined.DoneOutline,
                        contentDescription = null,
                        tint = dynamicScheme.onPrimary
                    )
                }
            }
        }

        Text(
            modifier = Modifier.width(themeCardSize - 4.dp).padding(2.dp),
            text = theme.name,
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 14.sp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}


@Composable
fun AddCustomizedThemeButton(onClick: () -> Unit) {
    val outline = MaterialTheme.colorScheme.outline

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(themeCardSize).padding(vertical = Theming.SpaceSM)
    ) {
        Card(
            modifier = Modifier.size(themeCardSize)
                .drawWithContent {
                    drawContent()
                    drawRoundRect(
                        color = outline,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                intervals = floatArrayOf(10f, 10f),
                                phase = 0f
                            )
                        ),
                        cornerRadius = CornerRadius(12.dp.toPx())
                    )
                },
            shape = MaterialTheme.shapes.small,
            onClick = onClick,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().size(themeCardSize)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Text(
            modifier = Modifier.width(themeCardSize).padding(top = 2.dp),
            text = stringResource(Res.string.theme_popup_customize_button),
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 14.sp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
