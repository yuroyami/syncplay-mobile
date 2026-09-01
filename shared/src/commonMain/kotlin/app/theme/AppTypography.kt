package app.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * The Material bridge. Some Compose internals read `MaterialTheme.typography`, so it stays
 * populated, with every Material role pointed at the nearest Synkplay role from [Type]. App code
 * never reads these roles directly; it reads [Type].
 */
val appTypography: Typography
    @Composable get() {
        val t = LocalType.current
        return Typography(
            displayLarge = t.display,
            displayMedium = t.display,
            displaySmall = t.display,
            headlineLarge = t.display,
            headlineMedium = t.display,
            headlineSmall = t.display,
            titleLarge = t.display,
            titleMedium = t.label,
            titleSmall = t.label,
            bodyLarge = t.note,
            bodyMedium = t.note,
            bodySmall = t.note,
            labelLarge = t.label,
            labelMedium = t.value,
            labelSmall = t.value,
        )
    }

/**
 * Still Material's rounding. Glass surfaces take their default shape from here today, so this
 * moves to the [Radius] ladder only once glass reads its shape from its tier (DESIGN/GLASS_SURFACES).
 */
val appShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
