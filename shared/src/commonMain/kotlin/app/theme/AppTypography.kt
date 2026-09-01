package app.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable

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
