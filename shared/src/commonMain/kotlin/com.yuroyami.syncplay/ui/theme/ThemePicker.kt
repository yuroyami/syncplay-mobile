package com.yuroyami.syncplay.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuroyami.syncplay.managers.ThemeManager
import com.yuroyami.syncplay.ui.screens.adam.LocalRoomViewmodel
import com.yuroyami.syncplay.ui.utils.SyncplayPopup
import com.yuroyami.syncplay.ui.utils.solidOverlay

val availableThemes = listOf(ThemeManager.PYNCSLAY, ThemeManager.AMOLED, ThemeManager.GREEN_GOBLIN, ThemeManager.ALLEY_LAMPPOST)

@Composable
fun ThemeMenu(visible: Boolean, onDismiss: () -> Unit) {
    SyncplayPopup(
        dialogOpen = visible,
        onDismiss = onDismiss,
    ) {
        val viewmodel = LocalRoomViewmodel.current
        val currentTheme by viewmodel.themeManager.currentTheme.collectAsState()

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            //TODO Localization
            Text("Select a theme by clicking on it", modifier = Modifier.padding(6.dp))

            availableThemes.forEach { theme ->
                ThemeEntry(theme, isSelected = currentTheme == theme)
            }
        }
    }
}


@Composable
fun ThemeEntry(theme: ThemeManager.Companion.Theme, isSelected: Boolean) {
    val viewmodel = LocalRoomViewmodel.current

    Card(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).solidOverlay(
            if (isSelected) Color.Transparent else Color.Black.copy(alpha = 0.65f)
        ),
        shape = RoundedCornerShape(8.dp),
        onClick = {
            viewmodel.themeManager.currentTheme.value = theme
        }
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(64.dp)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(theme.scheme.primary, theme.scheme.secondary, theme.scheme.tertiary)
                    )
                )
        ) {
            Text(
                text = theme.name,
                fontSize = 24.sp,
                modifier = Modifier.align(Alignment.Center),
                color = theme.scheme.onPrimary
            )
        }
    }
}