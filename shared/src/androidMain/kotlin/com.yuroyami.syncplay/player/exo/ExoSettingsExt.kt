package com.yuroyami.syncplay.player.exo

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.SettingsInputComponent
import com.yuroyami.syncplay.settings.DataStoreKeys.CATEG_INROOM_EXOPLAYER
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_EXO_MAX_BUFFER
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_EXO_MIN_BUFFER
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_EXO_SEEK_BUFFER
import com.yuroyami.syncplay.settings.ExtraSettingBundle
import com.yuroyami.syncplay.settings.Setting
import com.yuroyami.syncplay.settings.SettingCategory
import com.yuroyami.syncplay.settings.SettingType
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.setting_max_buffer_summary
import syncplaymobile.shared.generated.resources.setting_max_buffer_title
import syncplaymobile.shared.generated.resources.setting_min_buffer_summary
import syncplaymobile.shared.generated.resources.setting_min_buffer_title
import syncplaymobile.shared.generated.resources.setting_playback_buffer_summary
import syncplaymobile.shared.generated.resources.setting_playback_buffer_title
import syncplaymobile.shared.generated.resources.settings_categ_exoplayer

fun ExoPlayer.getExtraSettings(): ExtraSettingBundle {
    //TODO Reflect changes at runtime

    return Pair(
        first = SettingCategory(
            keyID = CATEG_INROOM_EXOPLAYER,
            title = Res.string.settings_categ_exoplayer,
            icon = Icons.Filled.SettingsInputComponent
        ),
        second = buildList {
            add(
                Setting.SliderSetting(
                    type = SettingType.SliderSettingType,
                    key = PREF_EXO_MAX_BUFFER,
                    title = Res.string.setting_max_buffer_title,
                    summary = Res.string.setting_max_buffer_summary,
                    defaultValue = 30,
                    icon = Icons.Filled.HourglassTop,
                    maxValue = 60,
                    minValue = 1,
                )
            )
            add(
                Setting.SliderSetting(
                    type = SettingType.SliderSettingType,
                    key = PREF_EXO_MIN_BUFFER,
                    title = Res.string.setting_min_buffer_title,
                    summary = Res.string.setting_min_buffer_summary,
                    defaultValue = 15,
                    icon = Icons.Filled.HourglassBottom,
                    maxValue = 30,
                    minValue = 1,
                )
            )
            add(
                Setting.SliderSetting(
                    type = SettingType.SliderSettingType,
                    key = PREF_EXO_SEEK_BUFFER,
                    title = Res.string.setting_playback_buffer_title,
                    summary = Res.string.setting_playback_buffer_summary,
                    defaultValue = 2500,
                    icon = Icons.Filled.HourglassEmpty,
                    maxValue = 15000,
                    minValue = 100,
                )
            )
        }
    )
}