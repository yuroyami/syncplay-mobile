package app.home.components

import SyncplayMobile.shared.KiteBuildConfig
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import app.LocalGlobalViewmodel
import app.home.HomeViewmodel
import app.uicomponents.SyncplayPopup
import app.uicomponents.SynkplayLogo
import app.uicomponents.SyncplayishText
import app.utils.appName
import app.utils.platform
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.about_client_is_unofficial_disclaimer
import syncplaymobile.shared.generated.resources.about_client_platforms
import syncplaymobile.shared.generated.resources.about_developed_by
import syncplaymobile.shared.generated.resources.about_official_website
import syncplaymobile.shared.generated.resources.about_version
import syncplaymobile.shared.generated.resources.connect_solomode
import syncplaymobile.shared.generated.resources.synkplay_logo
import syncplaymobile.shared.generated.resources.connect_solomode_tooltip
import syncplaymobile.shared.generated.resources.about_tagline
import syncplaymobile.shared.generated.resources.about_source_button
import syncplaymobile.shared.generated.resources.about_report_button
import app.theme.Theming
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring

object PopupAPropos {

    @Composable
    fun AProposPopup(visibilityState: MutableState<Boolean>, homeViewmodel: HomeViewmodel) {
        val globalViewmodel = LocalGlobalViewmodel.current
        val scope = rememberCoroutineScope()

        return SyncplayPopup(
            dialogOpen = visibilityState.value,
            widthPercent = 0.85f,
            onDismiss = { visibilityState.value = false }
        ) {
            // Staged entrance: the mark lands first, the words follow. Cheap, and it makes the
            // popup feel authored rather than dumped on screen.
            var revealed by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { revealed = true }
            val markScale by animateFloatAsState(
                targetValue = if (revealed) 1f else 0.6f,
                animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
                label = "aboutMark"
            )

            Column(
                modifier = Modifier.fillMaxWidth().padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Theming.SpaceMD)
            ) {
                /* Identity block: stacked, not side by side. The old row put a 96dp mark next to
                 * the wordmark and its tagline, which left the tagline about half a line of room
                 * and cut "a Syncplay client for Android" mid-phrase. */
                SynkplayLogo(
                    modifier = Modifier.requiredSize(84.dp).graphicsLayer {
                        scaleX = markScale; scaleY = markScale
                    }
                )

                SyncplayishText(
                    string = appName,
                    textAlign = TextAlign.Center,
                    size = 26f
                )

                Text(
                    text = stringResource(Res.string.about_tagline, platform.label),
                    color = platform.color,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = stringResource(Res.string.about_client_is_unofficial_disclaimer),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = Theming.SpaceXS)
                )

                Text(
                    text = stringResource(Res.string.about_client_platforms),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                /* Facts row: version, author, site. Small, quiet, evenly weighted. */
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AboutFact(stringResource(Res.string.about_version, KiteBuildConfig.APP_VERSION))
                    AboutFact(stringResource(Res.string.about_developed_by))
                    AboutFact(stringResource(Res.string.about_official_website))
                }

                Spacer(Modifier.height(Theming.SpaceXS))

                /* Primary action, with the explanation attached instead of assumed. */
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            visibilityState.value = false
                            globalViewmodel.viewModelScope.launch { homeViewmodel.joinRoom(null) }
                        },
                    ) {
                        Icon(imageVector = Icons.Filled.Tv, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(Res.string.connect_solomode), fontSize = 14.sp)
                    }

                    val soloTooltip = stringResource(Res.string.connect_solomode_tooltip)
                    val tooltipState = rememberTooltipState(isPersistent = true)
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
                        state = tooltipState,
                        tooltip = { PlainTooltip { Text(soloTooltip) } }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = soloTooltip,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp).size(18.dp)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = null,
                                    indication = ripple(bounded = false, radius = 14.dp)
                                ) { scope.launch { tooltipState.show() } }
                        )
                    }
                }

                /* Two labelled links. The bare GitHub octocat meant nothing to anyone who does
                 * not already know the site, and it was the only way to reach either page. */
                val uriHandler = LocalUriHandler.current
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Theming.SpaceSM, Alignment.CenterHorizontally)
                ) {
                    AboutLink(
                        icon = Icons.Outlined.Code,
                        label = stringResource(Res.string.about_source_button),
                        onClick = { uriHandler.openUri("https://www.github.com/yuroyami/syncplay-mobile") }
                    )
                    AboutLink(
                        icon = Icons.Outlined.BugReport,
                        label = stringResource(Res.string.about_report_button),
                        // Lands on a new-issue page with version and platform pre-filled, so the
                        // reporter only writes what happened.
                        onClick = { uriHandler.openUri(bugReportUrl()) }
                    )
                }
            }
        }
    }

    /** New-issue link with environment details already in the body. */
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

    /** One small fact in the About popup's stats row. */
    @Composable
    private fun AboutFact(text: String) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }

    /** A labelled outlined link button; the label is the point, the icon is decoration. */
    @Composable
    private fun AboutLink(icon: ImageVector, label: String, onClick: () -> Unit) {
        OutlinedButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 12.sp, maxLines = 1)
        }
    }
}