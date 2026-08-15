package app.uicomponents

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Shows a popup with the given content.
 *
 * Design convention (applies to every popup in the app): there is NO card and NO container
 * color. The content is drawn directly on a full-screen dim layer, gathered around the middle
 * of the screen. Popups should keep their content compact and centered instead of scattering
 * elements towards the screen edges.
 *
 * @param dialogOpen Controls whether the popup dialog is shown or not.
 * When this is false, the dialog is not rendered at all.
 * @param widthPercent Width the content occupies relative to the screen's width. 0f by default (wraps content).
 * @param heightPercent Percentage of screen's height the content occupies. 0f by default (wraps content).
 * @param dismissable Whether the popup dialog can be dismissed or not (via outside click or backpress).
 * @param onDismiss Block of code to execute when there is a dismiss request. If dismissable is false,
 * then the block of code will never get executed (you would have to close the dialog manually via booleans).
 * @param content Composable content, laid out in a centered column on the dim layer.*/
@Composable
fun SyncplayPopup(
    dialogOpen: Boolean,
    widthPercent: Float = 0f,
    heightPercent: Float = 0f,
    dismissable: Boolean = true,
    onDismiss: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    if (dialogOpen) {
        Dialog(
            onDismissRequest = {
                onDismiss()
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false, // Handled by scrim click below
                dismissOnBackPress = dismissable
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Heavy scrim on top of the platform dialog dim: with no card behind the
                    // content, the dim layer alone must guarantee readability over any video.
                    .background(Color.Black.copy(alpha = 0.55f))
                    // Keep the centered content above the soft keyboard for input popups.
                    .imePadding()
                    .then(
                        if (dismissable) Modifier.clickable(
                            interactionSource = null,
                            indication = null
                        ) { onDismiss() }
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .run { if (widthPercent == 0f) this else fillMaxWidth(widthPercent) }
                        .run { if (heightPercent == 0f) this else fillMaxHeight(heightPercent) }
                        .padding(24.dp)
                        .clickable(
                            interactionSource = null,
                            indication = null
                        ) { /* Consume clicks on the content area to prevent scrim dismiss */ },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                    content = content
                )
            }
        }
    }
}
