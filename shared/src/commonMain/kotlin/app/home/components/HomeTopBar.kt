package app.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.home.HomeViewmodel
import app.preferences.settings.SETTINGS_GLOBAL
import app.preferences.settings.SettingsUI
import app.theme.ThemeMenu
import app.theme.Theming
import app.theme.Theming.flexibleGradient
import app.uicomponents.FlexibleIcon
import app.uicomponents.SynkplayLogo
import app.uicomponents.SyncplayishText
import app.utils.appName
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.vectorResource
import syncplaymobile.shared.generated.resources.Directive4_Regular
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.synkplay_logo
import app.uicomponents.GlassMaterial
import app.uicomponents.glassSurface
import app.uicomponents.GlassEdge
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned

enum class SettingGridState {
    COLLAPSED, NAVIGATING_CATEGORIES, INSIDE_CATEGORY
}

/**
 * @param onRestingHeight reports the height of the bar's ALWAYS-PRESENT row (title + actions,
 *   plus the status-bar inset). The settings grid expands inside the same bar, and the page below
 *   must not be re-padded every time it opens, so the content spacer tracks this instead of the
 *   Scaffold's live top padding.
 */
@Composable
fun HomeTopBar(viewmodel: HomeViewmodel, onRestingHeight: (Dp) -> Unit = {}) {
    val aboutpopupState = remember { mutableStateOf(false) }

    PopupAPropos.AProposPopup(aboutpopupState, viewmodel)

    // Square top and sides: this is a bar welded to the top of the screen, not a floating card,
    // so only the bottom corners round and only the bottom edge draws a rim.
    val topBarShape = RoundedCornerShape(
        topEnd = 0.dp, topStart = 0.dp, bottomEnd = 20.dp, bottomStart = 20.dp
    )
    val density = LocalDensity.current
    Card(
        modifier = Modifier.fillMaxWidth()
            .glassSurface(shape = topBarShape, material = GlassMaterial.Thin, edge = GlassEdge.BottomOnly),
        shape = topBarShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        val settingState = remember { mutableStateOf(SettingGridState.COLLAPSED) }

        Column(
            horizontalAlignment = CenterHorizontally,
            modifier = Modifier
                .animateContentSize()
                // The expanded settings sit OVER the page (the page keeps its resting spacing),
                // and the bar's glass is translucent, so without this wash the grid and the form
                // print through each other. Near-opaque only while open; glass at rest.
                .background(
                    if (settingState.value != SettingGridState.COLLAPSED)
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                    else Color.Transparent
                )
        ) {
            ListItem(
                modifier = Modifier.fillMaxWidth()
                    .onGloballyPositioned { onRestingHeight(with(density) { it.size.height.toDp() }) }
                    .padding(top = (TopAppBarDefaults.windowInsets.asPaddingValues().calculateTopPadding())),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = {
                    Row(
                        verticalAlignment = CenterVertically,
                        modifier = Modifier.clip(CircleShape).clickable(
                            enabled = true,
                            interactionSource = null,
                            indication = ripple(bounded = false)
                        ) { aboutpopupState.value = true }.padding(16.dp)
                    ) {
                        SynkplayLogo(modifier = Modifier.height(32.dp).aspectRatio(1f))

                        Spacer(modifier = Modifier.width(12.dp))

                        SyncplayishText(
                            modifier = Modifier.padding(bottom = 4.dp),
                            string = appName,
                            size = 24f
                        )
                    }
                },
                trailingContent = {
                    Row(verticalAlignment = CenterVertically) {
                        var themePopupState by remember { mutableStateOf(false) }

                        FlexibleIcon(
                            icon = Icons.Outlined.Palette,
                            size = 38,
                            tintColors = listOf(MaterialTheme.colorScheme.primary),
                            onClick = {
                                themePopupState = true
                            }
                        )
                        ThemeMenu(themePopupState, onDismiss = { themePopupState = false })

                        FlexibleIcon(
                            icon = when (settingState.value) {
                                SettingGridState.COLLAPSED -> Icons.Filled.Settings
                                SettingGridState.NAVIGATING_CATEGORIES -> Icons.Filled.Close
                                SettingGridState.INSIDE_CATEGORY -> Icons.AutoMirrored.Filled.Redo
                            },
                            size = 38,
                            tintColors = listOf(MaterialTheme.colorScheme.primary),
                            onClick = {
                                settingState.value = when (settingState.value) {
                                    SettingGridState.COLLAPSED -> SettingGridState.NAVIGATING_CATEGORIES
                                    SettingGridState.NAVIGATING_CATEGORIES -> SettingGridState.COLLAPSED
                                    SettingGridState.INSIDE_CATEGORY -> SettingGridState.NAVIGATING_CATEGORIES
                                }
                            }
                        )
                    }
                }
            )

            AnimatedVisibility(
                modifier = Modifier.fillMaxWidth(),
                visible = settingState.value != SettingGridState.COLLAPSED,
                enter = scaleIn(),
                exit = scaleOut()
            ) {
                SettingsUI.SettingsGrid(
                    modifier = Modifier.fillMaxWidth(),
                    settings = SETTINGS_GLOBAL,
                    state = settingState,
                    layout = SettingsUI.Layout.SETTINGS_GLOBAL,
                )
            }
        }
    }
}
