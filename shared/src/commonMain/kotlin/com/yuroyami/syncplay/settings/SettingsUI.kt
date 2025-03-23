package com.yuroyami.syncplay.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowColumn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yuroyami.syncplay.compose.ComposeUtils.FancyText2
import com.yuroyami.syncplay.compose.ComposeUtils.gradientOverlay
import com.yuroyami.syncplay.compose.getRegularFont
import com.yuroyami.syncplay.settings.SettingsUI.SettingCategoryCard
import com.yuroyami.syncplay.ui.Paletting

/** Object class that will wrap everything related to settings (including composables for UI) */
object SettingsUI {

    enum class SettingsGridLayout {
        SETTINGS_GRID_HORIZONTAL_FLOW, SETTINGS_GRID_VERTICAL_FLOW, SETTINGS_GRID_HORIZONTAL_GRID, SETTINGS_GRID_VERTICAL_GRID
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    fun SettingsGrid(
        modifier: Modifier = Modifier,
        settingcategories: List<SettingCategory>,
        state: MutableState<Int>,
        layoutOrientation: SettingsGridLayout = SettingsGridLayout.SETTINGS_GRID_VERTICAL_GRID,
        titleSize: Float = 12f,
        cardSize: Float = 64f,
        gridRows: Int = 2,
        gridColumns: Int = 3,
        onCardClicked: (Int) -> Unit = {},
    ) {
        /** Variable to store which card is currently clicked */
        val clickedCardIndex = remember { mutableStateOf(settingcategories[0]) }

        /** We have to wrap our settings grid with AnimatedVisibility in order to do animations */
        AnimatedVisibility(
            modifier = modifier, visible = state.value == 1, exit = fadeOut(), enter = fadeIn()
        ) {

            when (layoutOrientation) {
                SettingsGridLayout.SETTINGS_GRID_VERTICAL_FLOW -> {
                    /** FlowColumn basically arranges cards vertically, then creates another column when space doesn't suffice */
                    FlowColumn(
                        modifier = Modifier.fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceEvenly,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        SettingCategoryIterator(
                            clickedCardIndex, settingcategories, titleSize, cardSize, onCardClicked
                        )
                    }
                }

                SettingsGridLayout.SETTINGS_GRID_HORIZONTAL_FLOW -> {
                    /** FlowRow arranges cards horizontally, then creates another row when space doesn't suffice */
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.SpaceAround,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        maxItemsInEachRow = gridColumns
                    ) {
                        SettingCategoryIterator(
                            clickedCardIndex, settingcategories, titleSize, cardSize, onCardClicked
                        )
                    }
                }

                SettingsGridLayout.SETTINGS_GRID_HORIZONTAL_GRID -> {
                    LazyHorizontalGrid(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        rows = GridCells.Fixed(gridRows)
                    ) {
                        iteratorSettingCategoryGrid(
                            clickedCardIndex, settingcategories, titleSize, cardSize, onCardClicked
                        )
                    }
                }

                SettingsGridLayout.SETTINGS_GRID_VERTICAL_GRID -> {
                    LazyVerticalGrid(
                        modifier = Modifier,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {

                        iteratorSettingCategoryGrid(
                            clickedCardIndex, settingcategories, titleSize, cardSize, onCardClicked
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            modifier = modifier, visible = state.value == 2, exit = fadeOut(), enter = fadeIn()
        ) {
            SettingScreen(
                modifier = Modifier.verticalScroll(rememberScrollState()), clickedCardIndex.value
            )
        }
    }

    @Composable
    fun SettingCategoryCard(
        index: Int,
        modifier: Modifier = Modifier,
        categ: SettingCategory,
        titleSize: Float,
        cardSize: Float,
        onClick: () -> Unit,
    ) {
        Column(
            modifier = modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.width(cardSize.dp).aspectRatio(1f).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = Paletting.SP_ORANGE)

                ) {
                    onClick()
                },
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Paletting.SP_GRADIENT[index % 3].copy(0.1f)
                ),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = categ.icon,
                        contentDescription = "",
                        modifier = modifier.size((cardSize * 0.81f).dp).align(Alignment.Center)
                            .gradientOverlay()
                    )


                }
            }

            FancyText2(
                string = categ.title,
                solid = Color.Transparent,
                size = titleSize,
                font = getRegularFont()
            )
        }
    }

    @Composable
    fun LazyGridItemScope.SettingCategoryCard(
        index: Int,
        modifier: Modifier = Modifier,
        categ: SettingCategory,
        titleSize: Float,
        cardSize: Float,
        onClick: () -> Unit,
    ) {

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clip(shape = RoundedCornerShape(8.dp))
                .background(Paletting.SP_GRADIENT[index % 3].copy(0.1f)).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = Paletting.SP_ORANGE)

                ) {
                    onClick()
                }.padding(8.dp),
        ) {
            Box(modifier = Modifier) {
                Icon(
                    imageVector = categ.icon,
                    contentDescription = "",
                    modifier = modifier.size(32.dp).align(Alignment.Center).gradientOverlay()
                )


            }
            Spacer(modifier = Modifier.width(8.dp))
            FancyText2(
                modifier = Modifier.basicMarquee(),
                string = categ.title,
                solid = Color.Transparent,
                size = 18f,
                font = getRegularFont()
            )
        }


    }

    @Composable
    fun SettingScreen(modifier: Modifier = Modifier, settingcategory: SettingCategory) {
        Column(modifier = modifier.fillMaxWidth()) {
            for ((index, setting) in settingcategory.settingList.withIndex()) {
                /** Creating the setting composable */
                setting.SettingComposable(Modifier)

                /** Creating dividers between (and only between) each setting and another */
                if (index != settingcategory.settingList.lastIndex) {
                    HorizontalDivider(Modifier.padding(horizontal = 32.dp))
                }
            }
        }
    }

    @Composable
    private fun SettingCategoryIterator(
        clickedCardIndex: MutableState<SettingCategory>,
        settingcategories: List<SettingCategory>,
        titleSize: Float = 13f,
        cardSize: Float = 64f,
        onCardClicked: (Int) -> Unit = {},
    ) {
        /** Iterating through our cards and invoking them one by one */
        for ((index, category) in settingcategories.withIndex()) {
            SettingCategoryCard(
                index,
                categ = category,
                titleSize = titleSize,
                cardSize = cardSize,
                onClick = {
                    clickedCardIndex.value = settingcategories[index]
                    onCardClicked(index)
                })
        }
    }

    private fun LazyGridScope.iteratorSettingCategoryGrid(
        clickedCardIndex: MutableState<SettingCategory>,
        settingcategories: List<SettingCategory>,
        titleSize: Float = 13f,
        cardSize: Float = 64f,
        onCardClicked: (Int) -> Unit = {},
    ) {

        itemsIndexed(settingcategories) { i, sg ->
            SettingCategoryCard(
                i,
                categ = sg,
                titleSize = titleSize,
                cardSize = cardSize,
                onClick = {
                    clickedCardIndex.value = settingcategories[i]
                    onCardClicked(i)
                })
        }
    }
}