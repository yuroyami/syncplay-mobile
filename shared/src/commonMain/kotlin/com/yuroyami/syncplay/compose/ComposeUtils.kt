package com.yuroyami.syncplay.compose

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
import com.yuroyami.syncplay.MR
import com.yuroyami.syncplay.ui.Paletting
import com.yuroyami.syncplay.ui.Paletting.backgroundGradient
import dev.icerock.moko.resources.compose.fontFamilyResource

/** Contains a bunch of composable functions that are frequently reused */
object ComposeUtils {

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
                    fontFamily = fontFamilyResource(MR.fonts.Directive4.regular),
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
                    fontFamily = fontFamilyResource(MR.fonts.Directive4.regular),
                    fontSize = size.sp,
                )
            )
        }
    }

    /** Creates a fancy icon with solid tint and gradient trailing shadow */
    @Composable
    fun FancyIcon(
        modifier: Modifier = Modifier,
        icon: ImageVector?,
        color: Color = Color.DarkGray,
        size: Int,
        onClick: () -> Unit = {},
    ) {
        Box(modifier = modifier) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = "",
                    modifier = modifier
                        .size(size.dp)
                        .align(Alignment.Center)
                        .gradientOverlay()
                )
                Icon(
                    imageVector = icon,
                    contentDescription = "",
                    modifier = modifier
                        .size((size - 2).dp)
                        .align(Alignment.Center),
                    tint = color
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
        shadowOffset: Pair<Int, Int> = Pair(2, 2),
        alpha: Float = 0.9f,
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
                modifier = Modifier
                    .padding(4.dp)
                    .wrapContentSize()
            ) {
                if (shadowColors.isNotEmpty()) {
                    /** Shadow */
                    val shadowModifier = Modifier
                        .size(size.dp)
                        .offset(x = shadowOffset.first.dp, y = shadowOffset.second.dp)
                    Icon(
                        imageVector = icon,
                        contentDescription = "",
                        modifier = if (shadowColors.size == 1) shadowModifier else shadowModifier.gradientOverlay(shadowColors),
                        tint = if (shadowColors.size == 1) shadowColors.first() else Color.Black
                    )
                }

                /** Foreground */
                val fgModifier = Modifier
                    .size(size.dp)
                    .alpha(0.9f)

                Icon(
                    imageVector = icon,
                    contentDescription = "",
                    modifier = if (tintColors.size <= 1) fgModifier else fgModifier.gradientOverlay(tintColors),
                    tint = if (tintColors.size == 1) tintColors.first() else Color.Transparent
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
    fun FlexibleFancyAnnotatedText(
        modifier: Modifier = Modifier,
        text: AnnotatedString,
        size: Float,
        font: Font,
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
                        fontFamily = FontFamily(font),
                        fontSize = size.sp,
                    )
                )
            }


            /** Stroke */
            if (strokeColors.isNotEmpty()) {
                Text(
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
                            fontFamily = FontFamily(font),
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
                            fontFamily = FontFamily(font),
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
                            fontFamily = FontFamily(font),
                            fontSize = size.sp,
                        )
                    } else {
                        TextStyle(
                            brush = Brush.linearGradient(colors = fillingColors),
                            textAlign = textAlign,
                            fontFamily = FontFamily(font),
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

    /** Creates a text with: Solid + Filling + Gradient Shadow */
    @Composable
    fun FancyText3(
        modifier: Modifier = Modifier,
        string: String,
        solid: Color,
        size: Float,
        font: Font? = null,
    ) {
        Box(modifier = modifier) {
            Text(
                text = string,
                modifier = Modifier.gradientOverlay(),
                style = TextStyle(
                    color = Color.Transparent,
                    shadow = Shadow(color = Color.Black, Offset(4f, 4f), blurRadius = 6f),
                    textAlign = TextAlign.Center,
                    fontFamily = font?.let { FontFamily(it) } ?: FontFamily.Default,
                    fontSize = size.sp,
                )
            )
            Text(
                text = string,
                style = TextStyle(
                    color = solid,
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
                                    fontFamily = fontFamilyResource(MR.fonts.Directive4.regular), fontSize = (15.6).sp
                                )
                            )
                            Text(
                                text = title,
                                style = TextStyle(
                                    color = Color.DarkGray,
                                    fontFamily = fontFamilyResource(MR.fonts.Directive4.regular), fontSize = 15.sp,
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

    /** Adds an OVAL gradient overlay on the composable (Syncplay gradient by default) */
    fun Modifier.radiantOverlay(colors: List<Color> = Paletting.SP_GRADIENT, offset: Offset = Offset.Unspecified): Modifier {
        return this
            .graphicsLayer(alpha = 0.99f)
            .drawWithCache {
                onDrawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.radialGradient(
                            center = offset,
                            colors = colors
                        ),
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
    //TODO post-KMM migration: Activate annotations by leveraging non JVM annotations... if any...
    @Composable
    fun RoomPopup(
        dialogOpen: Boolean,
        cardBackgroundColor: Color = Color.Gray,
        /*TODO: @IntRange(from = 0)*/ cardCornerRadius: Int = 10,
        /*TODO: @FloatRange(0.0, 10.0)*/ strokeWidth: Float = 1.5f,
        /*TODO: @FloatRange(0.0, 1.0)*/ widthPercent: Float = 0f,
        /*TODO: @FloatRange(0.0, 1.0)*/ heightPercent: Float = 0f,
        dismissable: Boolean = true,
        onDismiss: () -> Unit = {},
        content: @Composable BoxScope.() -> Unit,
    ) {
        if (dialogOpen) {
            Dialog(
                onDismissRequest = {
                    onDismiss()
                },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    //decorFitsSystemWindows = false,
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
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(brush = Brush.linearGradient(backgroundGradient()))
                    ) {
                        content()
                    }
                }

            }
        }
    }


    /** Toggle button responsible for switching the day/night (dark/light) mode.
     *
     * @param nightModeState Defines the initial state of the button (you should pass it,
     * like you're telling the composable which mode is enabled initially).
     */

//    @Composable
//    fun NightModeToggle(modifier: Modifier, state: State<Boolean>) {
//        val scope = rememberCoroutineScope()
//
//        /* The lottie composition to play */
//        val composition by rememberLottieComposition(LottieCompositionSpec.Asset("daynight_toggle.json"))
//        val anim = rememberLottieAnimatable() /* The animatable that accompanies the composition */
//        //val clip = remember { mutableStateOf(LottieClipSpec.Progress(0f, 0.49f)) }
//
//        val night2day = LottieClipSpec.Progress(0f, 0.4f)
//        val day2night = LottieClipSpec.Progress(0.52f, 1f)
//
//        var onInit by remember { mutableStateOf(false) }
//
//        LaunchedEffect(state.value) {
//            if (!onInit) {
//                onInit = true
//                anim.snapTo(composition = composition, progress = if (!state.value) 0f else 0.42f)
//            } else {
//                /** Applying the corresponding animation */
//                anim.animate(
//                    composition = composition,
//                    speed = 1.25f,
//                    clipSpec = if (state.value) night2day else day2night
//                )
//            }
//        }
//
//        IconButton(
//            modifier = modifier,
//            onClick = {
//                val newMode = !state.value
//
//                if (newMode) {
//                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
//                } else {
//                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
//                }
//
//                scope.launch {
//                    DataStoreKeys.DATASTORE_MISC_PREFS.writeBoolean(MISC_NIGHTMODE, !state.value)
//                }
//            }) {
//            LottieAnimation(
//                composition = composition,
//                progress = { anim.progress },
//            )
//        }
//    }
}