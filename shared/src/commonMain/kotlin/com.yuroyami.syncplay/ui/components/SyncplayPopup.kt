package com.yuroyami.syncplay.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys
import com.yuroyami.syncplay.managers.datastore.valueAsState
import com.yuroyami.syncplay.models.MessagePalette
import com.yuroyami.syncplay.ui.theme.Theming
import com.yuroyami.syncplay.ui.theme.Theming.backgroundGradient
import org.jetbrains.compose.resources.Font
import syncplaymobile.shared.generated.resources.Directive4_Regular
import syncplaymobile.shared.generated.resources.Helvetica_Regular
import syncplaymobile.shared.generated.resources.Res

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
    strokeWidth: Float = 1.5f,
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
                border = BorderStroke(width = strokeWidth.dp, brush = Brush.linearGradient(Theming.SP_GRADIENT))
            ) {
                content()
            }
        }
    }
}