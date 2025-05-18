package com.yuroyami.syncplay.player.mpv

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adb
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SupervisedUserCircle
import com.yuroyami.syncplay.settings.DataStoreKeys
import com.yuroyami.syncplay.settings.Setting
import com.yuroyami.syncplay.settings.SettingType
import com.yuroyami.syncplay.settings.settingROOMstyle
import com.yuroyami.syncplay.watchroom.viewmodel
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.ui_setting_mpv_debug_summary
import syncplaymobile.shared.generated.resources.ui_setting_mpv_debug_title
import syncplaymobile.shared.generated.resources.ui_setting_mpv_interpolation_summary
import syncplaymobile.shared.generated.resources.ui_setting_mpv_interpolation_title
import syncplaymobile.shared.generated.resources.ui_setting_mpv_profile_summary
import syncplaymobile.shared.generated.resources.ui_setting_mpv_profile_title
import syncplaymobile.shared.generated.resources.ui_setting_mpv_vidsync_summary
import syncplaymobile.shared.generated.resources.ui_setting_mpv_vidsync_title
import syncplaymobile.shared.generated.resources.uisetting_mpv_gpunext_summary
import syncplaymobile.shared.generated.resources.uisetting_mpv_gpunext_title
import syncplaymobile.shared.generated.resources.uisetting_mpv_hardware_acceleration_summary
import syncplaymobile.shared.generated.resources.uisetting_mpv_hardware_acceleration_title

val vidsyncEntries = listOf(
    "audio", "display-resample", "display-resample-vdrop", "display-resample-desync", "display-tempo",
    "display-vdrop", "display-adrop", "display-desync", "desync"
)

val profileEntries = listOf(
    "fast", "high-quality", "gpu-hq", "low-latency", "sw-fast"
)

val mpvRoomSettings = listOf(
    Setting.BooleanSetting(
        type = SettingType.ToggleSettingType,
        key = DataStoreKeys.PREF_MPV_HARDWARE_ACCELERATION,
        title = Res.string.uisetting_mpv_hardware_acceleration_title,
        summary = Res.string.uisetting_mpv_hardware_acceleration_summary,
        defaultValue = true,
        icon = Icons.Filled.Speed,
        styling = settingROOMstyle,
        onBooleanChanged = { b ->
            (viewmodel?.player as? MpvPlayer)?.toggleHardwareAcceleration(b)
        }
    ) to DataStoreKeys.CATEG_INROOM_MPV,

    Setting.BooleanSetting(
        type = SettingType.ToggleSettingType,
        key = DataStoreKeys.PREF_MPV_GPU_NEXT,
        title = Res.string.uisetting_mpv_gpunext_title,
        summary = Res.string.uisetting_mpv_gpunext_summary,
        defaultValue = true,
        icon = Icons.Filled.Memory,
        styling = settingROOMstyle,
        onBooleanChanged = { b ->
            (viewmodel?.player as? MpvPlayer)?.toggleGpuNext(b)
        }
    ) to DataStoreKeys.CATEG_INROOM_MPV,

    Setting.MultiChoiceSetting(
        type = SettingType.MultiChoicePopupSettingType,
        key = DataStoreKeys.PREF_MPV_VIDSYNC,
        title = Res.string.ui_setting_mpv_vidsync_title,
        summary = Res.string.ui_setting_mpv_vidsync_summary,
        defaultValue = vidsyncEntries.first(),
        icon = Icons.Filled.SlowMotionVideo,
        styling = settingROOMstyle,
        entryKeys = vidsyncEntries,
        entryValues = vidsyncEntries,
        onItemChosen = { i, s ->
            (viewmodel?.player as? MpvPlayer)?.setVidSyncMode(s)
        }

    ) to DataStoreKeys.CATEG_INROOM_MPV,

    Setting.BooleanSetting(
        type = SettingType.CheckboxSettingType,
        key = DataStoreKeys.PREF_MPV_INTERPOLATION,
        title = Res.string.ui_setting_mpv_interpolation_title,
        summary = Res.string.ui_setting_mpv_interpolation_summary,
        defaultValue = false,
        icon = Icons.Filled.Animation,
        styling = settingROOMstyle,
        onBooleanChanged = { b ->
            (viewmodel?.player as? MpvPlayer)?.toggleInterpolation(b)
        }
    ) to DataStoreKeys.CATEG_INROOM_MPV,

    Setting.MultiChoiceSetting(
        type = SettingType.MultiChoicePopupSettingType,
        key = DataStoreKeys.PREF_MPV_PROFILE,
        title = Res.string.ui_setting_mpv_profile_title,
        summary = Res.string.ui_setting_mpv_profile_summary,
        defaultValue = profileEntries.first(),
        icon = Icons.Filled.SupervisedUserCircle,
        styling = settingROOMstyle,
        entryKeys = profileEntries,
        entryValues = profileEntries,
        onItemChosen = { _, s ->
            (viewmodel?.player as? MpvPlayer)?.setProfileMode(s)
        }
    ) to DataStoreKeys.CATEG_INROOM_MPV,

    Setting.SliderSetting(
        type = SettingType.CheckboxSettingType,
        key = DataStoreKeys.PREF_MPV_DEBUG_MODE,
        title = Res.string.ui_setting_mpv_debug_title,
        summary = Res.string.ui_setting_mpv_debug_summary,
        defaultValue = 0,
        maxValue = 3,
        icon = Icons.Filled.Adb,
        styling = settingROOMstyle,
        onValueChanged = { i ->
            (viewmodel?.player as? MpvPlayer)?.toggleDebugMode(i)
        }
    ) to DataStoreKeys.CATEG_INROOM_MPV,
)