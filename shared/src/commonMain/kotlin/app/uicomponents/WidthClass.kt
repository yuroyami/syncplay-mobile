package app.uicomponents

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

/** The three width classes every surface reads: compact under 480dp, medium to 839dp, expanded above. */
enum class WidthClass { Compact, Medium, Expanded }

val LocalWidthClass = compositionLocalOf { WidthClass.Compact }

/** The window's width class right now; provided once at the root. */
@Composable
fun currentWidthClass(): WidthClass {
    val width = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    return when {
        width < 480.dp -> WidthClass.Compact
        width < 840.dp -> WidthClass.Medium
        else -> WidthClass.Expanded
    }
}
