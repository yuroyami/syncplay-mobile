package app.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.R

/** Contains a bunch of composable functions that are frequently reused */
object ComposeUtils {

    /** Creates a fancy Syncplay-themed icon */
    @Composable
    fun FancyIcon(modifier: Modifier = Modifier, icon: ImageVector?, size: Int) {
        Box(modifier = modifier) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = "",
                    modifier = modifier
                        .size(size.dp)
                        .align(Alignment.Center)
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

    /** Creates a multi-choice dialog which is populated by the given list */
    @OptIn(ExperimentalTextApi::class)
    @Composable
    fun MultiChoiceDialog(
        modifier: Modifier = Modifier,
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
                            Text(text = item, modifier = Modifier
                                .fillMaxWidth(0.75f)
                                .padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }
}