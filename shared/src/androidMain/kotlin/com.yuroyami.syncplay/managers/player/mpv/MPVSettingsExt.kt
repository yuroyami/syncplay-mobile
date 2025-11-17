package com.yuroyami.syncplay.managers.player.mpv

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adb
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SupervisedUserCircle
import com.yuroyami.syncplay.managers.preferences.Preferences.MPV_DEBUG_MODE
import com.yuroyami.syncplay.managers.preferences.Preferences.MPV_GPU_NEXT
import com.yuroyami.syncplay.managers.preferences.Preferences.MPV_HARDWARE_ACCELERATION
import com.yuroyami.syncplay.managers.preferences.Preferences.MPV_INTERPOLATION
import com.yuroyami.syncplay.managers.preferences.Preferences.MPV_PROFILE
import com.yuroyami.syncplay.managers.preferences.Preferences.MPV_VIDSYNC
import com.yuroyami.syncplay.managers.settings.ExtraSettingBundle
import com.yuroyami.syncplay.managers.settings.Setting
import com.yuroyami.syncplay.managers.settings.SettingCategory
import com.yuroyami.syncplay.managers.settings.SettingType
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
            keyID = "inroom_mpv",
            title = Res.string.uisetting_categ_mpv,
            icon = Icons.Filled.SettingsInputComponent
        ),
        second = buildList {
            add(
                Setting.BooleanSetting(
                type = SettingType.ToggleSettingType,
                staticKey = MPV_HARDWARE_ACCELERATION,
                title = Res.string.uisetting_mpv_hardware_acceleration_title,
                summary = Res.string.uisetting_mpv_hardware_acceleration_summary,
                icon = Icons.Filled.Speed,
                onBooleanChanged = { b ->
                    toggleHardwareAcceleration(b)
                }
            ))

            add(
                Setting.BooleanSetting(
                type = SettingType.ToggleSettingType,
                staticKey = MPV_GPU_NEXT,
                title = Res.string.uisetting_mpv_gpunext_title,
                summary = Res.string.uisetting_mpv_gpunext_summary,
                icon = Icons.Filled.Memory,
                onBooleanChanged = { b ->
                    toggleGpuNext(b)
                }
            ))

            add(
                Setting.MultiChoiceSetting(
                type = SettingType.MultiChoicePopupSettingType,
                staticKey = MPV_VIDSYNC,
                title = Res.string.ui_setting_mpv_vidsync_title,
                summary = Res.string.ui_setting_mpv_vidsync_summary,
                icon = Icons.Filled.SlowMotionVideo,
                entries =  { vidsyncEntries.associateBy { it } },
                onItemChosen = { s ->
                    setVidSyncMode(s)
                }
            ))

            add(
                Setting.BooleanSetting(
                type = SettingType.CheckboxSettingType,
                staticKey = MPV_INTERPOLATION,
                title = Res.string.ui_setting_mpv_interpolation_title,
                summary = Res.string.ui_setting_mpv_interpolation_summary,
                icon = Icons.Filled.Animation,
                onBooleanChanged = { b ->
                    toggleInterpolation(b)
                }
            ))

            add(
                Setting.MultiChoiceSetting(
                type = SettingType.MultiChoicePopupSettingType,
                staticKey = MPV_PROFILE,
                title = Res.string.ui_setting_mpv_profile_title,
                summary = Res.string.ui_setting_mpv_profile_summary,
                icon = Icons.Filled.SupervisedUserCircle,
                entries = { profileEntries.associateBy { it } },
                onItemChosen = { s ->
                    setProfileMode(s)
                }
            ))

            add(
                Setting.SliderSetting(
                type = SettingType.CheckboxSettingType,
                staticKey = MPV_DEBUG_MODE,
                title = Res.string.ui_setting_mpv_debug_title,
                summary = Res.string.ui_setting_mpv_debug_summary,
                maxValue = 3,
                icon = Icons.Filled.Adb,
                onValueChanged = { i ->
                    toggleDebugMode(i)
                }
            ))
        }
    )
}