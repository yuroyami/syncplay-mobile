package com.yuroyami.syncplay.managers.preferences

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Adb
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material.icons.filled.DesignServices
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FrontHand
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LogoDev
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.SpatialAudio
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SupervisedUserCircle
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Web
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.preferences.core.edit
import com.yuroyami.syncplay.ui.popups.PopupMediaDirs.MediaDirsPopup
import com.yuroyami.syncplay.ui.screens.theme.Theming
import com.yuroyami.syncplay.ui.screens.theme.defaultTheme
import com.yuroyami.syncplay.utils.PLATFORM
import com.yuroyami.syncplay.utils.availablePlatformVideoEngines
import com.yuroyami.syncplay.utils.generateTimestampMillis
import com.yuroyami.syncplay.utils.get
import com.yuroyami.syncplay.utils.logFile
import com.yuroyami.syncplay.utils.platform
import com.yuroyami.syncplay.utils.platformCallback
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.language_codes
import syncplaymobile.shared.generated.resources.language_names
import syncplaymobile.shared.generated.resources.media_directories
import syncplaymobile.shared.generated.resources.media_directories_setting_summary
import syncplaymobile.shared.generated.resources.setting_audio_default_language_summry
import syncplaymobile.shared.generated.resources.setting_audio_default_language_title
import syncplaymobile.shared.generated.resources.setting_cc_default_language_summry
import syncplaymobile.shared.generated.resources.setting_cc_default_language_title
import syncplaymobile.shared.generated.resources.setting_display_language_summry
import syncplaymobile.shared.generated.resources.setting_display_language_title
import syncplaymobile.shared.generated.resources.setting_erase_shortcuts_dialog
import syncplaymobile.shared.generated.resources.setting_erase_shortcuts_summary
import syncplaymobile.shared.generated.resources.setting_erase_shortcuts_title
import syncplaymobile.shared.generated.resources.setting_export_log_summary
import syncplaymobile.shared.generated.resources.setting_export_log_title
import syncplaymobile.shared.generated.resources.setting_fileinfo_behavior_a
import syncplaymobile.shared.generated.resources.setting_fileinfo_behavior_b
import syncplaymobile.shared.generated.resources.setting_fileinfo_behavior_c
import syncplaymobile.shared.generated.resources.setting_fileinfo_behaviour_name_summary
import syncplaymobile.shared.generated.resources.setting_fileinfo_behaviour_name_title
import syncplaymobile.shared.generated.resources.setting_fileinfo_behaviour_size_summary
import syncplaymobile.shared.generated.resources.setting_fileinfo_behaviour_size_title
import syncplaymobile.shared.generated.resources.setting_max_buffer_summary
import syncplaymobile.shared.generated.resources.setting_max_buffer_title
import syncplaymobile.shared.generated.resources.setting_min_buffer_summary
import syncplaymobile.shared.generated.resources.setting_min_buffer_title
import syncplaymobile.shared.generated.resources.setting_network_engine_ktor
import syncplaymobile.shared.generated.resources.setting_network_engine_netty
import syncplaymobile.shared.generated.resources.setting_network_engine_summary
import syncplaymobile.shared.generated.resources.setting_network_engine_swift_nio
import syncplaymobile.shared.generated.resources.setting_network_engine_title
import syncplaymobile.shared.generated.resources.setting_never_show_tips_summary
import syncplaymobile.shared.generated.resources.setting_never_show_tips_title
import syncplaymobile.shared.generated.resources.setting_pause_if_someone_left_summary
import syncplaymobile.shared.generated.resources.setting_pause_if_someone_left_title
import syncplaymobile.shared.generated.resources.setting_playback_buffer_summary
import syncplaymobile.shared.generated.resources.setting_playback_buffer_title
import syncplaymobile.shared.generated.resources.setting_ready_firsthand_summary
import syncplaymobile.shared.generated.resources.setting_ready_firsthand_title
import syncplaymobile.shared.generated.resources.setting_remember_join_info_summary
import syncplaymobile.shared.generated.resources.setting_remember_join_info_title
import syncplaymobile.shared.generated.resources.setting_resetdefault_dialog
import syncplaymobile.shared.generated.resources.setting_resetdefault_summary
import syncplaymobile.shared.generated.resources.setting_resetdefault_title
import syncplaymobile.shared.generated.resources.setting_tls_summary
import syncplaymobile.shared.generated.resources.setting_tls_title
import syncplaymobile.shared.generated.resources.setting_warn_file_mismatch_summary
import syncplaymobile.shared.generated.resources.setting_warn_file_mismatch_title
import syncplaymobile.shared.generated.resources.ui_setting_mpv_debug_summary
import syncplaymobile.shared.generated.resources.ui_setting_mpv_debug_title
import syncplaymobile.shared.generated.resources.ui_setting_mpv_interpolation_summary
import syncplaymobile.shared.generated.resources.ui_setting_mpv_interpolation_title
import syncplaymobile.shared.generated.resources.ui_setting_mpv_profile_summary
import syncplaymobile.shared.generated.resources.ui_setting_mpv_profile_title
import syncplaymobile.shared.generated.resources.ui_setting_mpv_vidsync_summary
import syncplaymobile.shared.generated.resources.ui_setting_mpv_vidsync_title
import syncplaymobile.shared.generated.resources.uisetting_audio_delay_summary
import syncplaymobile.shared.generated.resources.uisetting_audio_delay_title
import syncplaymobile.shared.generated.resources.uisetting_custom_seek_amount_summary
import syncplaymobile.shared.generated.resources.uisetting_custom_seek_amount_title
import syncplaymobile.shared.generated.resources.uisetting_custom_seek_front_summary
import syncplaymobile.shared.generated.resources.uisetting_custom_seek_front_title
import syncplaymobile.shared.generated.resources.uisetting_error_color_summary
import syncplaymobile.shared.generated.resources.uisetting_error_color_title
import syncplaymobile.shared.generated.resources.uisetting_friend_color_summary
import syncplaymobile.shared.generated.resources.uisetting_friend_color_title
import syncplaymobile.shared.generated.resources.uisetting_human_color_summary
import syncplaymobile.shared.generated.resources.uisetting_human_color_title
import syncplaymobile.shared.generated.resources.uisetting_messagery_alpha_summary
import syncplaymobile.shared.generated.resources.uisetting_messagery_alpha_title
import syncplaymobile.shared.generated.resources.uisetting_mpv_gpunext_summary
import syncplaymobile.shared.generated.resources.uisetting_mpv_gpunext_title
import syncplaymobile.shared.generated.resources.uisetting_mpv_hardware_acceleration_summary
import syncplaymobile.shared.generated.resources.uisetting_mpv_hardware_acceleration_title
import syncplaymobile.shared.generated.resources.uisetting_msgboxaction_summary
import syncplaymobile.shared.generated.resources.uisetting_msgboxaction_title
import syncplaymobile.shared.generated.resources.uisetting_msgcount_summary
import syncplaymobile.shared.generated.resources.uisetting_msgcount_title
import syncplaymobile.shared.generated.resources.uisetting_msglife_summary
import syncplaymobile.shared.generated.resources.uisetting_msglife_title
import syncplaymobile.shared.generated.resources.uisetting_msgoutline_summary
import syncplaymobile.shared.generated.resources.uisetting_msgoutline_title
import syncplaymobile.shared.generated.resources.uisetting_msgshadow_summary
import syncplaymobile.shared.generated.resources.uisetting_msgshadow_title
import syncplaymobile.shared.generated.resources.uisetting_msgsize_summary
import syncplaymobile.shared.generated.resources.uisetting_msgsize_title
import syncplaymobile.shared.generated.resources.uisetting_reconnect_interval_summary
import syncplaymobile.shared.generated.resources.uisetting_reconnect_interval_title
import syncplaymobile.shared.generated.resources.uisetting_resetdefault_summary
import syncplaymobile.shared.generated.resources.uisetting_resetdefault_title
import syncplaymobile.shared.generated.resources.uisetting_seek_backward_jump_summary
import syncplaymobile.shared.generated.resources.uisetting_seek_backward_jump_title
import syncplaymobile.shared.generated.resources.uisetting_seek_forward_jump_summary
import syncplaymobile.shared.generated.resources.uisetting_seek_forward_jump_title
import syncplaymobile.shared.generated.resources.uisetting_self_color_summary
import syncplaymobile.shared.generated.resources.uisetting_self_color_title
import syncplaymobile.shared.generated.resources.uisetting_subtitle_delay_summary
import syncplaymobile.shared.generated.resources.uisetting_subtitle_delay_title
import syncplaymobile.shared.generated.resources.uisetting_subtitle_size_summary
import syncplaymobile.shared.generated.resources.uisetting_subtitle_size_title
import syncplaymobile.shared.generated.resources.uisetting_system_color_summary
import syncplaymobile.shared.generated.resources.uisetting_system_color_title
import syncplaymobile.shared.generated.resources.uisetting_timestamp_color_title
import syncplaymobile.shared.generated.resources.uisetting_timestamp_summary
import syncplaymobile.shared.generated.resources.uisetting_timestamp_title

/**
 * Centralized preference definitions with type safety
 */
object Preferences {
    const val SYNCPLAY_PREFS = "syncplayprefs.preferences_pb"

    /** ------------ Miscellaneous -------------*/
    val JOIN_CONFIG = Pref<String?>("misc_join_config", null)

    val PLAYER_ENGINE = Pref("misc_player_engine", availablePlatformVideoEngines.first { it.isDefault }.name)
    val GESTURES = Pref("misc_gestures", true)
    val CURRENT_THEME = Pref("misc_current_theme", defaultTheme.asString())
    val CUSTOM_THEMES = Pref<Set<String>>("misc_custom_themes", emptySet())
    //val ROOM_ORIENTATION = PreferenceDef("misc_room_orientation", "auto")

    /** ------------ General -------------*/
    val REMEMBER_INFO = Pref("pref_remember_info", true) {
        title = Res.string.setting_remember_join_info_title
        summary = Res.string.setting_remember_join_info_summary
        icon = Icons.Filled.Face
    }
    val NEVER_SHOW_TIPS = Pref("pref_never_show_tips", false) {
        title = Res.string.setting_never_show_tips_title
        summary = Res.string.setting_never_show_tips_summary
        icon = Icons.Filled.Lightbulb
    }
    val ERASE_SHORTCUTS = Pref("pref_erase_shortcuts", "") {
        title = Res.string.setting_erase_shortcuts_title
        summary = Res.string.setting_erase_shortcuts_summary
        icon = Icons.Filled.BookmarkRemove

        extraConfig = PrefExtraConfig.YesNoDialog(
            rationale = Res.string.setting_erase_shortcuts_dialog,
            onYes = {
                platformCallback.onEraseConfigShortcuts()
            }
        )
    }
    val MEDIA_DIRECTORIES = Pref<Set<String>>("pref_syncplay_media_directories", emptySet()) {
        title = Res.string.media_directories
        summary = Res.string.media_directories_setting_summary
        icon = Icons.AutoMirrored.Filled.QueueMusic

        extraConfig = PrefExtraConfig.ShowComposable(
            composable = { MediaDirsPopup(this) }
        )
    }

    /** ------------ Language -------------*/
    val DISPLAY_LANG = Pref("pref_lang", "en") {
        title = Res.string.setting_display_language_title
        summary = Res.string.setting_display_language_summry
        icon = Icons.Filled.Translate

        extraConfig = if (platform == PLATFORM.Android) {
            PrefExtraConfig.MultiChoice(
                entries = {
                    val langNames = stringArrayResource(Res.array.language_names)
                    val langCodes = stringArrayResource(Res.array.language_codes)
                    langNames.zip(langCodes).toMap()
                },
                onItemChosen = { v ->
                    platformCallback.onLanguageChanged(v)
                }
            )
        } else {
            PrefExtraConfig.PerformAction(
                onClick = {
                    platformCallback.onLanguageChanged("")
                }
            )
        }
    }
    val AUDIO_LANG = Pref("pref_audio_preferred_lang", "eng") {
        title = Res.string.setting_audio_default_language_title
        summary = Res.string.setting_audio_default_language_summry
        icon = Icons.Filled.SpatialAudio
    }
    val CC_LANG = Pref("pref_cc_preferred_lang", "eng") {
        title = Res.string.setting_cc_default_language_title
        summary = Res.string.setting_cc_default_language_summry
        icon = Icons.Filled.ClosedCaptionOff
    }

    /** ------------ Syncing -------------*/
    val READY_FIRST_HAND = Pref("pref_ready_first_hand", true) {
        title = Res.string.setting_ready_firsthand_title
        summary = Res.string.setting_ready_firsthand_summary
        icon = Icons.Filled.TaskAlt
    }
    val PAUSE_ON_SOMEONE_LEAVE = Pref("pref_pause_if_someone_left", false) {
        title = Res.string.setting_pause_if_someone_left_title
        summary = Res.string.setting_pause_if_someone_left_summary
        icon = Icons.Filled.FrontHand
    }
    val FILE_MISMATCH_WARNING = Pref("pref_file_mismatch_warning", true) {
        title = Res.string.setting_warn_file_mismatch_title
        summary = Res.string.setting_warn_file_mismatch_summary
        icon = Icons.Filled.ErrorOutline
    }
    val HASH_FILENAME = Pref("pref_hash_filename", "1") {
        title = Res.string.setting_fileinfo_behaviour_name_title
        summary = Res.string.setting_fileinfo_behaviour_name_summary
        icon = Icons.Filled.DesignServices

        extraConfig = PrefExtraConfig.MultiChoice(
            entries = {
                mapOf(
                    stringResource(Res.string.setting_fileinfo_behavior_a) to "1",
                    stringResource(Res.string.setting_fileinfo_behavior_b) to "2",
                    stringResource(Res.string.setting_fileinfo_behavior_c) to "3"
                )
            }
        )
    }
    val HASH_FILESIZE = Pref("pref_hash_filesize", "1") {
        title = Res.string.setting_fileinfo_behaviour_size_title
        summary = Res.string.setting_fileinfo_behaviour_size_summary
        icon = Icons.Filled.DesignServices

        extraConfig = PrefExtraConfig.MultiChoice(
            entries = {
                mapOf(
                    stringResource(Res.string.setting_fileinfo_behavior_a) to "1",
                    stringResource(Res.string.setting_fileinfo_behavior_b) to "2",
                    stringResource(Res.string.setting_fileinfo_behavior_c) to "3"
                )
            }
        )
    }

    /** ------------ Network -------------*/
    val NETWORK_ENGINE = Pref("pref_network_engine", if (platform == PLATFORM.Android) "netty" else "swiftnio") {
        title = Res.string.setting_network_engine_title
        summary = Res.string.setting_network_engine_summary
        icon = Icons.Filled.Lan

        extraConfig = PrefExtraConfig.MultiChoice(
            entries = {
                buildMap {
                    if (platform == PLATFORM.Android) {
                        put(stringResource(Res.string.setting_network_engine_netty), "netty")
                    } else {
                        put(stringResource(Res.string.setting_network_engine_swift_nio), "swiftnio")
                    }

                    put(stringResource(Res.string.setting_network_engine_ktor), "ktor")
                }
            }
        )
    }
    val TLS_ENABLE = Pref("pref_tls", true) {
        title = Res.string.setting_tls_title
        summary = Res.string.setting_tls_summary
        icon = Icons.Filled.Key
    }

    /** ------------ Chat Colors -------------*/
    val COLOR_TIMESTAMP = Pref("pref_inroom_color_timestamp", Theming.MSG_TIMESTAMP.toArgb()) {
        title = Res.string.uisetting_timestamp_color_title
        summary = Res.string.uisetting_timestamp_summary
        icon = Icons.Filled.Brush
        extraConfig = PrefExtraConfig.ColorPick
    }
    val COLOR_SELFTAG = Pref("pref_inroom_color_selftag", Theming.MSG_SELF_TAG.toArgb()) {
        title = Res.string.uisetting_self_color_title
        summary = Res.string.uisetting_self_color_summary
        icon = Icons.Filled.Brush
        extraConfig = PrefExtraConfig.ColorPick
    }
    val COLOR_FRIENDTAG = Pref("pref_inroom_color_friendtag", Theming.MSG_FRIEND_TAG.toArgb()) {
        title = Res.string.uisetting_friend_color_title
        summary = Res.string.uisetting_friend_color_summary
        icon = Icons.Filled.Brush
        extraConfig = PrefExtraConfig.ColorPick
    }
    val COLOR_SYSTEMMSG = Pref("pref_inroom_color_systemmsg", Theming.MSG_SYSTEM.toArgb()) {
        title = Res.string.uisetting_system_color_title
        summary = Res.string.uisetting_system_color_summary
        icon = Icons.Filled.Brush
        extraConfig = PrefExtraConfig.ColorPick
    }
    val COLOR_USERMSG = Pref("pref_inroom_color_usermsg", Theming.MSG_CHAT.toArgb()) {
        title = Res.string.uisetting_human_color_title
        summary = Res.string.uisetting_human_color_summary
        icon = Icons.Filled.Brush
        extraConfig = PrefExtraConfig.ColorPick
    }
    val COLOR_ERRORMSG = Pref("pref_inroom_color_errormsg", Theming.MSG_ERROR.toArgb()) {
        title = Res.string.uisetting_error_color_title
        summary = Res.string.uisetting_error_color_summary
        icon = Icons.Filled.Brush
        extraConfig = PrefExtraConfig.ColorPick
    }

    /** ------------ Chat Properties -------------*/
    val MSG_ACTIVATE_STAMP = Pref("pref_inroom_msg_activate_stamp", true) {
        title = Res.string.uisetting_timestamp_title
        summary = Res.string.uisetting_timestamp_summary
        icon = Icons.Filled.Pin
    }
    val MSG_OUTLINE = Pref("pref_inroom_msg_outline", true) {
        title = Res.string.uisetting_msgoutline_title
        summary = Res.string.uisetting_msgoutline_summary
        icon = Icons.Filled.BorderColor
    }
    val MSG_SHADOW = Pref("pref_inroom_msg_shadow", false) {
        title = Res.string.uisetting_msgshadow_title
        summary = Res.string.uisetting_msgshadow_summary
        icon = Icons.Filled.BorderColor
    }
    val MSG_BG_OPACITY = Pref("pref_inroom_msg_bg_opacity", 0) {
        title = Res.string.uisetting_messagery_alpha_title
        summary = Res.string.uisetting_messagery_alpha_summary
        icon = Icons.Filled.Opacity

        extraConfig = PrefExtraConfig.Slider(maxValue = 255, minValue = 0)
    }
    val MSG_FONTSIZE = Pref("pref_inroom_msg_fontsize", 9) {
        title = Res.string.uisetting_msgsize_title
        summary = Res.string.uisetting_msgsize_summary
        icon = Icons.Filled.FormatSize

        extraConfig = PrefExtraConfig.Slider(maxValue = 28, minValue = 6)
    }
    val MSG_MAXCOUNT = Pref("pref_inroom_msg_maxcount", 10) {
        title = Res.string.uisetting_msgcount_title
        summary = Res.string.uisetting_msgcount_summary
        icon = Icons.Filled.FormatListNumbered

        extraConfig = PrefExtraConfig.Slider(maxValue = 30, minValue = 1)
    }
    val MSG_FADING_DURATION = Pref("pref_inroom_fading_msg_duration", 3) {
        title = Res.string.uisetting_msglife_title
        summary = Res.string.uisetting_msglife_summary
        icon = Icons.Filled.Timer

        extraConfig = PrefExtraConfig.Slider(maxValue = 10, minValue = 1)
    }
    val MSG_BOX_ACTION = Pref("pref_inroom_msg_box_action", true) {
        title = Res.string.uisetting_msgboxaction_title
        summary = Res.string.uisetting_msgboxaction_summary
        icon = Icons.Filled.Keyboard
    }

    /** ------------ Player Settings -------------*/
    val SUBTITLE_SIZE = Pref("pref_inroom_subtitle_size", 16) {
        title = Res.string.uisetting_subtitle_size_title
        summary = Res.string.uisetting_subtitle_size_summary
        icon = Icons.Filled.SortByAlpha

        extraConfig = PrefExtraConfig.Slider(
            maxValue = 200, minValue = 2,
            onValueChanged = { v ->
                roomWeakRef?.get()?.player?.changeSubtitleSize(v)
            }
        )
    }

    val AUDIO_DELAY = Pref("pref_inroom_audio_delay", 0) {
        title = Res.string.uisetting_audio_delay_title
        summary = Res.string.uisetting_audio_delay_summary
        icon = Icons.AutoMirrored.Filled.CompareArrows

        extraConfig = PrefExtraConfig.Slider(maxValue = 120_000, minValue = -120_000)
    }
    val SUBTITLE_DELAY = Pref("pref_inroom_subtitle_delay", 0) {
        title = Res.string.uisetting_subtitle_delay_title
        summary = Res.string.uisetting_subtitle_delay_summary
        icon = Icons.AutoMirrored.Filled.CompareArrows

        extraConfig = PrefExtraConfig.Slider(maxValue = 120_000, minValue = -120_000)
    }

    val CUSTOM_SEEK_AMOUNT = Pref("pref_inroom_custom_seek_amount", 90) {
        title = Res.string.uisetting_custom_seek_amount_title
        summary = Res.string.uisetting_custom_seek_amount_summary
        icon = Icons.Filled.Update

        extraConfig = PrefExtraConfig.Slider(maxValue = 300, minValue = 30)
    }
    val CUSTOM_SEEK_FRONT = Pref("pref_inroom_custom_seek_front", true) {
        title = Res.string.uisetting_custom_seek_front_title
        summary = Res.string.uisetting_custom_seek_front_summary
        icon = Icons.Filled.Update

    }
    val SEEK_FORWARD_JUMP = Pref("pref_inroom_seek_forward_jump", 10) {
        title = Res.string.uisetting_seek_forward_jump_title
        summary = Res.string.uisetting_seek_forward_jump_summary
        icon = Icons.Filled.FastForward

        extraConfig = PrefExtraConfig.Slider(maxValue = 120, minValue = 1)

    }
    val SEEK_BACKWARD_JUMP = Pref("pref_inroom_seek_backward_jump", 10) {
        title = Res.string.uisetting_seek_backward_jump_title
        summary = Res.string.uisetting_seek_backward_jump_summary
        icon = Icons.Filled.FastRewind

        extraConfig = PrefExtraConfig.Slider(maxValue = 120, minValue = 1)
    }

    /** ------------ MPV Settings -------------*/
    val MPV_HARDWARE_ACCELERATION = Pref("pref_mpv_hw", true) {
        title = Res.string.uisetting_mpv_hardware_acceleration_title
        summary = Res.string.uisetting_mpv_hardware_acceleration_summary
        icon = Icons.Filled.Speed
    }
    val MPV_GPU_NEXT = Pref("pref_mpv_gpunext", true) {
        title = Res.string.uisetting_mpv_gpunext_title
        summary = Res.string.uisetting_mpv_gpunext_summary
        icon = Icons.Filled.Memory
    }
    val MPV_DEBUG_MODE = Pref("pref_mpv_debug_mode", 0) {
        title = Res.string.ui_setting_mpv_debug_title
        summary = Res.string.ui_setting_mpv_debug_summary
        icon = Icons.Filled.Adb
    }
    val MPV_VIDSYNC = Pref("pref_mpv_video_sync", "audio") {
        title = Res.string.ui_setting_mpv_vidsync_title
        summary = Res.string.ui_setting_mpv_vidsync_summary
        icon = Icons.Filled.SlowMotionVideo
    }
    val MPV_PROFILE = Pref("pref_mpv_profile", "fast") {
        title = Res.string.ui_setting_mpv_profile_title
        summary = Res.string.ui_setting_mpv_profile_summary
        icon = Icons.Filled.SupervisedUserCircle
    }
    val MPV_INTERPOLATION = Pref("pref_mpv_interpolation", false) {
        title = Res.string.ui_setting_mpv_interpolation_title
        summary = Res.string.ui_setting_mpv_interpolation_summary
        icon = Icons.Filled.Animation
    }

    /** ------------ ExoPlayer Settings -------------*/
    val EXO_MAX_BUFFER = Pref("pref_max_buffer_size", 30) {
        title = Res.string.setting_max_buffer_title
        summary = Res.string.setting_max_buffer_summary
        icon = Icons.Filled.HourglassTop

        extraConfig = PrefExtraConfig.Slider(maxValue = 60, minValue = 1)
    }
    val EXO_MIN_BUFFER = Pref("pref_min_buffer_size", 15) {
        title = Res.string.setting_min_buffer_title
        summary = Res.string.setting_min_buffer_summary
        icon = Icons.Filled.HourglassBottom

        extraConfig = PrefExtraConfig.Slider(maxValue = 30, minValue = 1)
    }
    val EXO_SEEK_BUFFER = Pref("pref_seek_buffer_size", 5000) {
        title = Res.string.setting_playback_buffer_title
        summary = Res.string.setting_playback_buffer_summary
        icon = Icons.Filled.HourglassEmpty

        extraConfig = PrefExtraConfig.Slider(maxValue = 15000, minValue = 100)
    }

    /** ------------ Advanced -------------*/
    val RECONNECTION_INTERVAL = Pref("pref_inroom_reconnection_interval", 2) {
        title = Res.string.uisetting_reconnect_interval_title
        summary = Res.string.uisetting_reconnect_interval_summary
        icon = Icons.Filled.Web

        extraConfig = PrefExtraConfig.Slider(maxValue = 15, minValue = 0)
    }

    val GLOBAL_RESET_DEFAULTS = Pref("global_reset_defaults", "") {
        title = Res.string.setting_resetdefault_title
        summary = Res.string.setting_resetdefault_summary
        icon = Icons.Filled.ClearAll

        extraConfig = PrefExtraConfig.YesNoDialog(
            rationale = Res.string.setting_resetdefault_dialog,
            onYes = {
                datastore.edit { preferences ->
                    preferences.clear()
                }
            }
        )
    }

    val INROOM_RESET_DEFAULTS = Pref("inroom_reset_defaults", "") {
        title = Res.string.uisetting_resetdefault_title
        summary = Res.string.uisetting_resetdefault_summary
        icon = Icons.Filled.ClearAll

        extraConfig = PrefExtraConfig.YesNoDialog(
            rationale = Res.string.setting_resetdefault_dialog,
            onYes = {
                datastore.edit { preferences ->
                    preferences.clear()
                }
            }
        )
    }

    val EXPORT_LOGS = Pref<String>("log_saver", "") {
        title = Res.string.setting_export_log_title
        summary = Res.string.setting_export_log_summary
        icon = Icons.Filled.LogoDev

        extraConfig = PrefExtraConfig.ShowComposable(
            composable = {
                val logSaver = rememberFileSaverLauncher { directoryUri ->
                    if (directoryUri == null) return@rememberFileSaverLauncher
                }

                LaunchedEffect(null) {
                    logSaver.launch(
                        bytes = logFile,
                        baseName = "SyncplayLog_${generateTimestampMillis()}",
                        extension = "txt"
                    )
                }
            }
        )
    }
}