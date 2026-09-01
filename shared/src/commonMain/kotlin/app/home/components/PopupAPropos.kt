package app.home.components

import SyncplayMobile.shared.KiteBuildConfig
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import app.LocalGlobalViewmodel
import app.home.HomeViewmodel
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.SynkplayLogo
import app.uicomponents.SyncplayishText
import app.uicomponents.controls.AccentAction
import app.uicomponents.controls.SecondaryAction
import app.uicomponents.frames.Modal
import app.uicomponents.frames.ModalSize
import app.utils.appName
import app.utils.platform
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.about_client_is_unofficial_disclaimer
import syncplaymobile.shared.generated.resources.about_client_platforms
import syncplaymobile.shared.generated.resources.about_developed_by
import syncplaymobile.shared.generated.resources.about_official_website
import syncplaymobile.shared.generated.resources.about_report_button
import syncplaymobile.shared.generated.resources.about_source_button
import syncplaymobile.shared.generated.resources.about_tagline
import syncplaymobile.shared.generated.resources.about_version
import syncplaymobile.shared.generated.resources.connect_watch_alone

object PopupAPropos {

    /** About: the mark, the wordmark, what the app is, the facts, and the links. */
    @Composable
    fun AProposPopup(visibilityState: MutableState<Boolean>, homeViewmodel: HomeViewmodel) {
        val p = palette
        val globalViewmodel = LocalGlobalViewmodel.current
        val uriHandler = LocalUriHandler.current

        Modal(
            open = visibilityState.value,
            onDismiss = { visibilityState.value = false },
            size = ModalSize.Panel,
            actions = {
                SecondaryAction(stringResource(Res.string.about_source_button), onClick = { uriHandler.openUri("https://www.github.com/yuroyami/syncplay-mobile") })
                SecondaryAction(stringResource(Res.string.about_report_button), onClick = { uriHandler.openUri(bugReportUrl()) })
                AccentAction(stringResource(Res.string.connect_watch_alone), onClick = {
                    visibilityState.value = false
                    globalViewmodel.viewModelScope.launch { homeViewmodel.joinRoom(null) }
                })
            },
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                SynkplayLogo(modifier = Modifier.size(84.dp))
                Spacer(Modifier.height(Space.gap))
                SyncplayishText(string = appName, textAlign = TextAlign.Center, size = 26f)
                Text(
                    text = stringResource(Res.string.about_tagline, platform.label),
                    style = Type.value,
                    color = platform.color,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Space.gap))
                Text(
                    text = stringResource(Res.string.about_client_is_unofficial_disclaimer),
                    style = Type.note,
                    color = p.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Space.gapTight))
                Text(
                    text = stringResource(Res.string.about_client_platforms),
                    style = Type.note,
                    color = p.inkDim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Space.gap))
                Row(Modifier.fillMaxWidth().padding(bottom = Space.gapTight), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Text(stringResource(Res.string.about_version, KiteBuildConfig.APP_VERSION), style = Type.value, color = p.inkDim, maxLines = 1)
                    Text(stringResource(Res.string.about_developed_by), style = Type.value, color = p.inkDim, maxLines = 1)
                    Text(stringResource(Res.string.about_official_website), style = Type.value, color = p.inkDim, maxLines = 1)
                }
            }
        }
    }

    /** A new-issue link with the environment already in the body. */
    private fun bugReportUrl(): String {
        val body = """
            |**What happened?**
            |
            |
            |**What did you expect?**
            |
            |
            |---
            |App version: ${KiteBuildConfig.APP_VERSION}
            |Platform: ${platform.label}
        """.trimMargin()
        return "https://github.com/yuroyami/syncplay-mobile/issues/new" +
            "?title=" + urlEncode("[${platform.label}] ") +
            "&body=" + urlEncode(body)
    }

    private fun urlEncode(s: String): String = buildString {
        for (b in s.encodeToByteArray()) {
            val c = b.toInt().toChar()
            when {
                c.isLetterOrDigit() || c in "-._~" -> append(c)
                else -> append('%').append(((b.toInt() and 0xFF) or 0x100).toString(16).substring(1).uppercase())
            }
        }
    }
}
