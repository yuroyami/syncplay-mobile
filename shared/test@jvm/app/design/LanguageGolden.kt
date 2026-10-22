package app.design

import app.i18n.Locales
import app.preferences.settings.GLOBAL_NETWORK
import app.preferences.settings.INROOM_NOTICES
import app.preferences.settings.INROOM_SYNC
import app.preferences.settings.SettingsCategoryBody
import app.preferences.settings.SettingsCategoryList
import app.preferences.settings.SETTINGS_ROOM
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import app.home.components.HomeEnginePicker
import app.home.components.PopupAPropos
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.exoplayer
import syncplaymobile.shared.generated.resources.kiteplayer
import syncplaymobile.shared.generated.resources.mpv
import kotlin.test.Test

/**
 * The same screens in every language that the app ships.
 *
 * German and Russian words are longer than English ones, and Arabic reads right to left, so a
 * layout that passes in English can still fail in another language. These renders fail when a
 * translated label is clipped or ellipsised.
 */
class LanguageGolden {

    private val languages = listOf(
        Locales.En, Locales.Ar, Locales.De, Locales.Es,
        Locales.Fr, Locales.Pl, Locales.Ru, Locales.Zh,
    )

    @Test
    fun settingsRowsSurviveEveryLanguage() {
        for (language in languages) {
            for (category in listOf(INROOM_SYNC, INROOM_NOTICES, GLOBAL_NETWORK)) {
                DesignHarness.render(
                    name = "lang-${category.key}",
                    widthDp = 360,
                    heightDp = 2200,
                    language = language,
                ) { SettingsCategoryBody(category) }.assertAllTextFits()
            }
        }
    }

    @Test
    fun theRoomsCategoryGridSurvivesEveryLanguage() {
        for (language in languages) {
            DesignHarness.render(
                name = "lang-room-categories",
                widthDp = 320,
                heightDp = 600,
                language = language,
            ) { SettingsCategoryList(SETTINGS_ROOM, columns = 2) {} }.assertAllTextFits()
        }
    }

    /**
     * The engine picker, where the user chooses a video player, is the tightest row on the home
     * screen: three cells, each with a badge.
     */
    @Test
    fun theEnginePickerSurvivesEveryLanguage() {
        DesignHarness.initDatastore()
        val engines = listOf(
            FakeEngine("ExoPlayer", Res.drawable.exoplayer, isSystem = true),
            FakeEngine("mpv", Res.drawable.mpv, isDefault = true),
            FakeEngine("KitePlayer", Res.drawable.kiteplayer, isExperimental = true),
        )
        for (language in languages) {
            for (w in listOf(284, 393)) {
                DesignHarness.render("lang-engine-picker", w, heightDp = 280, language = language) {
                    Box(Modifier.fillMaxWidth()) {
                        HomeEnginePicker(engines = engines, selectedEngine = "mpv", onSelectEngine = {}, compact = false)
                    }
                }.assertAllTextFits()
            }
        }
    }

    @Test
    fun theAboutPageSurvivesEveryLanguage() {
        for (language in languages) {
            DesignHarness.render(
                name = "lang-about",
                widthDp = 360,
                heightDp = 900,
                language = language,
            ) {
                PopupAPropos.AboutBody(
                    updateResult = null,
                    updateChecking = false,
                    onCheckUpdate = {},
                    onOpenUri = {},
                    onLicences = {},
                    onWatchAlone = {},
                )
            }.assertAllTextFits()
        }
    }
}
