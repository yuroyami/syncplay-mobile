package app.uicomponents

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

/** The three window width classes: compact below 480dp, medium below 840dp, expanded from 840dp. */
enum class WidthClass { Compact, Medium, Expanded }

val LocalWidthClass = compositionLocalOf { WidthClass.Compact }

/** The window's current width class. The root provides it once as [LocalWidthClass]. */
@Composable
fun currentWidthClass(): WidthClass {
    val width = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    return when {
        width < 480.dp -> WidthClass.Compact
        width < 840.dp -> WidthClass.Medium
        else -> WidthClass.Expanded
    }
}
