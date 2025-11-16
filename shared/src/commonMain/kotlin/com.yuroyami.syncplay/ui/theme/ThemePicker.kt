package com.yuroyami.syncplay.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DoneOutline
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.yuroyami.syncplay.managers.ThemeManager
import com.yuroyami.syncplay.ui.components.FlexibleText
import com.yuroyami.syncplay.ui.components.gradientOverlay
import com.yuroyami.syncplay.ui.components.lexendFont
import com.yuroyami.syncplay.ui.components.solidOverlay
import com.yuroyami.syncplay.ui.screens.adam.LocalGlobalViewmodel

val availableThemes = listOf(ThemeManager.PYNCSLAY, ThemeManager.AMOLED, ThemeManager.GREEN_GOBLIN, ThemeManager.ALLEY_LAMPPOST)

@Composable
fun ThemeMenu(visible: Boolean, onDismiss: () -> Unit) {
    if (visible) {
        BasicAlertDialog(
            modifier = Modifier,//.background(Color.Black),
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            val viewmodel = LocalGlobalViewmodel.current
            val currentTheme by viewmodel.themeManager.currentTheme.collectAsState()

            Column(
                modifier = Modifier.background(color = MaterialTheme.colorScheme.background.copy(alpha = 0.85f)),
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
                    color = Color.Black,
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    tonalElevation = 2.dp,
                    shadowElevation = 4.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        Text("Built-in themes", modifier = Modifier.align(Alignment.Start))

                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 56.dp),
                            modifier = Modifier.fillMaxWidth().padding(4.dp)
                        ) {
                            items(availableThemes.size) { index ->
                                val theme = availableThemes[index]
                                ThemeEntry(theme, isSelected = currentTheme == theme)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Surface(
                    color = Color.Black,
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    tonalElevation = 2.dp,
                    shadowElevation = 4.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        Text("Custom themes", modifier = Modifier.align(Alignment.Start))

                        FlowRow {
                            availableThemes.forEach { theme ->
                                ThemeEntry(theme, isSelected = currentTheme == theme)
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
fun ThemeEntry(theme: ThemeManager.Companion.Theme, isSelected: Boolean) {
    val viewmodel = LocalGlobalViewmodel.current

    val size = 56.dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).size(size)
    ) {
        Card(
            modifier = Modifier
                .size(size)
                .solidOverlay(
                    if (isSelected) Color.Transparent else Color.Black.copy(alpha = 0.45f)
                ),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, brush = Brush.linearGradient(colors = Theming.SP_GRADIENT)),
            onClick = {
                viewmodel.themeManager.currentTheme.value = theme
            }
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().size(size)
                    .background(brush = Brush.linearGradient(
                        colors = listOf(theme.scheme.primary, theme.scheme.secondary, theme.scheme.tertiary))
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(imageVector = Icons.Outlined.DoneOutline, contentDescription = null)
                }
            }
        }
        Text(
            modifier = Modifier.basicMarquee().gradientOverlay().width(size),
            text = theme.name,
            fontSize = 11.sp,
            color = theme.scheme.onPrimary,
            maxLines = 1
        )
    }
}