package com.yuroyami.syncplay.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yuroyami.syncplay.ui.theme.Theming.backgroundGradient
import com.yuroyami.syncplay.ui.theme.Theming.flexibleGradient

/** Shows a popup with the given content.
 * @param dialogOpen Controls whether the popup dialog is shown or not.
 * When this is false, the dialog is not rendered at all.
 * @param cardBackgroundColor Color of the card that wraps dialog content. Gray by default.
 * @param widthPercent Width it occupies relative to the screen's width. 0f by default (wraps content).
 * @param heightPercent Percentage of screen's height it occupies. 0f by default (wraps content).
 * @param blurState A [MutableState] variable we should pass to control blur on other composables
 * using Cloudy. The dialog will control the mutable state for us and all we have to do is wrap
 * our Composables in Cloudy composables with the value of said mutable state.
 * @param dismissable Whether the popup dialog can be dismissed or not (via outside click or backpress).
 * @param onDismiss Block of code to execute when there is a dismiss request. If dismissable is false,
 * then the block of code will never get executed (you would have to close the dialog manually via booleans).
 * @param content Composable content.*/
@Composable
fun SyncplayPopup(
    dialogOpen: Boolean,
    alpha: Float = 0.88f,
    cardCornerRadius: Int = 10,
    strokeWidth: Float = 0f,
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
                dismissOnClickOutside = dismissable,
                dismissOnBackPress = dismissable
            )
        ) {
            Card(
                modifier = Modifier
                    .run { if (widthPercent == 0f) this else fillMaxWidth(widthPercent) }
                    .run { if (heightPercent == 0f) this else fillMaxHeight(heightPercent) }
                    .padding(24.dp)
                    .background(
                        shape = RoundedCornerShape(size = cardCornerRadius.dp),
                        brush = Brush.linearGradient(
                            backgroundGradient.map { it.copy(alpha = alpha) }
                        )
                    ), //Safe margin to prevent popup from covering all the screen
                shape = RoundedCornerShape(size = cardCornerRadius.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                border = BorderStroke(width = Dp(strokeWidth), brush = Brush.linearGradient(flexibleGradient))
            ) {
                content()
            }
        }
    }
}