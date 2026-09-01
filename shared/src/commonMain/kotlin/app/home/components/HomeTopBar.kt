package app.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import app.LocalGlobalViewmodel
import app.Screen
import app.home.HomeViewmodel
import app.theme.ThemeMenu
import app.uicomponents.FlexibleIcon
import app.uicomponents.GlassEdge
import app.uicomponents.GlassMaterial
import app.uicomponents.SynkplayLogo
import app.uicomponents.SyncplayishText
import app.uicomponents.glassSurface
import app.utils.appName

/**
 * @param onRestingHeight reports the height of the bar (title + actions, plus the status-bar
 *   inset) so the page below can space itself under it.
 */
@Composable
fun HomeTopBar(viewmodel: HomeViewmodel, onRestingHeight: (Dp) -> Unit = {}) {
    val aboutpopupState = remember { mutableStateOf(false) }
    val globalViewmodel = LocalGlobalViewmodel.current

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
                        indication = null
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

                    /* Settings are a screen now, not a drawer inside the bar. */
                    FlexibleIcon(
                        icon = Icons.Filled.Settings,
                        size = 38,
                        tintColors = listOf(MaterialTheme.colorScheme.primary),
                        onClick = { globalViewmodel.backstack.add(Screen.Settings()) }
                    )
                }
            }
        )
    }
}
