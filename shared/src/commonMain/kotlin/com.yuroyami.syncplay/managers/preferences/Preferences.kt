package com.yuroyami.syncplay.managers.preferences

import androidx.compose.ui.graphics.toArgb
import com.yuroyami.syncplay.ui.theme.Theming
import com.yuroyami.syncplay.utils.PLATFORM
import com.yuroyami.syncplay.utils.platform

/**
 * Centralized preference definitions with type safety and default values.
 * 
 * Usage:
 * ```
 * // Read without specifying default
 * val lang = Preferences.DISPLAY_LANG.get()
 * 
 * // Observe in Composable
 * val theme by Preferences.CURRENT_THEME.asState()
 * 
 * // Write
 * Preferences.DISPLAY_LANG.set("fr")
 * ```
 */
object Preferences {
    const val SYNCPLAY_PREFS = "syncplayprefs.preferences_pb"

    /** ------------ Miscellaneous -------------*/
    val JOIN_CONFIG = StaticPref("misc_join_config", "")
    val PLAYER_ENGINE = DynamicPref<String>("misc_player_engine")
    val GESTURES = StaticPref("misc_gestures", true)
    val CURRENT_THEME = StaticPref("misc_current_theme", "dark")
    val ALL_THEMES = StaticPref<Set<String>>("misc_all_themes", emptySet())
    val ROOM_ORIENTATION = StaticPref("misc_room_orientation", "auto")

    /** ------------ General -------------*/
    val REMEMBER_INFO = StaticPref("pref_remember_info", true)
    val NEVER_SHOW_TIPS = StaticPref("pref_never_show_tips", false)
    val ERASE_SHORTCUTS = StaticPref("pref_erase_shortcuts", true)
    val MEDIA_DIRECTORIES = StaticPref<Set<String>>("pref_syncplay_media_directories", emptySet())

    /** ------------ Language -------------*/
    val DISPLAY_LANG = StaticPref("pref_lang", "en")
    val AUDIO_LANG = StaticPref("pref_audio_preferred_lang", "eng")
    val CC_LANG = StaticPref("pref_cc_preferred_lang", "eng")

    /** ------------ Syncing -------------*/
    val READY_FIRST_HAND = StaticPref("pref_ready_first_hand", true)
    val PAUSE_ON_SOMEONE_LEAVE = StaticPref("pref_pause_if_someone_left", false)
    val FILE_MISMATCH_WARNING = StaticPref("pref_file_mismatch_warning", true)
    val HASH_FILENAME = StaticPref("pref_hash_filename", "1")
    val HASH_FILESIZE = StaticPref("pref_hash_filesize", "1")

    /** ------------ Network -------------*/
    val NETWORK_ENGINE = StaticPref("pref_network_engine", if (platform == PLATFORM.Android) "netty" else "swiftnio")
    val TLS_ENABLE = StaticPref("pref_tls", true)

    /** ------------ Chat Colors -------------*/
    val COLOR_TIMESTAMP = StaticPref("pref_inroom_color_timestamp", Theming.MSG_TIMESTAMP.toArgb())
    val COLOR_SELFTAG = StaticPref("pref_inroom_color_selftag", Theming.MSG_SELF_TAG.toArgb())
    val COLOR_FRIENDTAG = StaticPref("pref_inroom_color_friendtag", Theming.MSG_FRIEND_TAG.toArgb())
    val COLOR_SYSTEMMSG = StaticPref("pref_inroom_color_systemmsg", Theming.MSG_SYSTEM.toArgb())
    val COLOR_USERMSG = StaticPref("pref_inroom_color_usermsg", Theming.MSG_CHAT.toArgb())
    val COLOR_ERRORMSG = StaticPref("pref_inroom_color_errormsg", Theming.MSG_ERROR.toArgb())

    /** ------------ Chat Properties -------------*/
    val MSG_ACTIVATE_STAMP = StaticPref("pref_inroom_msg_activate_stamp", true)
    val MSG_OUTLINE = StaticPref("pref_inroom_msg_outline", true)
    val MSG_SHADOW = StaticPref("pref_inroom_msg_shadow", false)
    val MSG_BG_OPACITY = StaticPref("pref_inroom_msg_bg_opacity", 0)
    val MSG_FONTSIZE = StaticPref("pref_inroom_msg_fontsize", 9)
    val MSG_MAXCOUNT = StaticPref("pref_inroom_msg_maxcount", 10)
    val MSG_FADING_DURATION = StaticPref("pref_inroom_fading_msg_duration", 3)
    val MSG_BOX_ACTION = StaticPref("pref_inroom_msg_box_action", true)

    /** ------------ Player Settings -------------*/
    val SUBTITLE_SIZE = StaticPref("pref_inroom_subtitle_size", 16)
    val AUDIO_DELAY = StaticPref("pref_inroom_audio_delay", 0)
    val SUBTITLE_DELAY = StaticPref("pref_inroom_subtitle_delay", 0)
    val CUSTOM_SEEK_AMOUNT = StaticPref("pref_inroom_custom_seek_amount", 90)
    val CUSTOM_SEEK_FRONT = StaticPref("pref_inroom_custom_seek_front", true)
    val SEEK_FORWARD_JUMP = StaticPref("pref_inroom_seek_forward_jump", 10)
    val SEEK_BACKWARD_JUMP = StaticPref("pref_inroom_seek_backward_jump", 10)

    /** ------------ MPV Settings -------------*/
    val MPV_HARDWARE_ACCELERATION = StaticPref("pref_mpv_hw", "auto")
    val MPV_GPU_NEXT = StaticPref("pref_mpv_gpunext", false)
    val MPV_DEBUG_MODE = StaticPref("pref_mpv_debug_mode", false)
    val MPV_VIDSYNC = StaticPref("pref_mpv_video_sync", "audio")
    val MPV_PROFILE = StaticPref("pref_mpv_profile", "default")
    val MPV_INTERPOLATION = StaticPref("pref_mpv_interpolation", false)

    /** ------------ ExoPlayer Settings -------------*/
    val EXO_MAX_BUFFER = StaticPref("pref_max_buffer_size", 50000)
    val EXO_MIN_BUFFER = StaticPref("pref_min_buffer_size", 15000)
    val EXO_SEEK_BUFFER = StaticPref("pref_seek_buffer_size", 5000)

    /** ------------ Advanced -------------*/
    val PIP_ENABLE = StaticPref("pref_inroom_pip", true)
    val RECONNECTION_INTERVAL = StaticPref("pref_inroom_reconnection_interval", 2)
    val PERFORMANCE_UI_MODE = StaticPref("pref_inroom_performance_ui_mode", false)
}