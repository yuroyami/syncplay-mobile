package app.home.components

import SyncplayMobile.shared.KiteBuildConfig
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import app.i18n.strings
import app.uicomponents.controls.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import app.LocalGlobalViewmodel
import app.preferences.value
import app.preferences.Preferences
import app.home.HomeViewmodel
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.LocalWidthClass
import app.uicomponents.SynkplayLogo
import app.uicomponents.WidthClass
import app.uicomponents.SyncplayishText
import app.uicomponents.controls.AccentAction
import app.uicomponents.controls.SecondaryActionPair
import app.uicomponents.frames.Modal
import app.uicomponents.frames.ModalSize
import app.utils.appName
import app.utils.platform
import app.utils.platformDescription
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import app.uicomponents.controls.ListRow

object PopupAPropos {

    /** The About popup: the logo, the wordmark, what the app is, a few facts, and the links. */
    @Composable
    fun AProposPopup(visibilityState: MutableState<Boolean>, homeViewmodel: HomeViewmodel) {
        val globalViewmodel = LocalGlobalViewmodel.current
        val uriHandler = LocalUriHandler.current
        val licencesOpen = remember { mutableStateOf(false) }
        val updateCheck = homeViewmodel.updateCheck

        Modal(
            open = visibilityState.value,
            onDismiss = { visibilityState.value = false },
            size = ModalSize.Panel,
        ) {
            AboutBody(
                updateResult = updateCheck.result,
                updateChecking = updateCheck.isChecking,
                onCheckUpdate = updateCheck::check,
                onOpenUri = uriHandler::openUri,
                onLicences = { licencesOpen.value = true },
                onWatchAlone = {
                    visibilityState.value = false
                    globalViewmodel.viewModelScope.launch { homeViewmodel.joinRoom(null) }
                },
            )
        }

        LicencesModal(licencesOpen)
    }

    /**
     * The body of the About popup, without its dialog, so the desktop screenshot tests can draw
     * it. Normally the story sits above the links. In a window too short for that (a phone in
     * landscape, where the panel is 330dp tall), the story sits beside the links, so Watch alone
     * and the update check show without scrolling.
     */
    @Composable
    internal fun AboutBody(
        updateResult: UpdateCheck.Result?,
        updateChecking: Boolean,
        onCheckUpdate: () -> Unit,
        onOpenUri: (String) -> Unit,
        onLicences: () -> Unit,
        onWatchAlone: () -> Unit,
    ) {
        val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
        val sideBySide = windowHeight < SHORT_WINDOW && LocalWidthClass.current != WidthClass.Compact
        if (sideBySide) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.gutter), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Story(compact = true) }
                Column(Modifier.weight(1f)) { Links(updateResult, updateChecking, onCheckUpdate, onOpenUri, onLicences, onWatchAlone) }
            }
        } else {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Story(compact = false)
                Spacer(Modifier.height(Space.gap))
                Links(updateResult, updateChecking, onCheckUpdate, onOpenUri, onLicences, onWatchAlone)
            }
        }
    }

    /** The logo, the wordmark, what the app is, and three facts. [compact] draws a smaller logo. */
    @Composable
    private fun ColumnScope.Story(compact: Boolean) {
        val p = palette
        SynkplayLogo(modifier = Modifier.size(if (compact) 48.dp else 84.dp))
        Spacer(Modifier.height(Space.gap))
        SyncplayishText(string = appName, textAlign = TextAlign.Center, size = 26f)
        Text(
            text = strings.aboutTagline(platform.label),
            style = Type.value,
            color = platform.color,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Space.gap))
        Text(
            text = strings.aboutBlurb,
            style = Type.note,
            color = p.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Space.gapTight))
        Text(
            text = strings.aboutIndependent,
            style = Type.note,
            color = p.inkDim,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Space.gap))
        // When the text grows, the three facts wrap to a second line instead of running together.
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = Space.gapTight),
            horizontalArrangement = Arrangement.spacedBy(Space.gutter, Alignment.CenterHorizontally),
        ) {
            Text(strings.aboutVersionValue(KiteBuildConfig.APP_VERSION), style = Type.value, color = p.inkDim, maxLines = 1)
            Text(strings.aboutAuthor, style = Type.value, color = p.inkDim, maxLines = 1)
            Text(strings.aboutWebsite, style = Type.value, color = p.inkDim, maxLines = 1)
        }
    }

    /**
     * The links, the update check, and Watch alone, which is the only way into solo mode
     * (offline playback).
     */
    @Composable
    private fun Links(
        updateResult: UpdateCheck.Result?,
        updateChecking: Boolean,
        onCheckUpdate: () -> Unit,
        onOpenUri: (String) -> Unit,
        onLicences: () -> Unit,
        onWatchAlone: () -> Unit,
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.gapTight)) {
            SecondaryActionPair(
                firstText = strings.aboutSourceButton,
                onFirstClick = { onOpenUri("https://www.github.com/yuroyami/syncplay-mobile") },
                secondText = strings.aboutReportButton,
                onSecondClick = { onOpenUri(bugReportUrl()) },
            )
            SecondaryActionPair(
                firstText = strings.aboutPrivacyButton,
                onFirstClick = { onOpenUri("https://github.com/yuroyami/syncplay-mobile/blob/master/PRIVACY_POLICY.md") },
                secondText = strings.aboutLicencesButton,
                onSecondClick = onLicences,
            )
            // The user starts the update check; it never runs by itself. A direct download has no
            // other way to learn that a newer version exists.
            UpdateCheckAction(
                result = updateResult,
                isChecking = updateChecking,
                onCheck = onCheckUpdate,
                onOpenRelease = onOpenUri,
            )
            Spacer(Modifier.height(Space.gapTight))
            AccentAction(text = strings.connectWatchAlone, onClick = onWatchAlone, modifier = Modifier.fillMaxWidth())
        }
    }

    /** Under this window height the story sits beside the links instead of above them. */
    private val SHORT_WINDOW = 480.dp

    /** The licences popup: every third-party piece inside the app, with its licence and a link. */
    @Composable
    private fun LicencesModal(open: MutableState<Boolean>) {
        val p = palette
        val uriHandler = LocalUriHandler.current
        Modal(
            open = open.value,
            onDismiss = { open.value = false },
            title = strings.aboutLicencesTitle,
            size = ModalSize.Panel,
        ) {
            Text(
                text = strings.aboutLicencesNote,
                style = Type.note,
                color = p.inkDim,
                modifier = Modifier.fillMaxWidth().padding(bottom = Space.gap),
            )
            attributions.forEach { item ->
                ListRow(onClick = { uriHandler.openUri(item.url) }, horizontalPadding = Space.gapTight) {
                    Text(item.name, style = Type.label, color = p.ink, modifier = Modifier.weight(1f))
                    Text(item.licence, style = Type.value, color = p.inkDim, maxLines = 1)
                }
            }
        }
    }

    /** A new-issue link with the environment already in the body. */
    private fun bugReportUrl(): String {
        // The body already holds what triage needs. Without the engine and the build, most
        // reports need a second message before anyone can reproduce them.
        val body = """
            |**What happened?**
            |
            |
            |**What did you expect?**
            |
            |
            |---
            |App version: ${KiteBuildConfig.APP_VERSION}${if (KiteBuildConfig.EXOPLAYER_ONLY) " (exoOnly)" else ""}
            |Platform: ${platform.label}
            |Device: ${platformDescription()}
            |Video engine: ${Preferences.PLAYER_ENGINE.value()}
            |Network engine: ${Preferences.NETWORK_ENGINE.value()}
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
