package com.yuroyami.syncplay.player.mpv

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adb
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SupervisedUserCircle
import com.yuroyami.syncplay.logic.managers.datastore.DataStoreKeys
import com.yuroyami.syncplay.logic.managers.datastore.DataStoreKeys.CATEG_INROOM_MPV
import com.yuroyami.syncplay.logic.managers.settings.ExtraSettingBundle
import com.yuroyami.syncplay.logic.managers.settings.Setting
import com.yuroyami.syncplay.logic.managers.settings.SettingCategory
import com.yuroyami.syncplay.logic.managers.settings.SettingType
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.ui_setting_mpv_debug_summary
import syncplaymobile.shared.generated.resources.ui_setting_mpv_debug_title
import syncplaymobile.shared.generated.resources.ui_setting_mpv_interpolation_summary
import syncplaymobile.shared.generated.resources.ui_setting_mpv_interpolation_title
import syncplaymobile.shared.generated.resources.ui_setting_mpv_profile_summary
import syncplaymobile.shared.generated.resources.ui_setting_mpv_profile_title
import syncplaymobile.shared.generated.resources.ui_setting_mpv_vidsync_summary
import syncplaymobile.shared.generated.resources.ui_setting_mpv_vidsync_title
import syncplaymobile.shared.generated.resources.uisetting_categ_mpv
import syncplaymobile.shared.generated.resources.uisetting_mpv_gpunext_summary
import syncplaymobile.shared.generated.resources.uisetting_mpv_gpunext_title
import syncplaymobile.shared.generated.resources.uisetting_mpv_hardware_acceleration_summary
import syncplaymobile.shared.generated.resources.uisetting_mpv_hardware_acceleration_title


fun MpvPlayer.getExtraSettings(): ExtraSettingBundle {
    val vidsyncEntries = listOf(
        "audio", "display-resample", "display-resample-vdrop", "display-resample-desync", "display-tempo",
        "display-vdrop", "display-adrop", "display-desync", "desync"
    )

    val profileEntries = listOf(
        "fast", "high-quality", "gpu-hq", "low-latency", "sw-fast"
    )

    return Pair(
        first = SettingCategory(
            keyID = CATEG_INROOM_MPV,
            title = Res.string.uisetting_categ_mpv,
            icon = Icons.Filled.SettingsInputComponent
        ),
        second = buildList {
            add(
                Setting.BooleanSetting(
                type = SettingType.ToggleSettingType,
                key = DataStoreKeys.PREF_MPV_HARDWARE_ACCELERATION,
                title = Res.string.uisetting_mpv_hardware_acceleration_title,
                summary = Res.string.uisetting_mpv_hardware_acceleration_summary,
                defaultValue = true,
                icon = Icons.Filled.Speed,
                onBooleanChanged = { b ->
                    toggleHardwareAcceleration(b)
                }
            ))

            add(
                Setting.BooleanSetting(
                type = SettingType.ToggleSettingType,
                key = DataStoreKeys.PREF_MPV_GPU_NEXT,
                title = Res.string.uisetting_mpv_gpunext_title,
                summary = Res.string.uisetting_mpv_gpunext_summary,
                defaultValue = true,
                icon = Icons.Filled.Memory,
                onBooleanChanged = { b ->
                    toggleGpuNext(b)
                }
            ))

            add(
                Setting.MultiChoiceSetting(
                type = SettingType.MultiChoicePopupSettingType,
                key = DataStoreKeys.PREF_MPV_VIDSYNC,
                title = Res.string.ui_setting_mpv_vidsync_title,
                summary = Res.string.ui_setting_mpv_vidsync_summary,
                defaultValue = vidsyncEntries.first(),
                icon = Icons.Filled.SlowMotionVideo,
                entries =  { vidsyncEntries.associateBy { it } },
                onItemChosen = { s ->
                    setVidSyncMode(s)
                }
            ))

            add(
                Setting.BooleanSetting(
                type = SettingType.CheckboxSettingType,
                key = DataStoreKeys.PREF_MPV_INTERPOLATION,
                title = Res.string.ui_setting_mpv_interpolation_title,
                summary = Res.string.ui_setting_mpv_interpolation_summary,
                defaultValue = false,
                icon = Icons.Filled.Animation,
                onBooleanChanged = { b ->
                    toggleInterpolation(b)
                }
            ))

            add(
                Setting.MultiChoiceSetting(
                type = SettingType.MultiChoicePopupSettingType,
                key = DataStoreKeys.PREF_MPV_PROFILE,
                title = Res.string.ui_setting_mpv_profile_title,
                summary = Res.string.ui_setting_mpv_profile_summary,
                defaultValue = profileEntries.first(),
                icon = Icons.Filled.SupervisedUserCircle,
                entries = { profileEntries.associateBy { it } },
                onItemChosen = { s ->
                    setProfileMode(s)
                }
            ))

            add(
                Setting.SliderSetting(
                type = SettingType.CheckboxSettingType,
                key = DataStoreKeys.PREF_MPV_DEBUG_MODE,
                title = Res.string.ui_setting_mpv_debug_title,
                summary = Res.string.ui_setting_mpv_debug_summary,
                defaultValue = 0,
                maxValue = 3,
                icon = Icons.Filled.Adb,
                onValueChanged = { i ->
                    toggleDebugMode(i)
                }
            ))
        }
    )
}