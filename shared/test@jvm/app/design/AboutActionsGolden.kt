package app.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.home.components.UpdateCheck
import app.home.components.UpdateCheckAction
import app.theme.Space
import app.uicomponents.controls.AccentAction
import app.uicomponents.controls.SecondaryActionPair
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.about_licences_button
import syncplaymobile.shared.generated.resources.about_privacy_button
import syncplaymobile.shared.generated.resources.connect_watch_alone
import kotlin.test.Test
import kotlin.test.assertTrue

class AboutActionsGolden {
    @Composable
    private fun Actions() {
        Column(Modifier.fillMaxWidth().padding(Space.gutter), verticalArrangement = Arrangement.spacedBy(Space.gap)) {
            SecondaryActionPair(stringResource(Res.string.about_privacy_button), {}, stringResource(Res.string.about_licences_button), {})
            UpdateCheckAction(null, false, {}, {})
            UpdateCheckAction(null, true, {}, {})
            UpdateCheckAction(UpdateCheck.Result.UpToDate, false, {}, {})
            UpdateCheckAction(UpdateCheck.Result.Newer("0.25.0", "https://example.com/release"), false, {}, {})
            UpdateCheckAction(UpdateCheck.Result.Unreachable, false, {}, {})
            AccentAction(stringResource(Res.string.connect_watch_alone), {}, Modifier.fillMaxWidth())
        }
    }

    @Test
    fun labelsAndUpdateStatesAtNarrowWidthsAndLargeText() {
        for (width in listOf(280, 320, 360)) {
            for (scale in listOf(1f, 2f)) {
                val result = DesignHarness.render("about-actions", width, heightDp = if (scale == 1f) 700 else 1000, fontScale = scale) { Actions() }
                assertTrue(result.textLayouts.size >= 8, "The real button labels must expose their text layouts")
                result.assertAllTextFits()
            }
        }
        DesignHarness.render("about-actions", 320, heightDp = 700, theme = DesignHarness.lightTheme) { Actions() }
    }
}
