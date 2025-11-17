package com.yuroyami.syncplay.managers.player.exo

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsInputComponent
import com.yuroyami.syncplay.managers.preferences.Preferences.EXO_MAX_BUFFER
import com.yuroyami.syncplay.managers.preferences.Preferences.EXO_MIN_BUFFER
import com.yuroyami.syncplay.managers.preferences.Preferences.EXO_SEEK_BUFFER
import com.yuroyami.syncplay.managers.settings.SettingCategory
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.settings_categ_exoplayer

fun ExoPlayer.getExtraSettings(): SettingCategory {
    //TODO Reflect changes at runtime

    return SettingCategory(
        keyID = "inroom_exoplayer",
        title = Res.string.settings_categ_exoplayer,
        icon = Icons.Filled.SettingsInputComponent
    ).apply {
        intSettings.addAll(listOf(EXO_MAX_BUFFER, EXO_MIN_BUFFER, EXO_SEEK_BUFFER))
    }
}