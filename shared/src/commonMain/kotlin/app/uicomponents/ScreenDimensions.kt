package app.uicomponents

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val screenHeightPx: Int
    @Composable get() = LocalWindowInfo.current.containerSize.height

val screenWidthPx: Int
    @Composable get() = LocalWindowInfo.current.containerSize.width

/**
 * Height cap that every `ExposedDropdownMenu` must carry.
 *
 * Material3's `ExposedDropdownMenuPositionProvider` ends its vertical-placement loop with a bare
 * `coerceIn(48.dp, windowHeight - 48.dp - menuHeight)`. Once the menu is taller than
 * `windowHeight - 96.dp` that range inverts and Kotlin throws. The plain `DropdownMenu` guards the
 * same spot by centering instead; the exposed one does not.
 *
 * The menu's own height cap is only refreshed when the anchor re-lays-out (or while the menu is
 * already open), so it can outlive an orientation flip and describe a window that no longer exists.
 * Measuring against the SHORTER window side sidesteps that entirely: a menu legal in landscape is
 * legal in portrait too, whichever size the popup is finally measured against.
 */
val dropdownMenuMaxHeight: Dp
    @Composable get() {
        val window = LocalWindowInfo.current.containerSize
        return with(LocalDensity.current) {
            (minOf(window.width, window.height).toDp() - 112.dp).coerceAtLeast(96.dp)
        }
    }
