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
import com.yuroyami.syncplay.watchroom.lyricist
import com.yuroyami.syncplay.watchroom.viewmodel

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
        title = lyricist.strings.uisettingMpvHardwareAccelerationTitle,
        summary = lyricist.strings.uisettingMpvHardwareAccelerationSummary,
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
        title = lyricist.strings.uisettingMpvGpunextTitle,
        summary = lyricist.strings.uisettingMpvGpunextSummary,
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
        title = lyricist.strings.uiSettingMpvVidsyncTitle,
        summary = lyricist.strings.uiSettingMpvVidsyncSummary,
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
        title = lyricist.strings.uiSettingMpvInterpolationTitle,
        summary = lyricist.strings.uiSettingMpvInterpolationSummary,
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
        title = lyricist.strings.uiSettingMpvProfileTitle,
        summary = lyricist.strings.uiSettingMpvProfileSummary,
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
        title = lyricist.strings.uiSettingMpvDebugTitle,
        summary = lyricist.strings.uiSettingMpvDebugSummary,
        defaultValue = 0,
        maxValue = 3,
        icon = Icons.Filled.Adb,
        styling = settingROOMstyle,
        onValueChanged = { i ->
            (viewmodel?.player as? MpvPlayer)?.toggleDebugMode(i)
        }
    ) to DataStoreKeys.CATEG_INROOM_MPV,
)