package app.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import app.room.ui.misc.VisualizerNotice
import kotlin.test.Test

/**
 * The flashing light notice of the audio visualizer, open, on phones in every shipped language. It
 * is the longest text in any ask modal, so every language has to fit a small phone.
 */
class VisualizerNoticeGolden {

    @Composable
    private fun Notice() = VisualizerNotice(remember { mutableStateOf(true) })

    @Test
    fun theNoticeKeepsEveryWordInEveryLanguage() {
        for (language in listOf("en", "de", "fr", "es", "pl", "ru", "ar", "zh")) {
            DesignHarness.render("visualizer-notice", 360, heightDp = 640, language = language) { Notice() }.assertAllTextFits()
        }
        DesignHarness.render("visualizer-notice", 320, heightDp = 568, fontScale = 1.3f) { Notice() }.assertAllTextFits()
        DesignHarness.render("visualizer-notice", 375, heightDp = 812, theme = DesignHarness.lightTheme) { Notice() }.assertAllTextFits()
    }
}
