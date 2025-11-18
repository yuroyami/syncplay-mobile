package com.yuroyami.syncplay.managers.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.core.ScrollArea
import com.composables.core.Thumb
import com.composables.core.ThumbVisibility
import com.composables.core.VerticalScrollbar
import com.composables.core.rememberScrollAreaState
import com.yuroyami.syncplay.ui.components.FlexibleText
import com.yuroyami.syncplay.ui.components.gradientOverlay
import com.yuroyami.syncplay.ui.components.sairaFont
import com.yuroyami.syncplay.ui.screens.adam.LocalSettingStyling
import com.yuroyami.syncplay.ui.screens.home.SettingGridState
import com.yuroyami.syncplay.ui.screens.theme.Theming
import com.yuroyami.syncplay.ui.screens.theme.Theming.flexibleGradient
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.milliseconds

/** Object class that will wrap everything related to settings (including composables for UI) */
object SettingsUI {

    enum class Layout {
        SETTINGS_ROOM, SETTINGS_GLOBAL
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    fun SettingsGrid(
        modifier: Modifier = Modifier,
        settings: List<SettingCategory>,
        state: MutableState<SettingGridState>,
        layout: Layout,
        titleSize: Float = 12f,
        cardSize: Float = 64f,
        gridColumns: Int = 3,
    ) {
        /** Variable to store which category is accessed (null means we're at the root level, no category selected */
        var enteredCategory by remember { mutableStateOf<SettingCategory?>(null) }

        /** We have to wrap our settings grid with AnimatedVisibility in order to do animations */
        AnimatedVisibility(
            modifier = modifier, visible = state.value == SettingGridState.NAVIGATING_CATEGORIES, exit = fadeOut(), enter = fadeIn()
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

                        settings.forEach { category ->
                            SettingCategoryCard1(
                                0,
                                categ = category,
                                titleSize = titleSize,
                                cardSize = cardSize,
                                onClick = {
                                    state.value = SettingGridState.INSIDE_CATEGORY
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
                        settings.forEachIndexed { i, category ->
                            item {
                                SettingCategoryCard2(
                                    i,
                                    categ = category,
                                    onClick = {
                                        state.value = SettingGridState.INSIDE_CATEGORY
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
                modifier = modifier, visible = state.value == SettingGridState.INSIDE_CATEGORY, exit = fadeOut(), enter = fadeIn()
            ) {
                enteredCategory?.let { accessedCategory ->
                    SettingScreen(
                        settingCategory = accessedCategory
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
                    indication = ripple(color = Theming.SP_ORANGE)

                ) {
                    onClick()
                },
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Theming.SP_GRADIENT[index % 3].copy(0.1f)
                ),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = categ.icon,
                        contentDescription = "",
                        modifier = modifier.size((cardSize * 0.81f).dp).align(Alignment.Center)
                            .gradientOverlay(flexibleGradient)
                    )


                }
            }

            FlexibleText(
                text = stringResource(categ.title),
                fillingColors = flexibleGradient,
                strokeColors = listOf(MaterialTheme.colorScheme.outline),
                size = titleSize,
                font = sairaFont
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
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape = RoundedCornerShape(8.dp))
                .background(Theming.SP_GRADIENT[index % 3].copy(0.1f))
                .border(width = Dp.Hairline, color = MaterialTheme.colorScheme.outline, shape = RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = Theming.SP_ORANGE),
                    onClick = onClick
                )
                .padding(8.dp),
        ) {
            Box(modifier = Modifier) {
                Icon(
                    imageVector = categ.icon,
                    contentDescription = "",
                    modifier = modifier.size(32.dp)
                        .align(Alignment.Center)
                        .gradientOverlay(flexibleGradient)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            FlexibleText(
                text = stringResource(categ.title),
                fillingColors = flexibleGradient,
                strokeColors = listOf(MaterialTheme.colorScheme.outline),
                size = 17f,
                font = sairaFont
            )
        }


    }

    @Composable
    fun SettingScreen(modifier: Modifier = Modifier, settingCategory: SettingCategory) {
        val vss = rememberScrollState()
        val scrollAreaState = rememberScrollAreaState(vss)
        var columnHeight by remember { mutableStateOf(0.dp) }
        val density = LocalDensity.current

        ScrollArea(
            state = scrollAreaState,
            modifier = modifier.fillMaxWidth()
        ) {
            Column(modifier = modifier.fillMaxWidth().verticalScroll(vss)
                .onGloballyPositioned { coordinates ->
                    with(density) {
                        columnHeight = coordinates.size.height.toDp()
                    }
            }) {
                settingCategory.settings.forEach {
                    it.Render()

                    HorizontalDivider(
                        thickness = Dp.Hairline,
                        modifier = Modifier.padding(horizontal = 8.dp),
                        color = MaterialTheme.colorScheme.inverseSurface
                    )                }
            }

            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).width(4.dp).height(columnHeight)
            ) {
                Thumb(
                    modifier = Modifier.background(Color.Gray),
                    thumbVisibility = ThumbVisibility.HideWhileIdle(enter = fadeIn(), exit = fadeOut(), hideDelay = 150.milliseconds)
                )
            }
        }
    }
}