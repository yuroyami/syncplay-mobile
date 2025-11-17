package com.yuroyami.syncplay.managers.player.mpv

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsInputComponent
import com.yuroyami.syncplay.managers.preferences.Preferences.MPV_DEBUG_MODE
import com.yuroyami.syncplay.managers.preferences.Preferences.MPV_GPU_NEXT
import com.yuroyami.syncplay.managers.preferences.Preferences.MPV_HARDWARE_ACCELERATION
import com.yuroyami.syncplay.managers.preferences.Preferences.MPV_INTERPOLATION
import com.yuroyami.syncplay.managers.preferences.Preferences.MPV_PROFILE
import com.yuroyami.syncplay.managers.preferences.Preferences.MPV_VIDSYNC
import com.yuroyami.syncplay.managers.settings.ExtraSettingBundle
import com.yuroyami.syncplay.managers.settings.SettingCategory
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.uisetting_categ_mpv

val vidsyncEntries = listOf(
    "audio", "display-resample", "display-resample-vdrop", "display-resample-desync", "display-tempo",
    "display-vdrop", "display-adrop", "display-desync", "desync"
)

val profileEntries = listOf(
    "fast", "high-quality", "gpu-hq", "low-latency", "sw-fast"
)

fun MpvPlayer.getExtraSettings(): ExtraSettingBundle {


    return Pair(
        first = SettingCategory(
            keyID = "inroom_mpv",
            title = Res.string.uisetting_categ_mpv,
            icon = Icons.Filled.SettingsInputComponent
        ),
        second = listOf(
            MPV_HARDWARE_ACCELERATION,
            MPV_GPU_NEXT,
            MPV_VIDSYNC,
            MPV_INTERPOLATION,
            MPV_PROFILE,
            MPV_DEBUG_MODE
        )
    )

    //toggleHardwareAcceleration(b)
    //toggleGpuNext(b)

    //vidsyncEntries.associateBy { it }
    //setVidSyncMode(s)

    //toggleInterpolation(b)

    //profileEntries.associateBy { it } }
    //setProfileMode(s)

    //toggleDebugMode(i)


}