package app.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.layout.offset
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
import app.theme.Space
import app.theme.ThemeMenu
import app.uicomponents.SynkplayLogo
import app.uicomponents.SyncplayishText
import app.uicomponents.controls.GlyphButton
import app.uicomponents.controls.Rule
import app.uicomponents.controls.SettingsGlyph
import app.utils.appName
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.settings_title
import syncplaymobile.shared.generated.resources.theme_popup_select_a_theme

/** Logo and wordmark on the left, theme and settings glyphs on the right, one hairline under. */
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
            Row(
                modifier = Modifier.clickable(interactionSource = null, indication = null, role = Role.Button) { aboutOpen.value = true },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SynkplayLogo(modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(Space.gap))
                Column(horizontalAlignment = Alignment.End) {
                    // The version rides the wordmark as a tiny overscore, same brush, no pull on the eye.
                    SyncplayishText(string = KiteBuildConfig.APP_VERSION, size = 8f, modifier = Modifier.offset(y = 3.dp))
                    SyncplayishText(string = appName, size = 20f)
                }
            }
            Spacer(Modifier.weight(1f))
            GlyphButton(Icons.Outlined.Palette, name = stringResource(Res.string.theme_popup_select_a_theme)) { themeOpen = true }
            GlyphButton(SettingsGlyph, name = stringResource(Res.string.settings_title)) { globalViewmodel.backstack.add(Screen.Settings()) }
        }
        Rule()
    }
}
