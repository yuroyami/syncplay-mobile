package com.yuroyami.syncplay.player.mpv

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adb
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Speed
import com.yuroyami.syncplay.settings.DataStoreKeys
import com.yuroyami.syncplay.settings.MySettings
import com.yuroyami.syncplay.settings.Setting
import com.yuroyami.syncplay.settings.SettingType
import com.yuroyami.syncplay.watchroom.lyricist
import com.yuroyami.syncplay.watchroom.viewmodel

val mpvRoomSettings = listOf(
    Setting.BooleanSetting(
        type = SettingType.ToggleSettingType,
        key = DataStoreKeys.PREF_MPV_HARDWARE_ACCELERATION,
        title = lyricist.strings.uisettingMpvHardwareAccelerationTitle,
        summary = lyricist.strings.uisettingMpvHardwareAccelerationSummary,
        defaultValue = true,
        icon = Icons.Filled.Speed,
        styling = MySettings.settingROOMstyle,
        onBooleanChanged = { b ->
            (viewmodel?.player as? MpvPlayer)?.toggleHardwareAcceleration(b)
        }
    ) to DataStoreKeys.CATEG_INROOM_MPV,

    Setting.BooleanSetting(
        type = SettingType.ToggleSettingType,
        key = DataStoreKeys.PREF_MPV_GPU_NEXT,
        title = lyricist.strings.uisettingMpvGpunextTitle,
        summary = lyricist.strings.uisettingMpvGpunextSummary,
        defaultValue = false,
        icon = Icons.Filled.Memory,
        styling = MySettings.settingROOMstyle,
        onBooleanChanged = { b ->
            (viewmodel?.player as? MpvPlayer)?.toggleGpuNext(b)
        }
    ) to DataStoreKeys.CATEG_INROOM_MPV,

    Setting.BooleanSetting(
        type = SettingType.CheckboxSettingType,
        key = DataStoreKeys.PREF_MPV_INTERPOLATION,
        title = lyricist.strings.uiSettingMpvInterpolationTitle,
        summary = lyricist.strings.uiSettingMpvInterpolationSummary,
        defaultValue = false,
        icon = Icons.Filled.SlowMotionVideo,
        styling = MySettings.settingROOMstyle,
        onBooleanChanged = { b ->
            (viewmodel?.player as? MpvPlayer)?.toggleInterpolation(b)
        }
    ) to DataStoreKeys.CATEG_INROOM_MPV,

    Setting.BooleanSetting(
        type = SettingType.CheckboxSettingType,
        key = DataStoreKeys.PREF_MPV_DEBUG_MODE,
        title = lyricist.strings.uiSettingMpvDebugTitle,
        summary = lyricist.strings.uiSettingMpvDebugSummary,
        defaultValue = false,
        icon = Icons.Filled.Adb,
        styling = MySettings.settingROOMstyle,
        onBooleanChanged = { b ->
            (viewmodel?.player as? MpvPlayer)?.toggleDebugMode(b)
        }
    ) to DataStoreKeys.CATEG_INROOM_MPV,
)