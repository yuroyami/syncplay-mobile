package app.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.runtime.Composable
import SyncplayMobile.shared.KiteBuildConfig
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.LocalGlobalViewmodel
import app.Screen
import app.home.HomeViewmodel
import app.i18n.strings
import androidx.compose.ui.draw.clip
import app.theme.Radius
import app.theme.Space
import app.uicomponents.controls.controlStates
import app.theme.ThemeMenu
import app.uicomponents.SynkplayLogo
import app.uicomponents.SyncplayishText
import app.uicomponents.controls.GlyphButton
import app.uicomponents.controls.Rule
import app.uicomponents.controls.SettingsGlyph
import app.utils.appName

/**
 * The home screen's top bar: the logo and wordmark on the left, the theme and settings buttons on
 * the right, and a thin line under it.
 */
@Composable
fun HomeTopBar(viewmodel: HomeViewmodel) {
    val aboutOpen = remember { mutableStateOf(false) }
    val globalViewmodel = LocalGlobalViewmodel.current
    var themeOpen by remember { mutableStateOf(false) }

    PopupAPropos.AProposPopup(aboutOpen, viewmodel)
    ThemeMenu(themeOpen, onDismiss = { themeOpen = false })

    Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)))) {
        Row(
            modifier = Modifier.fillMaxWidth().height(Space.bar).padding(start = Space.gutter, end = Space.gapTight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The logo is a button: it opens About, which holds the way into solo mode. So a
            // remote or a keyboard shows the focus ring on it.
            val logoSource = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .clip(Radius.controlShape)
                    .clickable(interactionSource = logoSource, indication = null, role = Role.Button) { aboutOpen.value = true }
                    .controlStates(logoSource, Radius.controlShape)
                    .padding(horizontal = Space.gapTight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SynkplayLogo(modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(Space.gap))
                // The version sits beside the wordmark in the same brush, on the same baseline.
                Row {
                    SyncplayishText(string = appName, size = 20f, modifier = Modifier.alignByBaseline())
                    Spacer(Modifier.width(Space.gapTight))
                    SyncplayishText(string = KiteBuildConfig.APP_VERSION, size = 11f, modifier = Modifier.alignByBaseline())
                }
            }
            Spacer(Modifier.weight(1f))
            GlyphButton(Icons.Outlined.Palette, name = strings.themePopupSelectATheme) { themeOpen = true }
            GlyphButton(SettingsGlyph, name = strings.settingsTitle) { globalViewmodel.backstack.add(Screen.Settings()) }
        }
        Rule()
    }
}
