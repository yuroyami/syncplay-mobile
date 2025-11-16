package com.yuroyami.syncplay.ui.theme

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.yuroyami.syncplay.ui.components.FlexibleText
import com.yuroyami.syncplay.ui.components.gradientOverlay
import com.yuroyami.syncplay.ui.components.lexendFont
import com.yuroyami.syncplay.ui.components.solidOverlay
import com.yuroyami.syncplay.ui.screens.Screen
import com.yuroyami.syncplay.ui.screens.adam.LocalGlobalViewmodel
import com.yuroyami.syncplay.ui.screens.adam.LocalTheme

val availableThemes = listOf(PYNCSLAY, GrayOLED, GREEN_GOBLIN, ALLEY_LAMP)

val themeCardSize = 64.dp

@Composable
fun ThemeMenu(visible: Boolean, onDismiss: () -> Unit) {
    val globalViewmodel = LocalGlobalViewmodel.current

    if (visible) {
        BasicAlertDialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            val viewmodel = LocalGlobalViewmodel.current
            val currentTheme = LocalTheme.current
            val allCustomThemes by viewmodel.themeManager.customThemes.collectAsStateWithLifecycle()

            val primary = MaterialTheme.colorScheme.primary
            val srfc0 = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
            val srfc = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)

            var themeToEditOrDelete by remember { mutableStateOf<SaveableTheme?>(null) }

            Column(
                modifier = Modifier.background(color = srfc0),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                HorizontalDivider(color = Color.White, modifier = Modifier.gradientOverlay())

                Spacer(Modifier.height(12.dp))

                FlexibleText(
                    text = "Select a theme", //TODO Localize
                    size = 16f,
                    textAlign = TextAlign.Center,
                    fillingColors = listOf(MaterialTheme.colorScheme.primary),
                    font = lexendFont,
                    strokeColors = listOf(MaterialTheme.colorScheme.scrim),
                    shadowColors = Theming.SP_GRADIENT.map { it.copy(alpha = 0.65f) },
                    shadowSize = 3f
                )

                Surface(
                    color = srfc,
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    tonalElevation = 2.dp,
                    shadowElevation = 4.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        //TODO Localize
                        Text(
                            text = "Built-in themes",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.Start).padding(start = 8.dp)
                        )

                        HorizontalDivider(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .gradientOverlay(colors = listOf(primary, primary, primary, srfc, srfc))
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            availableThemes.forEach { theme ->
                                ThemeEntry(
                                    modifier = Modifier.clickable(
                                        interactionSource = null,
                                        indication = ripple(),
                                        onClick = {
                                            themeToEditOrDelete = null
                                            viewmodel.themeManager.changeTheme(theme)
                                        }
                                    ),
                                    theme = theme,
                                    isSelected = currentTheme == theme,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(2.dp))

                Surface(
                    color = srfc,
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    tonalElevation = 2.dp,
                    shadowElevation = 4.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        //TODO Localize
                        Text(
                            text = "Custom themes",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.Start).padding(start = 8.dp)
                        )

                        HorizontalDivider(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .gradientOverlay(colors = listOf(primary, primary, primary, srfc, srfc))
                        )

                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(themeCardSize.times(2f))
                                .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                                                viewmodel.themeManager.changeTheme(theme)
                                            },
                                            onLongClick = {
                                                themeToEditOrDelete = theme
                                            }
                                        ).run {
                                            if (themeToEditOrDelete == theme) {
                                                this.border(
                                                    width = 1.dp,
                                                    brush = Brush.linearGradient(colors = Theming.SP_GRADIENT),
                                                    shape = RoundedCornerShape(8.dp)
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
                                    Text("Cancel")
                                }

                                TextButton(
                                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                                    onClick = {
                                        viewmodel.themeManager.deleteTheme(themeToEditOrDelete!!)
                                        themeToEditOrDelete = null
                                    }
                                ) {
                                    Icon(Icons.Filled.Delete, null)
                                    Text("Delete")
                                }

                                TextButton(
                                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                                    onClick = {
                                        globalViewmodel.backstack.add(Screen.ThemeCreator(themeToEditOrDelete))
                                        themeToEditOrDelete = null
                                    }
                                ) {
                                    Icon(Icons.Filled.Edit, null)
                                    Text("Edit")
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))


                HorizontalDivider(color = Color.White, modifier = Modifier.gradientOverlay())
            }
        }
    }
}


@Composable
fun ThemeEntry(modifier: Modifier, theme: SaveableTheme, isSelected: Boolean) {
    val dynamicScheme = remember { theme.dynamicScheme }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(themeCardSize).padding(vertical = 8.dp)
    ) {
        Card(
            modifier = modifier
                .size(themeCardSize)
                .solidOverlay(
                    if (isSelected) Color.Transparent else Color.Black.copy(alpha = 0.2f)
                ),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(Dp.Hairline, color = Color.Black)
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
                    Icon(imageVector = Icons.Outlined.DoneOutline, contentDescription = null)
                }
            }
        }
        Text(
            modifier = Modifier.width(themeCardSize - 4.dp).safeContentPadding().padding(2.dp),
            text = theme.name,
            autoSize = TextAutoSize.StepBased(minFontSize = 1.sp, maxFontSize = 25.sp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}


@Composable
fun AddCustomizedThemeButton(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(themeCardSize).padding(vertical = 8.dp)
    ) {
        Card(
            modifier = Modifier.size(themeCardSize)
                .border(
                    width = Dp.Hairline,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(8.dp)
                )
                .drawWithContent {
                    drawContent()
                    drawRoundRect(
                        color = Color.Gray,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                intervals = floatArrayOf(10f, 10f),
                                phase = 0f
                            )
                        ),
                        cornerRadius = CornerRadius(8.dp.toPx())
                    )
                },
            shape = RoundedCornerShape(8.dp),
            onClick = onClick,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().size(themeCardSize)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Outlined.Add, contentDescription = null)
            }
        }
        Text(
            modifier = Modifier.width(themeCardSize - 4.dp).safeContentPadding().padding(2.dp),
            text = "Customize", //TODO Localize
            autoSize = TextAutoSize.StepBased(minFontSize = 1.sp, maxFontSize = 25.sp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}