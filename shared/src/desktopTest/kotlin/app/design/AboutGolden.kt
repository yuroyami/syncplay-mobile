package app.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import app.home.components.PopupAPropos
import app.home.components.UpdateCheck
import app.uicomponents.LocalWidthClass
import app.uicomponents.WidthClass
import app.uicomponents.frames.ModalFrame
import app.uicomponents.frames.ModalSize
import kotlin.test.Test

/**
 * The About screen inside its modal frame, the way a phone, a phone on its side and a desktop
 * window show it. The test renders the frame, not the bare body column, because the frame is what
 * people see.
 */
class AboutGolden {

    @Composable
    private fun About(result: UpdateCheck.Result? = null) {
        ModalFrame(ModalSize.Panel, null, true, {}, actions = null) {
            PopupAPropos.AboutBody(
                updateResult = result,
                updateChecking = false,
                onCheckUpdate = {},
                onOpenUri = {},
                onLicences = {},
                onWatchAlone = {},
            )
        }
    }

    @Test
    fun aboutInItsFrameKeepsEveryWord() {
        // Phones upright: a sheet. The width class is Compact by default in the harness.
        for ((w, h) in listOf(360 to 780, 375 to 812, 412 to 915)) {
            DesignHarness.render("about-modal", w, heightDp = h) { About() }.assertAllTextFits()
        }
        DesignHarness.render("about-modal", 375, heightDp = 812, fontScale = 1.3f) { About() }.assertAllTextFits()
        // Short phones upright: the logo moves beside the name.
        for ((w, h) in listOf(320 to 568, 360 to 640, 375 to 667)) {
            DesignHarness.render("about-modal", w, heightDp = h) { About() }.assertAllTextFits()
        }
        DesignHarness.render("about-modal", 360, heightDp = 640, fontScale = 1.3f) { About() }.assertAllTextFits()
        DesignHarness.render("about-modal", 375, heightDp = 812, theme = DesignHarness.lightTheme) { About(UpdateCheck.Result.UpToDate) }.assertAllTextFits()
        // A phone on its side: a wide panel with the story beside the links.
        for ((w, h) in listOf(812 to 375, 874 to 402)) {
            DesignHarness.render("about-modal", w, heightDp = h) {
                CompositionLocalProvider(LocalWidthClass provides WidthClass.Medium) { About(UpdateCheck.Result.Newer("0.25.0", "https://example.com")) }
            }.assertAllTextFits()
        }
        // A desktop window: the 440dp panel.
        DesignHarness.render("about-modal", 1280, heightDp = 720) {
            CompositionLocalProvider(LocalWidthClass provides WidthClass.Expanded) { About() }
        }.assertAllTextFits()
    }
}
