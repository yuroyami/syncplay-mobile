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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yuroyami.syncplay.ui.utils.FancyText2
import com.yuroyami.syncplay.ui.utils.gradientOverlay
import com.yuroyami.syncplay.ui.utils.getRegularFont
import com.yuroyami.syncplay.screens.adam.LocalSettingStyling
import com.yuroyami.syncplay.ui.theme.Paletting
import org.jetbrains.compose.resources.stringResource

/** Object class that will wrap everything related to settings (including composables for UI) */
object SettingsUI {

    enum class Layout {
        SETTINGS_ROOM, SETTINGS_GLOBAL
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    fun SettingsGrid(
        modifier: Modifier = Modifier,
        settings: SettingCollection,
        state: MutableState<Int>,
        layout: Layout,
        titleSize: Float = 12f,
        cardSize: Float = 64f,
        gridColumns: Int = 3,
        onEnteredSomeCategory: () -> Unit = {},
    ) {
        /** Variable to store which category is accessed (null means we're at the root level, no category selected */
        var enteredCategory by remember { mutableStateOf<SettingCategory?>(null) }

        /** We have to wrap our settings grid with AnimatedVisibility in order to do animations */
        AnimatedVisibility(
            modifier = modifier, visible = state.value == 1, exit = fadeOut(), enter = fadeIn()
        ) {

            when (layout) {
                Layout.SETTINGS_ROOM -> {
                    /** FlowRow arranges cards horizontally, then creates another row when space doesn't suffice */
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.SpaceAround,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        maxItemsInEachRow = gridColumns
                    ) {

                        settings.keys.forEach { category ->
                            SettingCategoryCard1(
                                0,
                                categ = category,
                                titleSize = titleSize,
                                cardSize = cardSize,
                                onClick = {
                                    onEnteredSomeCategory()
                                    enteredCategory = category
                                }
                            )
                        }
                    }
                }

                Layout.SETTINGS_GLOBAL -> {
                    LazyVerticalGrid(
                        modifier = Modifier,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        settings.keys.forEachIndexed { i, category ->
                            item {
                                SettingCategoryCard2(
                                    i,
                                    categ = category,
                                    onClick = {
                                        onEnteredSomeCategory()
                                        enteredCategory = category
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        CompositionLocalProvider(
            LocalSettingStyling provides when (layout) {
                Layout.SETTINGS_ROOM -> settingROOMstyle
                Layout.SETTINGS_GLOBAL -> settingGLOBALstyle
            }
        ) {
            AnimatedVisibility(
                modifier = modifier, visible = state.value == 2, exit = fadeOut(), enter = fadeIn()
            ) {
                val vss = rememberScrollState()

                enteredCategory?.let { accessedCategory ->
                    SettingScreen(
                        modifier = Modifier.verticalScroll(vss),
                        settingList = settings[accessedCategory]!!
                    )
                }
            }
        }
    }

    @Composable
    fun SettingCategoryCard1(
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
                string = stringResource(categ.title),
                solid = Color.Transparent,
                size = titleSize,
                font = getRegularFont()
            )
        }
    }

    @Composable
    fun SettingCategoryCard2(
        index: Int,
        modifier: Modifier = Modifier,
        categ: SettingCategory,
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
                string = stringResource(categ.title),
                solid = Color.Transparent,
                size = 18f,
                font = getRegularFont()
            )
        }


    }

    @Composable
    fun SettingScreen(modifier: Modifier = Modifier, settingList: SettingSet) {
        Column(modifier = modifier.fillMaxWidth()) {
            settingList.forEachIndexed { index, setting ->
                /** Creating the setting composable */
                setting.SettingComposable(Modifier)

                /** Creating dividers between (and only between) each setting and another */
                if (index != settingList.lastIndex) {
                    HorizontalDivider(Modifier.padding(horizontal = 32.dp))
                }
            }
        }
    }
}