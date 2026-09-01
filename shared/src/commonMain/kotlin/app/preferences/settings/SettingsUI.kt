package app.preferences.settings

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
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.LocalSettingStyling
import app.home.components.SettingGridState
import app.theme.Theming
import app.uicomponents.tvFocusable
import com.composeunstyled.Thumb
import com.composeunstyled.ThumbVisibility
import com.composeunstyled.UnstyledVerticalScrollbar
import com.composeunstyled.rememberScrollbarState
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.milliseconds
import app.uicomponents.GlassMaterial
import androidx.compose.ui.graphics.Color
import app.uicomponents.glassSurface

/** Composables that render setting categories: a navigable grid plus the per-category detail screen. */
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
        /** Currently opened category; null means the root grid is shown. */
        var enteredCategory by remember { mutableStateOf<SettingCategory?>(null) }

        AnimatedVisibility(
            modifier = modifier, visible = state.value == SettingGridState.NAVIGATING_CATEGORIES, exit = fadeOut(), enter = fadeIn()
        ) {

            when (layout) {
                Layout.SETTINGS_ROOM -> {
                    /** FlowRow wraps cards onto extra rows; the scrollable Column keeps overflow categories
                     *  reachable when the grid has more rows than fit (e.g. room settings + engine-injected category). */
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    ) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.SpaceAround,
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            maxItemsInEachRow = gridColumns
                        ) {
                            settings.forEach { category ->
                                SettingCategoryCard1(
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
                }

                Layout.SETTINGS_GLOBAL -> {
                    LazyVerticalGrid(
                        modifier = Modifier,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        settings.forEach { category ->
                            item {
                                SettingCategoryCard2(
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
        modifier: Modifier = Modifier,
        categ: SettingCategory,
        titleSize: Float,
        cardSize: Float,
        onClick: () -> Unit,
    ) {
        Column(
            modifier = modifier.width(cardSize.dp), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.width(cardSize.dp).aspectRatio(1f)
                    .glassSurface(shape = MaterialTheme.shapes.small, material = GlassMaterial.UltraThin)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple()
                    ) {
                        onClick()
                    }.tvFocusable(addFocusable = false),
                shape = MaterialTheme.shapes.small,
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = categ.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size((cardSize * 0.5f).dp).align(Alignment.Center)
                    )
                }
            }

            Text(
                text = stringResource(categ.title),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = titleSize.sp),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }

    @Composable
    fun SettingCategoryCard2(
        modifier: Modifier = Modifier,
        categ: SettingCategory,
        onClick: () -> Unit,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .fillMaxWidth()
                .glassSurface(shape = MaterialTheme.shapes.small, material = GlassMaterial.UltraThin)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                    onClick = onClick
                )
                .tvFocusable(addFocusable = false)
                .padding(Theming.SpaceMD),
        ) {
            Icon(
                imageVector = categ.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(Theming.SpaceMD))

            Text(
                text = stringResource(categ.title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2
            )
        }
    }

    @Composable
    fun SettingScreen(modifier: Modifier = Modifier, settingCategory: SettingCategory) {
        val vss = rememberScrollState()
        val scrollbarState = rememberScrollbarState(vss)
        var columnHeight by remember { mutableStateOf(0.dp) }
        val density = LocalDensity.current

        Box(modifier = modifier.fillMaxWidth()) {
            Column(modifier = modifier.fillMaxWidth().verticalScroll(vss)
                .onGloballyPositioned { coordinates ->
                    with(density) {
                        columnHeight = coordinates.size.height.toDp()
                    }
            }) {
                settingCategory.settings.forEachIndexed { index, setting ->
                    setting.Render()

                    if (index != settingCategory.settings.lastIndex) {
                        HorizontalDivider(
                            thickness = Dp.Hairline,
                            modifier = Modifier.padding(horizontal = Theming.SpaceLG),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }

            UnstyledVerticalScrollbar(
                scrollbarState = scrollbarState,
                modifier = Modifier.align(Alignment.CenterEnd).width(4.dp).height(columnHeight)
            ) {
                Thumb(
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)),
                    thumbVisibility = ThumbVisibility.HideWhileIdle(enter = fadeIn(), exit = fadeOut(), hideDelay = 150.milliseconds)
                )
            }
        }
    }
}
