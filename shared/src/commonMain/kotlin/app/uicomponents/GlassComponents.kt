package app.uicomponents

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties

/*
 * Material 3 overlays wearing the app's frosted glass.
 *
 * Each one works the same way: the component's own container is made transparent so it paints
 * nothing, and [glassSurface] draws the blur, tint and edge in its place. The component keeps
 * owning its position, animation, scrim and gestures. Menus are small and usually sit over UI
 * rather than video, so they take a thinner material than the full-screen surfaces.
 */

/** [DropdownMenu] on glass. Caller modifiers run first so size caps still measure correctly. */
@Composable
fun GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    shape: Shape = MaterialTheme.shapes.small,
    material: GlassMaterial = GlassMaterial.Regular,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.glassSurface(shape = shape, material = material),
        offset = offset,
        scrollState = scrollState,
        properties = properties,
        shape = shape,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = null,
        content = content,
    )
}

/** [ExposedDropdownMenuBoxScope.ExposedDropdownMenu] on glass. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExposedDropdownMenuBoxScope.GlassExposedDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    matchAnchorWidth: Boolean = true,
    shape: Shape = MaterialTheme.shapes.small,
    material: GlassMaterial = GlassMaterial.Regular,
    content: @Composable ColumnScope.() -> Unit,
) {
    ExposedDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.glassSurface(shape = shape, material = material),
        scrollState = scrollState,
        matchAnchorWidth = matchAnchorWidth,
        shape = shape,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = null,
        content = content,
    )
}

/** [ModalBottomSheet] on glass. Its own scrim is dropped for the app-wide [glassScrim]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    material: GlassMaterial = GlassMaterial.Thin,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.windowInsets },
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.glassSurface(shape = shape, material = material),
        sheetState = sheetState,
        shape = shape,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        scrimColor = glassScrim,
        dragHandle = dragHandle,
        contentWindowInsets = contentWindowInsets,
        content = content,
    )
}

/**
 * [AlertDialog] on glass. Also asks the platform to blur behind the dialog window, which is the
 * only way the blur reaches a video surface (see [DialogBackdropBlur]).
 */
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    material: GlassMaterial = GlassMaterial.Thin,
    tonalElevation: Dp = 0.dp,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        // The flag has to be set from inside the dialog's own window, and this slot is the only
        // one that is always present. DialogBackdropBlur emits no UI, so the layout is unchanged.
        confirmButton = { DialogBackdropBlur(); confirmButton() },
        modifier = modifier.glassSurface(shape = shape, material = material),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = Color.Transparent,
        tonalElevation = tonalElevation,
    )
}
