package app.design

import androidx.compose.runtime.CompositionLocalProvider
import app.preferences.settings.GLOBAL_ADVANCED
import app.preferences.settings.GLOBAL_NETWORK
import app.preferences.settings.INROOM_CHAT_PROPERTIES
import app.preferences.settings.INROOM_NOTICES
import app.preferences.settings.INROOM_PLAYER_SETTINGS
import app.preferences.settings.INROOM_SYNC
import app.preferences.settings.LocalSettingsDensity
import app.preferences.settings.SettingsDensity
import app.preferences.settings.SETTINGS_GLOBAL
import app.preferences.settings.SETTINGS_ROOM
import app.preferences.settings.SettingsCategoryBody
import app.preferences.settings.SettingsCategoryList
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The settings screens on the real categories, with a height budget for each category. A change
 * that brings back a seven-line row fails here.
 */
class SettingsGolden {

    @Test
    fun roomExplanationsRemainReadableOnNarrowScreens() {
        for ((name, category) in listOf("sync" to INROOM_SYNC, "player" to INROOM_PLAYER_SETTINGS, "notices" to INROOM_NOTICES)) {
            for (scale in listOf(1f, 2f)) {
                // The real host scrolls. Give the complete, expanded category enough room here
                // that the fixture's viewport does not clip its last rows at 200% text.
                val result = DesignHarness.render("settings-$name-explained", 320, heightDp = 5000, fontScale = scale) {
                    CompositionLocalProvider(LocalSettingsDensity provides SettingsDensity(showInlineExplanations = true)) {
                        SettingsCategoryBody(category)
                    }
                }
                assertTrue(result.contentHeightDp < 5000, "Expanded settings exceeded the render canvas")
                result.assertAllTextFits()
            }
        }
    }

    @Test
    fun categories() {
        val network = DesignHarness.render("settings-network", 360) { SettingsCategoryBody(GLOBAL_NETWORK) }
        val player = DesignHarness.render("settings-player", 360) { SettingsCategoryBody(INROOM_PLAYER_SETTINGS) }
        val chat = DesignHarness.render("settings-chat", 360) { SettingsCategoryBody(INROOM_CHAT_PROPERTIES) }
        val notices = DesignHarness.render("settings-notices", 360) { SettingsCategoryBody(INROOM_NOTICES) }
        DesignHarness.render("settings-advanced", 360) { SettingsCategoryBody(GLOBAL_ADVANCED) }
        DesignHarness.render("settings-network", 720) { SettingsCategoryBody(GLOBAL_NETWORK) }
        DesignHarness.render("settings-player", 360, fontScale = 1.3f) { SettingsCategoryBody(INROOM_PLAYER_SETTINGS) }
        DesignHarness.render("settings-categories", 360) { SettingsCategoryList(SETTINGS_GLOBAL) {} }

        // The room shows its six categories two per row inside the side panel, so the grid fills
        // evenly. The names still have to fit the half-width cell.
        for (w in listOf(320, 440)) {
            DesignHarness.render("settings-room-categories", w) {
                SettingsCategoryList(SETTINGS_ROOM, columns = 2) {}
            }.assertAllTextFits()
        }

        // The network category must fit one screen with no scroll, with every control target at
        // the 48dp platform minimum.
        assertTrue(network.contentHeightDp <= 295, "network category ${network.contentHeightDp}dp exceeds its 295dp budget")
        // The player category must fit one screen on a phone in landscape.
        assertTrue(player.contentHeightDp <= 750, "player category ${player.contentHeightDp}dp exceeds its 750dp budget")
        assertTrue(chat.contentHeightDp <= 900, "chat category ${chat.contentHeightDp}dp exceeds its 900dp budget")
        assertTrue(notices.contentHeightDp <= 400, "notices category ${notices.contentHeightDp}dp exceeds its 400dp budget")
    }
}
