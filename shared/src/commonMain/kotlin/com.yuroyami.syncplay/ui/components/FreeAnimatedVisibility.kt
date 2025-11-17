package com.yuroyami.syncplay.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Jetpack Compose provides three overloads of `AnimatedVisibility`.
 * Two of them have `ColumnScope` or `RowScope` as receivers, and one has neither.
 *
 * When adding `AnimatedVisibility` to a `Box` that's nested inside a `Column` or `Row`,
 * this can lead to ambiguity due to receiver resolution.
 *
 * This method is used to eliminate that ambiguity by explicitly selecting the correct overload.
 */
@Composable
fun FreeAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = fadeIn() + expandIn(),
    exit: ExitTransition = shrinkOut() + fadeOut(),
    label: String = "AnimatedVisibility",
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = enter,
        exit = exit,
        label = label,
        content = content
    )
}