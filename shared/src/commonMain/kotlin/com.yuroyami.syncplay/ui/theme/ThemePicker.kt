package com.yuroyami.syncplay.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuroyami.syncplay.ui.utils.SyncplayPopup

/**
 * Defines a Theme.
 *
 * @param name The name of theme, appearing on the theme selection menu.
 * @param primary The primary color, used for button filling and primary texts and titles.
 * @param secondary The secondary color, used in secondary fillings such as textfield filling.
 * @param tertiary The tertiary color, used in texts and icons that are inside buttons (should go well with primary).
 * @param background The background color, used for backgrounds such as popup background or screen background.
 * @param isDarkTheme Whether it's a dark theme, which changes the behavior/contrast of the system bars.
 * @param usesSyncplayGradient Whether the standard Syncplay gradient is used to outline buttons and icons. When false, secondary is used.
 */
data class Theme(
    val name: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val background: Color,
    val isDarkTheme: Boolean,
    val usesSyncplayGradient: Boolean
)

val lavenderTheme = Theme(
    name = "Lavender",
    primary = Color.Black,
    secondary = Color.Red,
    tertiary = Color.Magenta,
    background = Color.Green,
    isDarkTheme = false,
    usesSyncplayGradient = false
)

val moonlightTheme = Theme(
    name = "Moonlight",
    primary = Color.Black,
    secondary = Color.Red,
    tertiary = Color.Magenta,
    background = Color.Green,
    isDarkTheme = true,
    usesSyncplayGradient = true
)

val blackoledTheme = Theme(
    name = "BLACKOLED",
    primary = Color.White,
    secondary = Color.Gray,
    tertiary = Color.LightGray,
    background = Color.Black,
    isDarkTheme = true,
    usesSyncplayGradient = true
)

val availableThemes = listOf(lavenderTheme, moonlightTheme, blackoledTheme)

@Composable
fun ThemeMenu(visible: Boolean, onDismiss: () -> Unit) {
    SyncplayPopup(
        dialogOpen = visible,
        onDismiss = onDismiss,
    ) {
        Column {
            Text("Choose a Theme")

            availableThemes.forEach { theme ->
                ThemeEntry(theme)
            }
        }
    }
}


@Composable
fun ThemeEntry(theme: Theme) {
    Surface(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(8.dp),
        onClick = {

        }
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(64.dp)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(theme.primary, theme.secondary)
                    )
                )
        ) {
            Text(
                text = theme.name,
                fontSize = 24.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}