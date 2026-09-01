package app.design

import app.preferences.settings.GLOBAL_ADVANCED
import app.preferences.settings.GLOBAL_NETWORK
import app.preferences.settings.INROOM_CHAT_PROPERTIES
import app.preferences.settings.INROOM_PLAYER_SETTINGS
import app.preferences.settings.SETTINGS_GLOBAL
import app.preferences.settings.SettingsCategoryBody
import app.preferences.settings.SettingsCategoryList
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The settings console on the real categories, with the height budgets from
 * DESIGN/PREF_SYSTEM. A change that reintroduces a seven line row fails here.
 */
class SettingsGolden {

    @Test
    fun categories() {
        val network = DesignHarness.render("settings-network", 360) { SettingsCategoryBody(GLOBAL_NETWORK) }
        val player = DesignHarness.render("settings-player", 360) { SettingsCategoryBody(INROOM_PLAYER_SETTINGS) }
        val chat = DesignHarness.render("settings-chat", 360) { SettingsCategoryBody(INROOM_CHAT_PROPERTIES) }
        DesignHarness.render("settings-advanced", 360) { SettingsCategoryBody(GLOBAL_ADVANCED) }
        DesignHarness.render("settings-network", 720) { SettingsCategoryBody(GLOBAL_NETWORK) }
        DesignHarness.render("settings-player", 360, fontScale = 1.3f) { SettingsCategoryBody(INROOM_PLAYER_SETTINGS) }
        DesignHarness.render("settings-categories", 360) { SettingsCategoryList(SETTINGS_GLOBAL) {} }

        assertTrue(network.contentHeightDp <= 260, "network category ${network.contentHeightDp}dp exceeds its 260dp budget")
        assertTrue(player.contentHeightDp <= 640, "player category ${player.contentHeightDp}dp exceeds its 640dp budget")
        assertTrue(chat.contentHeightDp <= 900, "chat category ${chat.contentHeightDp}dp exceeds its 900dp budget")
    }
}
