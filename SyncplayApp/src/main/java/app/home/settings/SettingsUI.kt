package app.home.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.R
import app.home.Paletting
import com.google.accompanist.flowlayout.FlowMainAxisAlignment
import com.google.accompanist.flowlayout.FlowRow

/** Object class that will wrap everything related to settings (including composables for UI) */
object SettingsUI {

    @Composable
    fun SettingsGrid(
        modifier: Modifier = Modifier,
        settingcategories: List<SettingCategory>,
        state: MutableState<Int>,
        onCardClicked: (Int) -> Unit = {},
    ) {
        /** This integer mutable state controls which setting screen is to appear based on the card
         * that is clicked. It has to be remembered. */
        val clickedCardIndex = remember { mutableStateOf(settingcategories[0]) }


        /** We have to wrap our settings grid with AnimatedVisibility in order to do animations */
        AnimatedVisibility(modifier = modifier, visible = state.value == 1, exit = fadeOut(), enter = fadeIn()) {

            /** FlowRow basically arranges cards horizontally, then creates another row when space doesn't suffice */
            FlowRow(modifier = modifier, mainAxisAlignment = FlowMainAxisAlignment.SpaceEvenly, mainAxisSpacing = 12.dp) {

                /** Iterating through our cards and invoking them one by one */
                for ((index, category) in settingcategories.withIndex()) {
                    SettingCategoryCard(
                        categ = category,
                        onClick = {
                            //clickedCardBoolean.value = true
                            clickedCardIndex.value = settingcategories[index]
                            onCardClicked(index)
                        }
                    )
                }
            }
        }

        AnimatedVisibility(visible = state.value == 2, exit = fadeOut(), enter = fadeIn()) {
            SettingScreen(modifier = modifier, clickedCardIndex.value)
        }
    }

    @OptIn(ExperimentalTextApi::class)
    @Composable
    fun SettingCategoryCard(
        modifier: Modifier = Modifier,
        categ: SettingCategory,
        onClick: () -> Unit,
    ) {
        Column(modifier = modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Card(
                modifier = Modifier
                    .width(64.dp)
                    .aspectRatio(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = rememberRipple(color = Paletting.SP_ORANGE)

                    ) {
                        onClick()
                    },
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.DarkGray),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 10.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = categ.icon,
                        contentDescription = "",
                        modifier = modifier
                            .size(52.dp)
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
                        imageVector = categ.icon,
                        contentDescription = "",
                        modifier = modifier
                            .size(48.dp)
                            .align(Alignment.Center),
                        tint = Color.Gray
                    )

                }
            }


            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = categ.title,
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
                        fontFamily = FontFamily(Font(R.font.bahidj_fedra_arabic_bold)),
                        fontSize = 12.sp,
                    )
                )
                Text(
                    text = categ.title,
                    style = TextStyle(
                        color = Color.DarkGray,
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily(Font(R.font.bahidj_fedra_arabic_bold)),
                        fontSize = 12.sp,
                    )
                )
            }
        }
    }

    @Composable
    fun SettingScreen(modifier: Modifier = Modifier, settingcategory: SettingCategory) {
        Column(modifier = modifier.fillMaxWidth()) {
            for ((index, setting) in settingcategory.settingList.withIndex()) {
                /** Creating the setting composable */
                setting.SettingSingleton()

                /** Creating dividers between (and only between) each setting and another */
                if (index != settingcategory.settingList.lastIndex) {
                    Divider(Modifier.padding(horizontal = 32.dp))
                }
            }
        }
    }
}