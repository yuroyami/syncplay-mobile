package com.yuroyami.syncplay.components

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
import com.yuroyami.syncplay.models.MessagePalette
import com.yuroyami.syncplay.settings.DataStoreKeys
import com.yuroyami.syncplay.settings.valueAsState
import com.yuroyami.syncplay.ui.Paletting
import com.yuroyami.syncplay.ui.Paletting.backgroundGradient
import org.jetbrains.compose.resources.Font
import syncplaymobile.shared.generated.resources.Directive4_Regular
import syncplaymobile.shared.generated.resources.Res

/** Creates a text with syncplay style */
@Composable
fun SyncplayishText(
    modifier: Modifier = Modifier,
    string: String,
    size: Float,
    colorStops: List<Color> = Paletting.SP_GRADIENT,
    stroke: Color = Paletting.SP_PALE,
    shadow: Color = Paletting.SP_INTENSE_PINK,
    textAlign: TextAlign = TextAlign.Start,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            modifier = Modifier.wrapContentWidth(),
            text = string,
            textAlign = textAlign,
            maxLines = 1,
            style = TextStyle(
                color = stroke,
                drawStyle = Stroke(
                    miter = 10f,
                    width = 2f,
                    join = StrokeJoin.Round
                ),
                shadow = Shadow(
                    color = shadow,
                    offset = Offset(0f, 10f),
                    blurRadius = 5f
                ),
                fontFamily = FontFamily(Font(Res.font.Directive4_Regular)),
                fontSize = size.sp,
            )
        )
        Text(
            modifier = Modifier.wrapContentWidth(),
            text = string,
            textAlign = textAlign,
            maxLines = 1,
            style = TextStyle(
                brush = Brush.linearGradient(
                    colors = colorStops
                ),
                fontFamily = FontFamily(Font(Res.font.Directive4_Regular)),
                fontSize = size.sp,
            )
        )
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
                    .gradientOverlay(),
            )
        }
    }
}

/** Creates a smart fancy icon. It's basically an icon that takes a list of tint colors to apply gradient,
 * however, if the list only contains one color, then a color style will be used instead of a brush style.
 * That is because brush only supports gradients */
@Composable
fun SmartFancyIcon(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    size: Int = Paletting.ROOM_ICON_SIZE,
    tintColors: List<Color> = listOf(MaterialTheme.colorScheme.primary),
    shadowColors: List<Color> = Paletting.SP_GRADIENT,
    shadowOffset: Pair<Int, Int> = Pair(1, 1),
    alpha: Float = 0.95f,
    onClick: () -> Unit = {},
) {
    IconButton(
        modifier = modifier
            .alpha(alpha)
            .wrapContentSize()
            .size(size.dp),
        onClick = { onClick() }
    ) {
        Box(
            modifier = Modifier.padding(4.dp).wrapContentSize()
        ) {
            if (shadowColors.isNotEmpty()) {
                /** Shadow */
                val shadowModifier = Modifier
                    .size(size.dp)
                    .offset(x = shadowOffset.first.dp, y = shadowOffset.second.dp)
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = if (shadowColors.size == 1) shadowModifier else shadowModifier.gradientOverlay(shadowColors),
                    tint = if (shadowColors.size == 1) shadowColors.first() else Color.Black
                )
            }

            /** Foreground */
            val fgModifier = Modifier
                .size((size - 0.5).dp)
                .alpha(0.9f)

            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = if (tintColors.size <= 1) fgModifier else fgModifier.gradientOverlay(tintColors),
                tint = tintColors.firstOrNull() ?: Color.White
            )
        }
    }
}

/** Creates a flexible fancy text. It's basically a text that takes a list of colors to apply gradient or solid overlays,
 * For example, if you pass a filling list of one color, then a solid filling will be used, otherwise, gradient.
 * */
@Composable
fun FlexibleFancyText(
    modifier: Modifier = Modifier,
    text: String,
    size: Float,
    font: Font? = null,
    textAlign: TextAlign = TextAlign.Start,
    fillingColors: List<Color> = listOf(MaterialTheme.colorScheme.primary),
    strokeColors: List<Color> = listOf(),
    strokeWidth: Float = 2f,
    shadowColors: List<Color> = listOf(),
    shadowOffset: Pair<Int, Int> = Pair(2, 2),
    shadowSize: Float = 6f,
) {
    Box(modifier = modifier) {
        /** Shadow */
        if (shadowColors.isNotEmpty()) {
            Text(
                text = text,
                modifier = if (shadowColors.size == 1) Modifier else Modifier.gradientOverlay(shadowColors),
                style = TextStyle(
                    color = Color.Transparent,
                    shadow = Shadow(
                        color = if (shadowColors.size == 1) shadowColors.first() else Color.Black, Offset(4f, 4f), blurRadius = shadowSize
                    ),
                    textAlign = textAlign,
                    fontFamily = font?.let { FontFamily(it) } ?: FontFamily.Default,
                    fontSize = size.sp,
                )
            )
        }


        /** Stroke */
        if (strokeColors.isNotEmpty()) {
            Text(
                text = text,
                style = if (strokeColors.size == 1) {
                    TextStyle(
                        color = strokeColors.first(),
                        drawStyle = Stroke(
                            miter = 10f,
                            width = strokeWidth,
                            join = StrokeJoin.Round
                        ),
                        textAlign = textAlign,
                        fontFamily = font?.let { FontFamily(it) } ?: FontFamily.Default,
                        fontSize = size.sp,
                    )
                } else {
                    TextStyle(
                        brush = Brush.linearGradient(colors = strokeColors),
                        drawStyle = Stroke(
                            miter = 10f,
                            width = strokeWidth,
                            join = StrokeJoin.Round
                        ),
                        textAlign = textAlign,
                        fontFamily = font?.let { FontFamily(it) } ?: FontFamily.Default,
                        fontSize = size.sp,
                    )
                }
            )
        }

        /** Filling */
        if (fillingColors.isNotEmpty()) {
            Text(
                text = text,
                style = if (fillingColors.size <= 1) {
                    TextStyle(
                        color = fillingColors.first(),
                        textAlign = textAlign,
                        fontFamily = font?.let { FontFamily(it) } ?: FontFamily.Default,
                        fontSize = size.sp,
                    )
                } else {
                    TextStyle(
                        brush = Brush.linearGradient(colors = fillingColors),
                        textAlign = textAlign,
                        fontFamily = font?.let { FontFamily(it) } ?: FontFamily.Default,
                        fontSize = size.sp,
                    )
                }
            )
        }
    }
}

@Composable
fun ChatAnnotatedText(
    modifier: Modifier = Modifier,
    text: AnnotatedString,
    size: Float,
    font: Font? = null,
    textAlign: TextAlign = TextAlign.Start,
    hasStroke: Boolean,
    hasShadow: Boolean
) {
    val txtMdfr = modifier.focusable(false)
        .focusProperties { canFocus = false }
        .clickable(false) {}

    val styleStroke = TextStyle(
        color = Color.Black,
        drawStyle = Stroke(
            width = 1f,
        ),
        textAlign = textAlign,
        fontFamily = font?.let { FontFamily(it) },
        fontSize = size.sp,
    )

    val styleFill = TextStyle(
        textAlign = textAlign,
        fontFamily = font?.let { FontFamily(it) },
        fontSize = size.sp,
        shadow = if (hasShadow) Shadow(
            color = Color.Black, Offset(4f, 4f), blurRadius = 1.5f
        ) else null
    )

    Box(modifier = modifier) {
        if (hasStroke) {
            Text(
                modifier = txtMdfr,
                text = text,
                style = styleStroke
            )
        }

        Text(
            modifier = txtMdfr,
            text = text,
            style = styleFill
        )
    }
}

@Composable
fun FlexibleFancyAnnotatedText(
    modifier: Modifier = Modifier,
    text: AnnotatedString,
    size: Float,
    font: Font? = null,
    textAlign: TextAlign = TextAlign.Start,
    fillingColors: List<Color> = listOf(MaterialTheme.colorScheme.primary),
    strokeColors: List<Color> = listOf(),
    strokeWidth: Float = 2f,
    shadowColors: List<Color> = listOf(),
    shadowOffset: Pair<Int, Int> = Pair(2, 2),
    shadowSize: Float = 6f,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val txtMdfr = Modifier.focusable(false)
        .focusProperties { canFocus = false }
        .clickable(false) {}

    Box(modifier = modifier) {
        /** Shadow */
        if (shadowColors.isNotEmpty()) {
            Text(
                text = text,
                modifier = if (shadowColors.size == 1) txtMdfr else txtMdfr.gradientOverlay(shadowColors),
                style = TextStyle(
                    color = Color.Transparent,
                    shadow = Shadow(
                        color = if (shadowColors.size == 1) shadowColors.first() else Color.Black, Offset(4f, 4f), blurRadius = shadowSize
                    ),
                    textAlign = textAlign,
                    fontFamily = font?.let { FontFamily(it) },
                    fontSize = size.sp,
                )
            )
        }


        /** Stroke */
        if (strokeColors.isNotEmpty()) {
            Text(
                modifier = txtMdfr,
                text = text.toString(),
                style = if (strokeColors.size == 1) {
                    TextStyle(
                        color = strokeColors.first(),
                        drawStyle = Stroke(
                            miter = 10f,
                            width = strokeWidth,
                            join = StrokeJoin.Round
                        ),
                        textAlign = textAlign,
                        fontFamily = font?.let { FontFamily(it) },
                        fontSize = size.sp,
                    )
                } else {
                    TextStyle(
                        brush = Brush.linearGradient(colors = strokeColors),
                        drawStyle = Stroke(
                            miter = 10f,
                            width = strokeWidth,
                            join = StrokeJoin.Round
                        ),
                        textAlign = textAlign,
                        fontFamily = font?.let { FontFamily(it) },
                        fontSize = size.sp,
                    )
                }
            )
        }

        /** Filling */
        if (fillingColors.isNotEmpty()) {
            Text(
                modifier = txtMdfr,
                text = text,
                style = if (fillingColors.size <= 1) {
                    TextStyle(
                        color = fillingColors.first(),
                        textAlign = textAlign,
                        fontFamily = font?.let { FontFamily(it) },
                        fontSize = size.sp,
                    )
                } else {
                    TextStyle(
                        brush = Brush.linearGradient(colors = fillingColors),
                        textAlign = textAlign,
                        fontFamily = font?.let { FontFamily(it) },
                        fontSize = size.sp,
                    )
                }
            )
        }
    }
}

/** Creates a text with: Solid Stroke + Gradient Filling */
@Composable
fun FancyText2(
    modifier: Modifier = Modifier,
    string: String,
    solid: Color,
    size: Float,
    font: Font? = null,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = string,
            style = TextStyle(
                brush = Brush.linearGradient(
                    colors = Paletting.SP_GRADIENT
                ),
                textAlign = TextAlign.Center,
                fontFamily = font?.let { FontFamily(it) } ?: FontFamily.Default,
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
                fontFamily = font?.let { FontFamily(it) } ?: FontFamily.Default,
                fontSize = size.sp,
            )
        )
    }
}

/** Creates a multi-choice dialog which is populated by the given list */
@Composable
fun MultiChoiceDialog(
    title: String = "",
    items: Map<String, String>,
    selectedItem: Map.Entry<String, String>,
    onItemClick: (Map.Entry<String, String>) -> Unit,
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
                                fontFamily = FontFamily(Font(Res.font.Directive4_Regular)),
                                fontSize = (15.6).sp
                            )
                        )
                        Text(
                            text = title,
                            style = TextStyle(
                                color = Color.DarkGray,
                                fontFamily = FontFamily(Font(Res.font.Directive4_Regular)),
                                fontSize = 15.sp,
                            )
                        )
                    }
                }

                Column(modifier = Modifier.padding(all = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    items.entries.forEach { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true, color = Paletting.SP_ORANGE)
                            ) {
                                onItemClick(item)
                                onDismiss()
                            }
                        ) {
                            RadioButton(
                                selected = item == selectedItem,
                                onClick = {
                                    onItemClick(item)
                                    onDismiss()
                                }
                            )
                            Text(
                                text = item.key, modifier = Modifier
                                    .fillMaxWidth(0.75f)
                                    .padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Adds a gradient overlay on the composable (Syncplay gradient by default) */
fun Modifier.gradientOverlay(colors: List<Color> = Paletting.SP_GRADIENT): Modifier {
    return this
        .graphicsLayer(alpha = 0.99f)
        .drawWithCache {
            onDrawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.linearGradient(
                        colors = colors
                    ), blendMode = BlendMode.SrcAtop
                )
            }
        }
}

/** Draws a solid fill that overlays the non-transparent parts of the composable. */
fun Modifier.solidOverlay(color: Color): Modifier {
    return this
        .graphicsLayer(alpha = 0.99f)
        .drawWithCache {
            onDrawWithContent {
                drawContent()
                drawRect(
                    color = color,
                    blendMode = BlendMode.SrcAtop
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
                border = BorderStroke(width = strokeWidth.dp, brush = Brush.linearGradient(Paletting.SP_GRADIENT))
            ) {
                content()
            }
        }
    }
}

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

//TODO DONT DO THIS TO PRODUCE A MSG PALETTE
@Composable
fun ComposedMessagePalette(): MessagePalette {
    val colorTimestamp = DataStoreKeys.PREF_INROOM_COLOR_TIMESTAMP.valueAsState(Paletting.MSG_TIMESTAMP.toArgb())
    val colorSelftag = DataStoreKeys.PREF_INROOM_COLOR_SELFTAG.valueAsState(Paletting.MSG_SELF_TAG.toArgb())
    val colorFriendtag = DataStoreKeys.PREF_INROOM_COLOR_FRIENDTAG.valueAsState(Paletting.MSG_FRIEND_TAG.toArgb())
    val colorSystem = DataStoreKeys.PREF_INROOM_COLOR_SYSTEMMSG.valueAsState(Paletting.MSG_SYSTEM.toArgb())
    val colorUserchat = DataStoreKeys.PREF_INROOM_COLOR_USERMSG.valueAsState(Paletting.MSG_CHAT.toArgb())
    val colorError = DataStoreKeys.PREF_INROOM_COLOR_ERRORMSG.valueAsState(Paletting.MSG_ERROR.toArgb())
    val msgIncludeTimestamp = DataStoreKeys.PREF_INROOM_MSG_ACTIVATE_STAMP.valueAsState(true)

    return MessagePalette(
        timestampColor = Color(colorTimestamp.value),
        selftagColor = Color(colorSelftag.value),
        friendtagColor = Color(colorFriendtag.value),
        systemmsgColor = Color(colorSystem.value),
        usermsgColor = Color(colorUserchat.value),
        errormsgColor = Color(colorError.value),
        includeTimestamp = msgIncludeTimestamp.value
    )
}


