package app.ui.compose

import androidx.annotation.FloatRange
import androidx.annotation.IntRange
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.R
import app.ui.Paletting

/** Contains a bunch of composable functions that are frequently reused */
object ComposeUtils {

    /** Creates a fancy Syncplay-themed icon (solid foreground and gradient trailing shadow) */
    @Composable
    fun FancyIcon(modifier: Modifier = Modifier, icon: ImageVector?, size: Int, onClick: () -> Unit = {}) {
        Box(modifier = modifier) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = "",
                    modifier = modifier
                        .size(size.dp)
                        .align(Alignment.Center)
                        .syncplayGradient()
                )
                Icon(
                    imageVector = icon,
                    contentDescription = "",
                    modifier = modifier
                        .size((size - 2).dp)
                        .align(Alignment.Center),
                    tint = Color.DarkGray
                )

            } else {
                Spacer(modifier = Modifier.size(size.dp))
            }
        }
    }

    /** Creates a fancy Syncplay-themed icon (gradient foreground and a solid trailing shadow) */
    @Composable
    fun FancyIcon2(
        modifier: Modifier = Modifier,
        icon: ImageVector, size: Int = Paletting.ROOM_ICON_SIZE, shadowColor: Color = Color.Gray,
        onClick: () -> Unit = {},
    ) {
        IconButton(
            modifier = modifier
                .alpha(0.9f)
                .wrapContentSize()
                .size(size.dp),
            onClick = { onClick() }) {
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .wrapContentSize()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "",
                    modifier = Modifier
                        .size(size.dp)
                        .offset(x = 2.dp, y = 1.dp),
                    tint = shadowColor
                )
                Icon(
                    imageVector = icon,
                    contentDescription = "",
                    modifier = Modifier
                        .size(size.dp)
                        .alpha(0.9f)
                        .syncplayGradient(),
                )
            }
        }
    }

    /** Creates a text with: Syncplay Gradient Stroke + Solid Filling */
    @OptIn(ExperimentalTextApi::class)
    @Composable
    fun FancyText(
        modifier: Modifier = Modifier,
        string: String,
        solid: Color,
        size: Float,
        font: Font,
    ) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = string,
                style = TextStyle(
                    brush = Brush.linearGradient(
                        colors = Paletting.SP_GRADIENT
                    ),
                    drawStyle = Stroke(
                        miter = 10f,
                        width = 2f,
                        join = StrokeJoin.Round
                    ),
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily(font),
                    fontSize = size.sp,
                )
            )
            Text(
                text = string,
                style = TextStyle(
                    color = solid,
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily(font),
                    fontSize = size.sp,
                )
            )
        }
    }

    /** Creates a text with: Solid Stroke + Syncplay Gradient Filling */
    @OptIn(ExperimentalTextApi::class)
    @Composable
    fun FancyText2(
        modifier: Modifier = Modifier,
        string: String,
        solid: Color,
        size: Float,
        font: Font,
    ) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = string,
                style = TextStyle(
                    brush = Brush.linearGradient(
                        colors = Paletting.SP_GRADIENT
                    ),
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily(font),
                    fontSize = size.sp,
                )
            )
            Text(
                text = string,
                style = TextStyle(
                    color = solid,
                    drawStyle = Stroke(
                        miter = 10f,
                        width = 2f,
                        join = StrokeJoin.Round
                    ),
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily(font),
                    fontSize = size.sp,
                )
            )
        }
    }

    /** Creates a multi-choice dialog which is populated by the given list */
    @OptIn(ExperimentalTextApi::class)
    @Composable
    fun MultiChoiceDialog(
        title: String = "",
        subtext: String = "",
        items: List<String>,
        selectedItem: Int,
        onItemClick: (Int) -> Unit,
        onDismiss: () -> Unit,
    ) {
        Dialog(onDismissRequest = { onDismiss() }) {
            Card(
                modifier = Modifier
                    .wrapContentWidth()
                    .wrapContentHeight(),
                shape = RoundedCornerShape(size = 10.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Gray)
            ) {
                Column(modifier = Modifier.padding(all = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (title != "") {
                        Box(modifier = Modifier.wrapContentWidth(), contentAlignment = Alignment.Center) {
                            Text(
                                text = title,
                                style = TextStyle(
                                    brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT),
                                    shadow = Shadow(offset = Offset(0f, 0f), blurRadius = 1f),
                                    fontFamily = FontFamily(Font(R.font.directive4bold)), fontSize = (15.6).sp
                                )
                            )
                            Text(
                                text = title,
                                style = TextStyle(
                                    color = Color.DarkGray,
                                    fontFamily = FontFamily(Font(R.font.directive4bold)), fontSize = 15.sp,
                                )
                            )
                        }
                    }

                    for ((index, item) in items.withIndex()) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = rememberRipple(bounded = true, color = Paletting.SP_ORANGE)
                            ) {
                                onItemClick(index)
                                onDismiss()
                            }
                        ) {
                            RadioButton(
                                selected = index == selectedItem,
                                onClick = {
                                    onItemClick(index)
                                    onDismiss()
                                }
                            )
                            Text(
                                text = item, modifier = Modifier
                                    .fillMaxWidth(0.75f)
                                    .padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }


    /** Adds a syncplayish gradient to the modifier (convenient) */
    fun Modifier.syncplayGradient(): Modifier {
        return this
            .graphicsLayer(alpha = 0.99f)
            .drawWithCache {
                onDrawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = Paletting.SP_GRADIENT
                        ), blendMode = BlendMode.SrcAtop
                    )
                }
            }
    }

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
    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    fun RoomPopup(
        dialogOpen: Boolean,
        cardBackgroundColor: Color = Color.Gray,
        @IntRange(from = 0) cardCornerRadius: Int = 10,
        @FloatRange(0.0, 10.0) strokeWidth: Float = 1.5f,
        @FloatRange(0.0, 1.0) widthPercent: Float = 0f,
        @FloatRange(0.0, 1.0) heightPercent: Float = 0f,
        dismissable: Boolean = true,
        onDismiss: () -> Unit = {},
        content: @Composable BoxScope.() -> Unit,
    ) {
        if (dialogOpen) {
            Dialog(
                onDismissRequest = { onDismiss() },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false,
                    dismissOnClickOutside = dismissable,
                    dismissOnBackPress = dismissable
                )
            ) {
                var modifier: Modifier = Modifier
                modifier = if (widthPercent == 0f) {
                    modifier.wrapContentWidth()
                } else {
                    modifier.fillMaxWidth(widthPercent)
                }
                modifier = if (heightPercent == 0f) {
                    modifier.wrapContentHeight()
                } else {
                    modifier.fillMaxHeight(heightPercent)
                }

                Card(
                    modifier = modifier,
                    shape = RoundedCornerShape(size = cardCornerRadius.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    border = BorderStroke(width = strokeWidth.dp, brush = Brush.linearGradient(Paletting.SP_GRADIENT))
                ) {
                    Box(modifier = Modifier
                        .fillMaxSize()
                        .background(brush = Brush.linearGradient(Paletting.BG_Gradient_DARK))) {
                        content()
                    }
                }

            }
        }
    }

}