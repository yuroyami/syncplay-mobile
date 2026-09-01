package app.preferences

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
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LogoDev
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.SpatialAudio
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SupervisedUserCircle
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Web
import androidx.compose.material.icons.filled.BlurOff
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.preferences.core.edit
import app.theme.Theming
import app.theme.defaultTheme
import app.preferences.settings.ChatColorsPopup
import app.preferences.settings.TrustedDomainsPopup
import app.uicomponents.PopupMediaDirs.MediaDirsPopup
import app.utils.Platform
import app.utils.appName
import app.utils.availablePlatformPlayerEngines
import app.utils.generateTimestampMillis
import app.utils.get
import app.utils.clearLogs
import app.utils.getMpvConfFilePath
import app.utils.loggy
import app.utils.logFile
import app.utils.readFileBytes
import app.utils.writeFileBytes
import app.utils.platform
import app.utils.platformCallback
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.setting_reduce_motion_summary
import syncplaymobile.shared.generated.resources.setting_reduce_motion_title
import syncplaymobile.shared.generated.resources.server_host_disable_ready
import syncplaymobile.shared.generated.resources.server_host_disable_chat
import syncplaymobile.shared.generated.resources.server_host_isolate_rooms
import syncplaymobile.shared.generated.resources.server_host_motd
import syncplaymobile.shared.generated.resources.server_host_password_detail
import syncplaymobile.shared.generated.resources.server_host_password
import syncplaymobile.shared.generated.resources.server_host_port
import syncplaymobile.shared.generated.resources.room_hud_auto_hide_summary
import syncplaymobile.shared.generated.resources.room_hud_auto_hide_title
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
import syncplaymobile.shared.generated.resources.setting_clear_logs_dialog
import syncplaymobile.shared.generated.resources.setting_clear_logs_summary
import syncplaymobile.shared.generated.resources.setting_clear_logs_title
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
import syncplaymobile.shared.generated.resources.setting_media_resolver_summary
import syncplaymobile.shared.generated.resources.setting_disable_glass_title
import syncplaymobile.shared.generated.resources.setting_disable_glass_summary
import syncplaymobile.shared.generated.resources.setting_media_resolver_title
import syncplaymobile.shared.generated.resources.setting_min_buffer_summary
import syncplaymobile.shared.generated.resources.setting_min_buffer_title
import syncplaymobile.shared.generated.resources.setting_network_engine_ktor
import syncplaymobile.shared.generated.resources.setting_network_engine_netty
import syncplaymobile.shared.generated.resources.setting_network_engine_summary
import syncplaymobile.shared.generated.resources.setting_network_engine_swift_nio
import syncplaymobile.shared.generated.resources.setting_network_engine_title
import syncplaymobile.shared.generated.resources.setting_never_show_tips_summary
import syncplaymobile.shared.generated.resources.setting_never_show_tips_title
import syncplaymobile.shared.generated.resources.settings_show_descriptions_summary
import syncplaymobile.shared.generated.resources.settings_show_descriptions_title
import syncplaymobile.shared.generated.resources.settings_haptics_controls_summary
import syncplaymobile.shared.generated.resources.settings_haptics_controls_title
import app.preferences.settings.SETTINGS_GLOBAL
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
import syncplaymobile.shared.generated.resources.setting_trusted_domains_summary
import syncplaymobile.shared.generated.resources.setting_trusted_domains_title
import syncplaymobile.shared.generated.resources.setting_unpause_action_always
import syncplaymobile.shared.generated.resources.setting_unpause_action_if_min_users_ready
import syncplaymobile.shared.generated.resources.setting_unpause_action_if_others_ready
import syncplaymobile.shared.generated.resources.setting_unpause_action_if_ready
import syncplaymobile.shared.generated.resources.setting_unpause_action_summary
import syncplaymobile.shared.generated.resources.setting_unpause_action_title
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
import syncplaymobile.shared.generated.resources.uisetting_categ_chat_colors
import syncplaymobile.shared.generated.resources.uisetting_chat_colors_entry_summary
import syncplaymobile.shared.generated.resources.uisetting_custom_seek_front_summary
import syncplaymobile.shared.generated.resources.uisetting_custom_seek_front_title
import syncplaymobile.shared.generated.resources.uisetting_doubletap_seek_summary
import syncplaymobile.shared.generated.resources.uisetting_doubletap_seek_title
import syncplaymobile.shared.generated.resources.uisetting_error_color_summary
import syncplaymobile.shared.generated.resources.uisetting_error_color_title
import syncplaymobile.shared.generated.resources.uisetting_friend_color_summary
import syncplaymobile.shared.generated.resources.uisetting_friend_color_title
import syncplaymobile.shared.generated.resources.uisetting_haptic_on_chat_summary
import syncplaymobile.shared.generated.resources.uisetting_haptic_on_chat_title
import syncplaymobile.shared.generated.resources.uisetting_haptic_on_connection_summary
import syncplaymobile.shared.generated.resources.uisetting_haptic_on_connection_title
import syncplaymobile.shared.generated.resources.uisetting_haptic_on_joined_summary
import syncplaymobile.shared.generated.resources.uisetting_haptic_on_joined_title
import syncplaymobile.shared.generated.resources.uisetting_haptic_on_left_summary
import syncplaymobile.shared.generated.resources.uisetting_haptic_on_left_title
import syncplaymobile.shared.generated.resources.uisetting_haptic_on_paused_summary
import syncplaymobile.shared.generated.resources.uisetting_haptic_on_paused_title
import syncplaymobile.shared.generated.resources.uisetting_haptic_on_played_summary
import syncplaymobile.shared.generated.resources.uisetting_haptic_on_played_title
import syncplaymobile.shared.generated.resources.uisetting_haptic_on_playlist_summary
import syncplaymobile.shared.generated.resources.uisetting_haptic_on_playlist_title
import syncplaymobile.shared.generated.resources.uisetting_haptic_on_seeked_summary
import syncplaymobile.shared.generated.resources.uisetting_haptic_on_seeked_title
import syncplaymobile.shared.generated.resources.uisetting_human_color_summary
import syncplaymobile.shared.generated.resources.uisetting_human_color_title
import syncplaymobile.shared.generated.resources.uisetting_kite_audio_delay_summary
import syncplaymobile.shared.generated.resources.uisetting_kite_audio_delay_title
import syncplaymobile.shared.generated.resources.uisetting_kite_debug_stats_summary
import syncplaymobile.shared.generated.resources.uisetting_kite_debug_stats_title
import syncplaymobile.shared.generated.resources.uisetting_kite_eq_brightness_summary
import syncplaymobile.shared.generated.resources.uisetting_kite_eq_brightness_title
import syncplaymobile.shared.generated.resources.uisetting_kite_eq_contrast_summary
import syncplaymobile.shared.generated.resources.uisetting_kite_eq_contrast_title
import syncplaymobile.shared.generated.resources.uisetting_kite_eq_hue_summary
import syncplaymobile.shared.generated.resources.uisetting_kite_eq_hue_title
import syncplaymobile.shared.generated.resources.uisetting_kite_eq_saturation_summary
import syncplaymobile.shared.generated.resources.uisetting_kite_eq_saturation_title
import syncplaymobile.shared.generated.resources.uisetting_kite_hw_summary
import syncplaymobile.shared.generated.resources.uisetting_kite_compose_renderer_summary
import syncplaymobile.shared.generated.resources.uisetting_kite_compose_renderer_title
import syncplaymobile.shared.generated.resources.uisetting_kite_hw_title
import syncplaymobile.shared.generated.resources.uisetting_kite_preserve_pitch_summary
import syncplaymobile.shared.generated.resources.uisetting_kite_preserve_pitch_title
import syncplaymobile.shared.generated.resources.uisetting_video_bg_color_summary
import syncplaymobile.shared.generated.resources.uisetting_video_bg_color_title
import syncplaymobile.shared.generated.resources.uisetting_kite_sub_pos_summary
import syncplaymobile.shared.generated.resources.uisetting_kite_sub_pos_title
import syncplaymobile.shared.generated.resources.uisetting_kite_sub_autoselect_summary
import syncplaymobile.shared.generated.resources.uisetting_kite_sub_autoselect_title
import syncplaymobile.shared.generated.resources.uisetting_kite_sub_delay_summary
import syncplaymobile.shared.generated.resources.uisetting_kite_sub_delay_title
import syncplaymobile.shared.generated.resources.uisetting_kite_sub_scale_summary
import syncplaymobile.shared.generated.resources.uisetting_kite_sub_scale_title
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
import syncplaymobile.shared.generated.resources.uisetting_osd_duration_summary
import syncplaymobile.shared.generated.resources.uisetting_osd_duration_title
import syncplaymobile.shared.generated.resources.uisetting_osd_nonoperator_summary
import syncplaymobile.shared.generated.resources.uisetting_osd_nonoperator_title
import syncplaymobile.shared.generated.resources.uisetting_osd_otherroom_summary
import syncplaymobile.shared.generated.resources.uisetting_osd_otherroom_title
import syncplaymobile.shared.generated.resources.uisetting_osd_sameroom_summary
import syncplaymobile.shared.generated.resources.uisetting_osd_sameroom_title
import syncplaymobile.shared.generated.resources.uisetting_osd_slowdown_summary
import syncplaymobile.shared.generated.resources.uisetting_osd_slowdown_title
import syncplaymobile.shared.generated.resources.uisetting_mpv_export_conf_summary
import syncplaymobile.shared.generated.resources.uisetting_mpv_export_conf_title
import syncplaymobile.shared.generated.resources.uisetting_mpv_import_conf_summary
import syncplaymobile.shared.generated.resources.uisetting_mpv_import_conf_title
import syncplaymobile.shared.generated.resources.uisetting_osd_warnings_summary
import syncplaymobile.shared.generated.resources.uisetting_osd_warnings_title
import syncplaymobile.shared.generated.resources.uisetting_reconnect_interval_summary
import syncplaymobile.shared.generated.resources.uisetting_vlc_custom_flags_summary
import syncplaymobile.shared.generated.resources.uisetting_vlc_custom_flags_title
import syncplaymobile.shared.generated.resources.uisetting_reconnect_interval_title
import syncplaymobile.shared.generated.resources.uisetting_resetdefault_summary
import syncplaymobile.shared.generated.resources.uisetting_resetdefault_title
import syncplaymobile.shared.generated.resources.uisetting_seek_backward_jump_summary
import syncplaymobile.shared.generated.resources.uisetting_seek_backward_jump_title
import syncplaymobile.shared.generated.resources.uisetting_seek_forward_jump_summary
import syncplaymobile.shared.generated.resources.uisetting_seek_forward_jump_title
import syncplaymobile.shared.generated.resources.uisetting_self_color_summary
import syncplaymobile.shared.generated.resources.uisetting_self_color_title
import syncplaymobile.shared.generated.resources.uisetting_chapter_dots_clickable_summary
import syncplaymobile.shared.generated.resources.uisetting_chapter_dots_clickable_title
import syncplaymobile.shared.generated.resources.uisetting_show_chapter_dots_summary
import syncplaymobile.shared.generated.resources.uisetting_show_chapter_dots_title
import syncplaymobile.shared.generated.resources.uisetting_subtitle_delay_summary
import syncplaymobile.shared.generated.resources.uisetting_subtitle_delay_title
import syncplaymobile.shared.generated.resources.uisetting_subtitle_size_summary
import syncplaymobile.shared.generated.resources.uisetting_subtitle_size_title
import syncplaymobile.shared.generated.resources.uisetting_swipe_gestures_summary
import syncplaymobile.shared.generated.resources.uisetting_swipe_gestures_title
import syncplaymobile.shared.generated.resources.uisetting_sync_dont_slow_with_me_summary
import syncplaymobile.shared.generated.resources.uisetting_sync_dont_slow_with_me_title
import syncplaymobile.shared.generated.resources.uisetting_sync_fastforward_summary
import syncplaymobile.shared.generated.resources.uisetting_sync_fastforward_title
import syncplaymobile.shared.generated.resources.uisetting_sync_rewind_summary
import syncplaymobile.shared.generated.resources.uisetting_sync_rewind_title
import syncplaymobile.shared.generated.resources.uisetting_sync_slowdown_summary
import syncplaymobile.shared.generated.resources.uisetting_sync_slowdown_title
import syncplaymobile.shared.generated.resources.uisetting_system_color_summary
import syncplaymobile.shared.generated.resources.uisetting_system_color_title
import syncplaymobile.shared.generated.resources.uisetting_timestamp_color_title
import syncplaymobile.shared.generated.resources.uisetting_timestamp_summary
import syncplaymobile.shared.generated.resources.uisetting_timestamp_title
import syncplaymobile.shared.generated.resources.uisetting_ui_opacity_summary
import syncplaymobile.shared.generated.resources.uisetting_ui_opacity_title

/** Common media languages with ISO 639-2 codes for audio/subtitle track selection. */
private val mediaLanguageEntries = mapOf(
    "No preference" to "und",
    "English" to "eng",
    "Spanish" to "spa",
    "French" to "fra",
    "German" to "deu",
    "Italian" to "ita",
    "Portuguese" to "por",
    "Russian" to "rus",
    "Japanese" to "jpn",
    "Korean" to "kor",
    "Chinese" to "zho",
    "Arabic" to "ara",
    "Hindi" to "hin",
    "Turkish" to "tur",
    "Polish" to "pol",
    "Dutch" to "nld",
    "Swedish" to "swe",
    "Norwegian" to "nor",
    "Danish" to "dan",
    "Finnish" to "fin",
    "Hungarian" to "hun",
    "Czech" to "ces",
    "Romanian" to "ron",
    "Greek" to "ell",
    "Hebrew" to "heb",
    "Thai" to "tha",
    "Vietnamese" to "vie",
    "Indonesian" to "ind",
    "Malay" to "msa",
    "Ukrainian" to "ukr",
    "Bulgarian" to "bul",
    "Croatian" to "hrv",
    "Serbian" to "srp",
    "Slovak" to "slk",
    "Slovenian" to "slv",
    "Catalan" to "cat",
    "Filipino" to "fil",
    "Tamil" to "tam",
    "Telugu" to "tel",
    "Bengali" to "ben",
    "Urdu" to "urd",
    "Persian" to "fas",
    "Latvian" to "lav",
    "Lithuanian" to "lit",
    "Estonian" to "est",
    "Icelandic" to "isl",
    "Swahili" to "swa",
)

/**
 * Centralized preference definitions with type safety
 */
object Preferences {
    const val SYNKPLAY_PREFS = "syncplayprefs.preferences_pb"

    /** ------------ Miscellaneous -------------*/
    val USER_ID = Pref<String?>("misc_user_id", null)
    val JOIN_CONFIG = Pref<String?>("misc_join_config", null)
    val PLAYER_ENGINE = Pref("misc_player_engine", availablePlatformPlayerEngines.first { it.isDefault }.name)
    val GESTURES = Pref("misc_gestures", true)
    val CURRENT_THEME = Pref("misc_current_theme", defaultTheme.asString())
    val CUSTOM_THEMES = Pref<Set<String>>("misc_custom_themes", emptySet())
    val KLIPY_FAVORITES = Pref<Set<String>>("misc_klipy_favorites", emptySet())
    /** When true, the "Undo Seek" action skips its confirmation dialog. Set by the dialog's
     * "Always do" button. No SettingConfig, so it never appears in the settings UI. */
    val UNDO_SEEK_NO_CONFIRM = Pref("misc_undo_seek_no_confirm", false)

    /** ------------ General -------------*/
    val REMEMBER_INFO = Pref("pref_remember_info", true) {
        title = Res.string.setting_remember_join_info_title
        summary = Res.string.setting_remember_join_info_summary
        summaryFormatArgs = arrayOf(appName)
        icon = Icons.Filled.Face
    }
    val NEVER_SHOW_TIPS = Pref("pref_never_show_tips", false) {
        title = Res.string.setting_never_show_tips_title
        summary = Res.string.setting_never_show_tips_summary
        icon = Icons.Filled.Lightbulb
    }
    /** Prints every row's explanation under it, for people who liked the old manuals. */
    val SHOW_SETTING_DESCRIPTIONS = Pref("pref_show_setting_descriptions", false) {
        title = Res.string.settings_show_descriptions_title
        summary = Res.string.settings_show_descriptions_summary
        icon = Icons.Filled.Lightbulb
    }
    /** The control haptics (a rocker flip, a seek landing), separate from the room event pulses. */
    /** Forces every transition to a crossfade; the platform setting does the same on its own. */
    /** The desktop window's last size, position and placement, as "x,y,w,h,placement". Never shown as a row. */
    val DESKTOP_WINDOW = Pref("pref_desktop_window", "")

    val REDUCE_MOTION = Pref("pref_reduce_motion", false) {
        title = Res.string.setting_reduce_motion_title
        summary = Res.string.setting_reduce_motion_summary
        icon = Icons.Filled.Timer
    }

    val HAPTICS_ON_CONTROLS = Pref("pref_haptics_on_controls", true) {
        title = Res.string.settings_haptics_controls_title
        summary = Res.string.settings_haptics_controls_summary
        icon = Icons.Filled.Vibration
    }
    val ERASE_SHORTCUTS = Pref("pref_erase_shortcuts", "") {
        title = Res.string.setting_erase_shortcuts_title
        summary = Res.string.setting_erase_shortcuts_summary
        icon = Icons.Filled.BookmarkRemove

        extraConfig = PrefExtraConfig.YesNoDialog(
            rationale = Res.string.setting_erase_shortcuts_dialog,
            destructive = true,
            onYes = {
                platformCallback.onEraseConfigShortcuts()
            }
        )
    }
    val MEDIA_DIRECTORIES = Pref<Set<String>>("pref_syncplay_media_directories", emptySet()) {
        title = Res.string.media_directories
        summary = Res.string.media_directories_setting_summary
        summaryFormatArgs = arrayOf(appName)
        icon = Icons.AutoMirrored.Filled.QueueMusic

        extraConfig = PrefExtraConfig.ShowComposable(
            composable = { MediaDirsPopup(this) }
        )
    }

    /** ------------ Language -------------*/
    val DISPLAY_LANG = Pref("pref_lang", "en") {
        title = Res.string.setting_display_language_title
        summary = Res.string.setting_display_language_summry
        summaryFormatArgs = arrayOf(appName)
        icon = Icons.Filled.Translate

        extraConfig = if (platform == Platform.Android) {
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

        extraConfig = PrefExtraConfig.MultiChoice(
            entries = { mediaLanguageEntries }
        )
    }
    val CC_LANG = Pref("pref_cc_preferred_lang", "eng") {
        title = Res.string.setting_cc_default_language_title
        summary = Res.string.setting_cc_default_language_summry
        icon = Icons.Filled.ClosedCaptionOff

        extraConfig = PrefExtraConfig.MultiChoice(
            entries = { mediaLanguageEntries }
        )
    }

    /**
     * Language used for the OpenSubtitles "download from web" search. Holds an ISO 639-1 (2-letter)
     * code — NOT the 3-letter [mediaLanguageEntries] codes the player track prefs use — because the
     * OpenSubtitles API speaks 2-letter codes. The sentinel "all" means "don't filter by language".
     * Picked inline in the subtitle-search sheet, so this has no settings-UI entry of its own.
     */
    val SUBTITLE_SEARCH_LANG = Pref("pref_subtitle_search_lang", "en")

    /** ------------ Syncing -------------*/
    val READY_FIRST_HAND = Pref("pref_ready_first_hand", true) {
        title = Res.string.setting_ready_firsthand_title
        summary = Res.string.setting_ready_firsthand_summary
        icon = Icons.Filled.TaskAlt
    }
    val UNPAUSE_ACTION = Pref("pref_unpause_action", "IfOthersReady") {
        title = Res.string.setting_unpause_action_title
        summary = Res.string.setting_unpause_action_summary
        icon = Icons.Filled.PlayArrow

        extraConfig = PrefExtraConfig.MultiChoice(
            entries = {
                mapOf(
                    stringResource(Res.string.setting_unpause_action_if_ready) to "IfAlreadyReady",
                    stringResource(Res.string.setting_unpause_action_if_others_ready) to "IfOthersReady",
                    stringResource(Res.string.setting_unpause_action_if_min_users_ready) to "IfMinUsersReady",
                    stringResource(Res.string.setting_unpause_action_always) to "Always"
                )
            }
        )
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
    val NETWORK_ENGINE = Pref("pref_network_engine", if (platform == Platform.IOS) "swiftnio" else "netty") {
        title = Res.string.setting_network_engine_title
        summary = Res.string.setting_network_engine_summary
        icon = Icons.Filled.Lan

        extraConfig = PrefExtraConfig.MultiChoice(
            entries = {
                buildMap {
                    if (platform == Platform.IOS) {
                        put(stringResource(Res.string.setting_network_engine_swift_nio), "swiftnio")
                    } else {
                        // Android and Desktop both run the Netty engine.
                        put(stringResource(Res.string.setting_network_engine_netty), "netty")
                    }

                    put(stringResource(Res.string.setting_network_engine_ktor), "ktor")
                }
            }
        )
    }
    val TLS_ENABLE = Pref("pref_tls", true) {
        title = Res.string.setting_tls_title
        summary = Res.string.setting_tls_summary
        summaryFormatArgs = arrayOf(appName)
        icon = Icons.Filled.Key
    }
    /** When true, page URLs (YouTube, SoundCloud, …) entered as media are run through the
     *  platform's native extractor before reaching the player. A heuristic short-circuits when
     *  the URL is already direct media, so there's no cost in the common case. */
    val MEDIA_RESOLVER_ENABLED = Pref("pref_media_resolver_enabled", true) {
        title = Res.string.setting_media_resolver_title
        summary = Res.string.setting_media_resolver_summary
        icon = Icons.Filled.Language
    }

    /** Master off-switch for the frosted glass system, end to end.
     *
     *  When true: no Haze capture or blur anywhere, panels fall back to a solid tonal surface,
     *  no platform window blur, and the Android players go back to SurfaceView, which can use a
     *  hardware overlay plane (lower power, HDR passthrough) but cannot be captured for blur.
     *  Glass and the overlay fast path are mutually exclusive, so this is one switch, not two.
     *
     *  Surface type is fixed when the player view is inflated, so a change lands on the next
     *  room entry, matching how the other engine options behave. */
    val DISABLE_FROSTED_GLASS = Pref("pref_disable_frosted_glass", false) {
        title = Res.string.setting_disable_glass_title
        summary = Res.string.setting_disable_glass_summary
        icon = Icons.Filled.BlurOff
    }

    /** ------------ Security -------------*/
    val TRUSTED_DOMAINS = Pref("pref_trusted_domains", "") {
        title = Res.string.setting_trusted_domains_title
        summary = Res.string.setting_trusted_domains_summary
        icon = Icons.Filled.Web

        extraConfig = PrefExtraConfig.ShowComposable(
            composable = { TrustedDomainsPopup(this) }
        )
    }

    /** ------------ Sync Mechanisms (In-Room) -------------*/
    val SYNC_FASTFORWARD = Pref("pref_inroom_sync_fastforward", true) {
        title = Res.string.uisetting_sync_fastforward_title
        summary = Res.string.uisetting_sync_fastforward_summary
        icon = Icons.Filled.FastForward
    }
    val SYNC_SLOWDOWN = Pref("pref_inroom_sync_slowdown", true) {
        title = Res.string.uisetting_sync_slowdown_title
        summary = Res.string.uisetting_sync_slowdown_summary
        icon = Icons.Filled.SlowMotionVideo
    }
    val SYNC_REWIND = Pref("pref_inroom_sync_rewind", true) {
        title = Res.string.uisetting_sync_rewind_title
        summary = Res.string.uisetting_sync_rewind_summary
        icon = Icons.Filled.FastRewind
    }
    val SYNC_DONT_SLOW_WITH_ME = Pref("pref_inroom_sync_dont_slow_with_me", false) {
        title = Res.string.uisetting_sync_dont_slow_with_me_title
        summary = Res.string.uisetting_sync_dont_slow_with_me_summary
        icon = Icons.Filled.Speed
    }

    /** ------------ Chat Colors -------------*/
    /** Single entry that opens [app.preferences.settings.ChatColorsPopup] gathering all the
     *  COLOR_* prefs below. The individual color prefs no longer appear as standalone rows. */
    val CHAT_COLORS_ENTRY = Pref("pref_inroom_chat_colors_entry", "") {
        title = Res.string.uisetting_categ_chat_colors
        summary = Res.string.uisetting_chat_colors_entry_summary
        icon = Icons.Filled.Palette

        extraConfig = PrefExtraConfig.ShowComposable(
            composable = { ChatColorsPopup(this) }
        )
    }

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

    /** ------------ Hosted server (persisted so a host does not retype them) ------------ */
    val SERVER_PORT = Pref("pref_server_port", "8999") {
        title = Res.string.server_host_port
        icon = Icons.Filled.Keyboard
        extraConfig = PrefExtraConfig.TextField(keyboardType = 1)
    }
    val SERVER_PASSWORD = Pref("pref_server_password", "") {
        title = Res.string.server_host_password
        summary = Res.string.server_host_password_detail
        icon = Icons.Filled.Keyboard
        extraConfig = PrefExtraConfig.TextField()
    }
    val SERVER_MOTD = Pref("pref_server_motd", "") {
        title = Res.string.server_host_motd
        icon = Icons.Filled.Keyboard
        extraConfig = PrefExtraConfig.TextField()
    }
    val SERVER_ISOLATE_ROOMS = Pref("pref_server_isolate_rooms", true) {
        title = Res.string.server_host_isolate_rooms
        icon = Icons.Filled.Pin
    }
    val SERVER_DISABLE_CHAT = Pref("pref_server_disable_chat", false) {
        title = Res.string.server_host_disable_chat
        icon = Icons.Filled.Pin
    }
    val SERVER_DISABLE_READY = Pref("pref_server_disable_ready", false) {
        title = Res.string.server_host_disable_ready
        icon = Icons.Filled.Pin
    }

    /** ------------ Chat Properties -------------*/
    val MSG_ACTIVATE_STAMP = Pref("pref_inroom_msg_activate_stamp", true) {
        title = Res.string.uisetting_timestamp_title
        summary = Res.string.uisetting_timestamp_summary
        icon = Icons.Filled.Pin
    }
    val MSG_OUTLINE_ACTIVATE = Pref("pref_inroom_msg_outline_activate", true) {
        title = Res.string.uisetting_msgoutline_title
        summary = Res.string.uisetting_msgoutline_summary
        icon = Icons.Filled.BorderColor
    }
    val MSG_OUTLINE_THICKNESS = Pref("pref_inroom_msg_outline_thickness", 2) {
        title = Res.string.uisetting_msgoutline_title
        summary = Res.string.uisetting_msgoutline_summary
        icon = Icons.Filled.BorderColor

        extraConfig = PrefExtraConfig.Slider(maxValue = 30, minValue = 0)
    }
    val MSG_SHADOW_ACTIVATE = Pref("pref_inroom_msg_shadow_activate", false) {
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
    /** 11 to 24; a stored value under 11 is read as 11 (see MessageRow), nothing is rewritten. */
    val MSG_FONTSIZE = Pref("pref_inroom_msg_fontsize", 13) {
        title = Res.string.uisetting_msgsize_title
        summary = Res.string.uisetting_msgsize_summary
        icon = Icons.Filled.FormatSize

        extraConfig = PrefExtraConfig.Slider(maxValue = 24, minValue = 11)
    }
    /** How many recent unseen lines the fading layout shows over the video. */
    val MSG_MAXCOUNT = Pref("pref_inroom_msg_maxcount", 3) {
        title = Res.string.uisetting_msgcount_title
        summary = Res.string.uisetting_msgcount_summary
        icon = Icons.Filled.FormatListNumbered

        extraConfig = PrefExtraConfig.Slider(maxValue = 10, minValue = 1)
    }
    val MSG_FADING_DURATION = Pref("pref_inroom_fading_msg_duration", 3) {
        title = Res.string.uisetting_msglife_title
        summary = Res.string.uisetting_msglife_summary
        icon = Icons.Filled.Timer

        extraConfig = PrefExtraConfig.Slider(maxValue = 10, minValue = 1, unit = "s")
    }
    val MSG_BOX_ACTION = Pref("pref_inroom_msg_box_action", true) {
        title = Res.string.uisetting_msgboxaction_title
        summary = Res.string.uisetting_msgboxaction_summary
        icon = Icons.Filled.Keyboard
    }

    val OSD_DURATION = Pref("pref_inroom_osd_duration", 2) {
        title = Res.string.uisetting_osd_duration_title
        summary = Res.string.uisetting_osd_duration_summary
        icon = Icons.Filled.Timer

        extraConfig = PrefExtraConfig.Slider(maxValue = 10, minValue = 0, unit = "s")
    }

    /** ------------ OSD Notification Filters -------------
     *  Mirrors Syncplay PC's "Messages" tab toggles (showSameRoomOSD / showNonControllerOSD /
     *  showDifferentRoomOSD / showSlowdownOSD / showOSDWarnings). These gate which event-driven
     *  OSD overlays bubble up via [RoomViewmodel.dispatchOSD]. They do NOT affect the chat log. */
    val OSD_SAME_ROOM = Pref("pref_inroom_osd_same_room", true) {
        title = Res.string.uisetting_osd_sameroom_title
        summary = Res.string.uisetting_osd_sameroom_summary
        icon = Icons.Filled.SupervisedUserCircle
    }
    val OSD_NON_OPERATOR = Pref("pref_inroom_osd_non_operator", true) {
        title = Res.string.uisetting_osd_nonoperator_title
        summary = Res.string.uisetting_osd_nonoperator_summary
        icon = Icons.Filled.Face
        dependencyEnable = { OSD_SAME_ROOM.value() }
    }
    /** Default false to match Syncplay PC's SHOW_DIFFERENT_ROOM_OSD = False default. */
    val OSD_OTHER_ROOM = Pref("pref_inroom_osd_other_room", false) {
        title = Res.string.uisetting_osd_otherroom_title
        summary = Res.string.uisetting_osd_otherroom_summary
        icon = Icons.Filled.Web
    }
    val OSD_SLOWDOWN = Pref("pref_inroom_osd_slowdown", true) {
        title = Res.string.uisetting_osd_slowdown_title
        summary = Res.string.uisetting_osd_slowdown_summary
        icon = Icons.Filled.SlowMotionVideo
    }
    val OSD_WARNINGS = Pref("pref_inroom_osd_warnings", true) {
        title = Res.string.uisetting_osd_warnings_title
        summary = Res.string.uisetting_osd_warnings_summary
        icon = Icons.Filled.ErrorOutline
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

        extraConfig = PrefExtraConfig.Slider(maxValue = 120_000, minValue = -120_000, unit = "ms")
    }
    val SUBTITLE_DELAY = Pref("pref_inroom_subtitle_delay", 0) {
        title = Res.string.uisetting_subtitle_delay_title
        summary = Res.string.uisetting_subtitle_delay_summary
        icon = Icons.AutoMirrored.Filled.CompareArrows

        extraConfig = PrefExtraConfig.Slider(maxValue = 120_000, minValue = -120_000, unit = "ms")
    }

    val CUSTOM_SEEK_AMOUNT = Pref("pref_inroom_custom_seek_amount", 90) {
        title = Res.string.uisetting_custom_seek_amount_title
        summary = Res.string.uisetting_custom_seek_amount_summary
        icon = Icons.Filled.Update

        extraConfig = PrefExtraConfig.Slider(maxValue = 300, minValue = 30, unit = "s")
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

        extraConfig = PrefExtraConfig.Slider(maxValue = 120, minValue = 1, unit = "s")

    }
    val SEEK_BACKWARD_JUMP = Pref("pref_inroom_seek_backward_jump", 10) {
        title = Res.string.uisetting_seek_backward_jump_title
        summary = Res.string.uisetting_seek_backward_jump_summary
        icon = Icons.Filled.FastRewind

        extraConfig = PrefExtraConfig.Slider(maxValue = 120, minValue = 1, unit = "s")
    }

    val SHOW_CHAPTER_DOTS = Pref("pref_inroom_show_chapter_dots", true) {
        title = Res.string.uisetting_show_chapter_dots_title
        summary = Res.string.uisetting_show_chapter_dots_summary
        icon = Icons.Filled.FormatListNumbered
    }

    val CHAPTER_DOTS_CLICKABLE = Pref("pref_inroom_chapter_dots_clickable", false) {
        title = Res.string.uisetting_chapter_dots_clickable_title
        summary = Res.string.uisetting_chapter_dots_clickable_summary
        icon = Icons.Filled.TouchApp
        dependencyEnable = { SHOW_CHAPTER_DOTS.value() }
    }

    /** Off by default: double-tap-to-seek fights with tap-to-reveal-HUD for most users. */
    val DOUBLETAP_SEEK = Pref("pref_inroom_doubletap_seek", false) {
        title = Res.string.uisetting_doubletap_seek_title
        summary = Res.string.uisetting_doubletap_seek_summary
        icon = Icons.Filled.TouchApp
    }

    val SWIPE_GESTURES = Pref("pref_inroom_swipe_gestures", true) {
        title = Res.string.uisetting_swipe_gestures_title
        summary = Res.string.uisetting_swipe_gestures_summary
        icon = Icons.Filled.Swipe
    }

    /** The HUD hides itself after a few idle seconds; off keeps it up until tapped away. */
    val HUD_AUTO_HIDE = Pref("pref_inroom_hud_auto_hide", true) {
        title = Res.string.room_hud_auto_hide_title
        summary = Res.string.room_hud_auto_hide_summary
        icon = Icons.Filled.Timer
    }

    /** ------------ KitePlayer Settings -------------*/
    val KITE_COMPOSE_RENDERER = Pref("pref_kite_compose_renderer", false) {
        title = Res.string.uisetting_kite_compose_renderer_title
        summary = Res.string.uisetting_kite_compose_renderer_summary
        icon = Icons.Filled.Layers
    }
    val KITE_HARDWARE_ACCELERATION = Pref("pref_kite_hw", true) {
        title = Res.string.uisetting_kite_hw_title
        summary = Res.string.uisetting_kite_hw_summary
        icon = Icons.Filled.Speed
    }
    val KITE_SUBTITLE_AUTOSELECT = Pref("pref_kite_sub_autoselect", true) {
        title = Res.string.uisetting_kite_sub_autoselect_title
        summary = Res.string.uisetting_kite_sub_autoselect_summary
        icon = Icons.Filled.Subtitles
    }
    val KITE_SUBTITLE_SCALE = Pref("pref_kite_sub_scale", 100) {
        title = Res.string.uisetting_kite_sub_scale_title
        summary = Res.string.uisetting_kite_sub_scale_summary
        icon = Icons.Filled.FormatSize
    }
    val KITE_SUBTITLE_DELAY_MS = Pref("pref_kite_sub_delay_ms", 0) {
        title = Res.string.uisetting_kite_sub_delay_title
        summary = Res.string.uisetting_kite_sub_delay_summary
        icon = Icons.Filled.Timer
    }
    val KITE_AUDIO_DELAY_MS = Pref("pref_kite_audio_delay_ms", 0) {
        title = Res.string.uisetting_kite_audio_delay_title
        summary = Res.string.uisetting_kite_audio_delay_summary
        icon = Icons.Filled.Timer
    }
    val KITE_PRESERVE_PITCH = Pref("pref_kite_preserve_pitch", true) {
        title = Res.string.uisetting_kite_preserve_pitch_title
        summary = Res.string.uisetting_kite_preserve_pitch_summary
        icon = Icons.Filled.MusicNote
    }
    val KITE_SUBTITLE_POS = Pref("pref_kite_sub_pos", 100) {
        title = Res.string.uisetting_kite_sub_pos_title
        summary = Res.string.uisetting_kite_sub_pos_summary
        icon = Icons.Filled.VerticalAlignBottom
    }
    val KITE_EQ_BRIGHTNESS = Pref("pref_kite_eq_brightness", 0) {
        title = Res.string.uisetting_kite_eq_brightness_title
        summary = Res.string.uisetting_kite_eq_brightness_summary
        icon = Icons.Filled.BrightnessMedium
    }
    val KITE_EQ_CONTRAST = Pref("pref_kite_eq_contrast", 100) {
        title = Res.string.uisetting_kite_eq_contrast_title
        summary = Res.string.uisetting_kite_eq_contrast_summary
        icon = Icons.Filled.Contrast
    }
    val KITE_EQ_SATURATION = Pref("pref_kite_eq_saturation", 100) {
        title = Res.string.uisetting_kite_eq_saturation_title
        summary = Res.string.uisetting_kite_eq_saturation_summary
        icon = Icons.Filled.Palette
    }
    val KITE_EQ_HUE = Pref("pref_kite_eq_hue", 0) {
        title = Res.string.uisetting_kite_eq_hue_title
        summary = Res.string.uisetting_kite_eq_hue_summary
        icon = Icons.Filled.Colorize
    }
    val KITE_DEBUG_STATS = Pref("pref_kite_debug_stats", false) {
        title = Res.string.uisetting_kite_debug_stats_title
        summary = Res.string.uisetting_kite_debug_stats_summary
        icon = Icons.Filled.Adb
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

        extraConfig = PrefExtraConfig.Slider(maxValue = 60, minValue = 1, unit = "s")
    }
    val EXO_MIN_BUFFER = Pref("pref_min_buffer_size", 15) {
        title = Res.string.setting_min_buffer_title
        summary = Res.string.setting_min_buffer_summary
        icon = Icons.Filled.HourglassBottom

        extraConfig = PrefExtraConfig.Slider(maxValue = 30, minValue = 1, unit = "s")
    }
    val EXO_SEEK_BUFFER = Pref("pref_seek_buffer_size", 5000) {
        title = Res.string.setting_playback_buffer_title
        summary = Res.string.setting_playback_buffer_summary
        icon = Icons.Filled.HourglassEmpty

        extraConfig = PrefExtraConfig.Slider(maxValue = 15000, minValue = 100, unit = "ms")
    }

    /** ------------ Haptics -------------*/
    val HAPTIC_ON_JOINED = Pref("pref_haptic_on_joined", false) {
        title = Res.string.uisetting_haptic_on_joined_title
        summary = Res.string.uisetting_haptic_on_joined_summary
        icon = Icons.Filled.Vibration
    }
    val HAPTIC_ON_LEFT = Pref("pref_haptic_on_left", true) {
        title = Res.string.uisetting_haptic_on_left_title
        summary = Res.string.uisetting_haptic_on_left_summary
        icon = Icons.Filled.Vibration
    }
    val HAPTIC_ON_CHAT = Pref("pref_haptic_on_chat", true) {
        title = Res.string.uisetting_haptic_on_chat_title
        summary = Res.string.uisetting_haptic_on_chat_summary
        icon = Icons.Filled.Vibration
    }
    val HAPTIC_ON_PAUSED = Pref("pref_haptic_on_paused", false) {
        title = Res.string.uisetting_haptic_on_paused_title
        summary = Res.string.uisetting_haptic_on_paused_summary
        icon = Icons.Filled.Vibration
    }
    val HAPTIC_ON_PLAYED = Pref("pref_haptic_on_played", false) {
        title = Res.string.uisetting_haptic_on_played_title
        summary = Res.string.uisetting_haptic_on_played_summary
        icon = Icons.Filled.Vibration
    }
    val HAPTIC_ON_SEEKED = Pref("pref_haptic_on_seeked", false) {
        title = Res.string.uisetting_haptic_on_seeked_title
        summary = Res.string.uisetting_haptic_on_seeked_summary
        icon = Icons.Filled.Vibration
    }
    val HAPTIC_ON_PLAYLIST = Pref("pref_haptic_on_playlist", false) {
        title = Res.string.uisetting_haptic_on_playlist_title
        summary = Res.string.uisetting_haptic_on_playlist_summary
        icon = Icons.Filled.Vibration
    }
    val HAPTIC_ON_CONNECTION = Pref("pref_haptic_on_connection", false) {
        title = Res.string.uisetting_haptic_on_connection_title
        summary = Res.string.uisetting_haptic_on_connection_summary
        icon = Icons.Filled.Vibration
    }

    /**
     * The colour behind the video picture (the letterbox area). Black by default, because a
     * letterbox that is anything else reads as a rendering bug; picker for whoever disagrees.
     * Opaque ARGB, same storage convention as the chat colour prefs.
     */
    val VIDEO_BACKGROUND_COLOR = Pref("pref_video_background_color", androidx.compose.ui.graphics.Color.Black.toArgb()) {
        title = Res.string.uisetting_video_bg_color_title
        summary = Res.string.uisetting_video_bg_color_summary
        icon = Icons.Filled.Brush
        extraConfig = PrefExtraConfig.ColorPick
    }

    /** ------------ Advanced -------------*/
    val ROOM_UI_OPACITY = Pref("pref_room_ui_opacity", 80) {
        title = Res.string.uisetting_ui_opacity_title
        summary = Res.string.uisetting_ui_opacity_summary
        icon = Icons.Filled.Opacity

        extraConfig = PrefExtraConfig.Slider(maxValue = 100, minValue = 0, unit = "%")
    }

    val RECONNECTION_INTERVAL = Pref("pref_inroom_reconnection_interval", 2) {
        title = Res.string.uisetting_reconnect_interval_title
        summary = Res.string.uisetting_reconnect_interval_summary
        icon = Icons.Filled.Web

        extraConfig = PrefExtraConfig.Slider(maxValue = 15, minValue = 0, unit = "s")
    }

    val GLOBAL_RESET_DEFAULTS: Pref<String> = Pref("global_reset_defaults", "") {
        title = Res.string.setting_resetdefault_title
        summary = Res.string.setting_resetdefault_summary
        icon = Icons.Filled.ClearAll

        extraConfig = PrefExtraConfig.YesNoDialog(
            rationale = Res.string.setting_resetdefault_dialog,
            destructive = true,
            onYes = {
                // Only the global categories' keys. Identity, themes, favourites and the saved
                // join config are not settings and survive a reset.
                datastore.edit { preferences ->
                    SETTINGS_GLOBAL.flatMap { it.settings }.forEach { preferences.remove(it.anyKey) }
                }
            }
        )
    }

    val INROOM_RESET_DEFAULTS: Pref<String> = Pref("inroom_reset_defaults", "") {
        title = Res.string.uisetting_resetdefault_title
        summary = Res.string.uisetting_resetdefault_summary
        icon = Icons.Filled.ClearAll

        extraConfig = PrefExtraConfig.YesNoDialog(
            rationale = Res.string.setting_resetdefault_dialog,
            destructive = true,
            onYes = {
                // Every in-room and engine key, by prefix; nothing else.
                datastore.edit { preferences ->
                    preferences.asMap().keys
                        .filter { key -> IN_ROOM_KEY_PREFIXES.any { key.name.startsWith(it) } || key.name in IN_ROOM_EXTRA_KEYS }
                        .forEach { preferences.remove(it) }
                }
            }
        )
    }

    private val IN_ROOM_KEY_PREFIXES = listOf("pref_inroom_", "pref_kite_", "pref_mpv_", "pref_haptic_", "pref_vlc_", "vlc_")
    private val IN_ROOM_EXTRA_KEYS = setOf(
        "pref_max_buffer_size", "pref_min_buffer_size", "pref_seek_buffer_size",
        "pref_video_background_color", "pref_room_ui_opacity",
    )

    val CLEAR_LOGS = Pref("log_clear", "") {
        title = Res.string.setting_clear_logs_title
        summary = Res.string.setting_clear_logs_summary
        icon = Icons.Filled.ClearAll

        extraConfig = PrefExtraConfig.YesNoDialog(
            rationale = Res.string.setting_clear_logs_dialog,
            destructive = true,
            onYes = { clearLogs() }
        )
    }

    val EXPORT_LOGS = Pref<String>("log_saver", "") {
        title = Res.string.setting_export_log_title
        summary = Res.string.setting_export_log_summary
        icon = Icons.Filled.LogoDev

        extraConfig = PrefExtraConfig.ShowComposable(
            composable = {
                val scope = rememberCoroutineScope { Dispatchers.IO }

                val logSaver = rememberFileSaverLauncher(dialogSettings = FileKitDialogSettings.createDefault()) { file ->
                    scope.launch {
                        file?.write(logFile)
                    }
                }

                LaunchedEffect(null) {
                    logSaver.launch(
                        suggestedName = "${appName}Log_${generateTimestampMillis()}",
                        extension = "txt"
                    )
                }
            }
        )
    }

    /**
     * Extra command-line flags forwarded verbatim to LibVLC on iOS (`VLCLibrary(args)`).
     * Tokenized by [tokenizeVlcFlags], which splits on whitespace but honours `"`/`'` quoted runs
     * so values with spaces work (e.g. `--sub-text-scale="1.5"`). Takes effect on the next VLCKit
     * (re)initialization.
     */
    val VLC_CUSTOM_FLAGS = Pref("pref_vlc_custom_flags", "") {
        title = Res.string.uisetting_vlc_custom_flags_title
        summary = Res.string.uisetting_vlc_custom_flags_summary
        icon = Icons.Filled.Keyboard
        extraConfig = PrefExtraConfig.TextField()
    }

    /**
     * Import an mpv.conf from the user's storage, overwriting the one mpv reads from at
     * `{filesDir}/mpv.conf`. Only shown by the mpv engine's own settings category, so platforms
     * without mpv never see it; there [getMpvConfFilePath] returns null and this is a no-op.
     *
     * The new config takes effect the next time mpv is initialized (e.g. after loading a video
     * fresh) because `MPVLib.setOptionString("config-dir", ...)` is read at init() time.
     */
    val MPV_IMPORT_CONF = Pref<String>("mpv_import_conf", "") {
        title = Res.string.uisetting_mpv_import_conf_title
        summary = Res.string.uisetting_mpv_import_conf_summary
        icon = Icons.Filled.FileUpload

        extraConfig = PrefExtraConfig.ShowComposable(
            composable = {
                val scope = rememberCoroutineScope { Dispatchers.IO }
                val picker = rememberFilePickerLauncher(type = FileKitType.File()) { file ->
                    if (file == null) return@rememberFilePickerLauncher
                    scope.launch {
                        runCatching {
                            val bytes = file.readBytes()
                            val dest = getMpvConfFilePath()
                            if (dest != null) {
                                writeFileBytes(dest, bytes)
                                loggy("mpv.conf imported to $dest (${bytes.size} bytes)")
                            } else {
                                loggy("mpv.conf import: platform does not support a config path.")
                            }
                        }.onFailure {
                            loggy("mpv.conf import failed: ${it.message}")
                        }
                    }
                }
                LaunchedEffect(null) { picker.launch() }
            }
        )
    }

    /**
     * Export the currently active mpv.conf (if any) to a user-chosen location. On Android this
     * reads from `{filesDir}/mpv.conf`; if the file does not exist yet the pref is effectively a
     * no-op (user is informed via logs).
     */
    val MPV_EXPORT_CONF = Pref<String>("mpv_export_conf", "") {
        title = Res.string.uisetting_mpv_export_conf_title
        summary = Res.string.uisetting_mpv_export_conf_summary
        icon = Icons.Filled.FileDownload

        extraConfig = PrefExtraConfig.ShowComposable(
            composable = {
                val scope = rememberCoroutineScope { Dispatchers.IO }
                val saver = rememberFileSaverLauncher(dialogSettings = FileKitDialogSettings.createDefault()) { file ->
                    if (file == null) return@rememberFileSaverLauncher
                    scope.launch {
                        runCatching {
                            val src = getMpvConfFilePath()
                            val bytes = src?.let { readFileBytes(it) }
                            if (bytes != null) {
                                file.write(bytes)
                                loggy("mpv.conf exported (${bytes.size} bytes)")
                            } else {
                                loggy("mpv.conf export: no config file to export yet.")
                            }
                        }.onFailure {
                            loggy("mpv.conf export failed: ${it.message}")
                        }
                    }
                }
                LaunchedEffect(null) {
                    saver.launch(suggestedName = "mpv", extension = "conf")
                }
            }
        )
    }
}