package com.yuroyami.syncplay.logic.managers.datastore

object DataStoreKeys {
    const val SYNCPLAY_PREFS = "syncplayprefs.preferences_pb"

    /** ------------ Joining info -------------*/

    /* Log-in (join) info */
    const val MISC_JOIN_CONFIG = "misc_join_config"
    const val MISC_PLAYER_ENGINE = "misc_player_engine"
    const val MISC_GESTURES = "misc_gestures"

    /* General */
    const val CATEG_GLOBAL_GENERAL = "categ_global_general"
    const val PREF_REMEMBER_INFO = "pref_remember_info"
    const val PREF_ERASE_SHORTCUTS = "pref_erase_shortcuts"
    const val PREF_SP_MEDIA_DIRS = "pref_syncplay_media_directories"

    /* Language */
    const val CATEG_GLOBAL_LANG = "categ_global_lang"
    const val PREF_DISPLAY_LANG = "pref_lang"
    const val PREF_AUDIO_LANG = "pref_audio_preferred_lang"
    const val PREF_CC_LANG = "pref_cc_preferred_lang"

    /* Syncing */
    const val CATEG_GLOBAL_SYNCING = "categ_global_syncing"
    const val PREF_READY_FIRST_HAND = "pref_ready_first_hand"
    const val PREF_PAUSE_ON_SOMEONE_LEAVE = "pref_pause_if_someone_left"
    const val PREF_FILE_MISMATCH_WARNING = "pref_file_mismatch_warning"
    const val PREF_HASH_FILENAME = "pref_hash_filename"
    const val PREF_HASH_FILESIZE = "pref_hash_filesize"

    /* Network */
    const val CATEG_GLOBAL_NETWORK = "categ_global_network"
    const val PREF_NETWORK_ENGINE = "pref_network_engine"
    const val PREF_TLS_ENABLE = "pref_tls"

    /* Advanced */
    const val CATEG_GLOBAL_ADVANCED = "categ_global_advanced"
    const val PREF_GLOBAL_CLEAR_ALL = "pref_global_clear_all"

    /** ---------- In-room Preferences ----------- */
    /* Message Colors */
    const val CATEG_INROOM_CHATCOLORS = "categ_room_chatcolors"
    const val PREF_INROOM_COLOR_TIMESTAMP = "pref_inroom_color_timestamp"
    const val PREF_INROOM_COLOR_SELFTAG = "pref_inroom_color_selftag"
    const val PREF_INROOM_COLOR_FRIENDTAG = "pref_inroom_color_friendtag"
    const val PREF_INROOM_COLOR_SYSTEMMSG = "pref_inroom_color_systemmsg"
    const val PREF_INROOM_COLOR_USERMSG = "pref_inroom_color_usermsg"
    const val PREF_INROOM_COLOR_ERRORMSG = "pref_inroom_color_errormsg"

    /* Message Properties */
    const val CATEG_INROOM_CHATPROPS = "categ_room_chatproperties"
    const val PREF_INROOM_MSG_ACTIVATE_STAMP = "pref_inroom_msg_activate_stamp"
    const val PREF_INROOM_MSG_OUTLINE = "pref_inroom_msg_outline"
    const val PREF_INROOM_MSG_SHADOW = "pref_inroom_msg_shadow"
    const val PREF_INROOM_MSG_BG_OPACITY = "pref_inroom_msg_bg_opacity"
    const val PREF_INROOM_MSG_FONTSIZE = "pref_inroom_msg_fontsize"
    const val PREF_INROOM_MSG_MAXCOUNT = "pref_inroom_msg_maxcount"
    const val PREF_INROOM_MSG_FADING_DURATION = "pref_inroom_fading_msg_duration"
    const val PREF_INROOM_MSG_BOX_ACTION = "pref_inroom_msg_box_action"

    /* Player Settings */
    const val CATEG_INROOM_PLAYERSETTINGS = "categ_inroom_playersettings"
    const val PREF_INROOM_PLAYER_SUBTITLE_SIZE = "pref_inroom_subtitle_size"

    const val PREF_INROOM_PLAYER_AUDIO_DELAY = "pref_inroom_audio_delay"
    const val PREF_INROOM_PLAYER_SUBTITLE_DELAY = "pref_inroom_subtitle_delay"

    const val PREF_INROOM_PLAYER_CUSTOM_SEEK_AMOUNT = "pref_inroom_custom_seek_amount"
    const val PREF_INROOM_PLAYER_CUSTOM_SEEK_FRONT = "pref_inroom_custom_seek_front"

    const val PREF_INROOM_PLAYER_SEEK_FORWARD_JUMP = "pref_inroom_seek_forward_jump"
    const val PREF_INROOM_PLAYER_SEEK_BACKWARD_JUMP = "pref_inroom_seek_backward_jump"

    /* MPV Settings */
    const val CATEG_INROOM_MPV = "categ_inroom_mpv"
    const val PREF_MPV_HARDWARE_ACCELERATION = "pref_mpv_hw"
    const val PREF_MPV_GPU_NEXT = "pref_mpv_gpunext"
    const val PREF_MPV_DEBUG_MODE = "pref_mpv_debug_mode"
    const val PREF_MPV_VIDSYNC = "pref_mpv_video_sync"
    const val PREF_MPV_PROFILE = "pref_mpv_profile"
    const val PREF_MPV_INTERPOLATION = "pref_mpv_interpolation"

    /* Exoplayer Settings */
    const val CATEG_INROOM_EXOPLAYER = "categ_global_exoplayer"
    const val PREF_EXO_MAX_BUFFER = "pref_max_buffer_size"
    const val PREF_EXO_MIN_BUFFER = "pref_min_buffer_size"
    const val PREF_EXO_SEEK_BUFFER = "pref_seek_buffer_size"

    /* Advanced */
    const val CATEG_INROOM_ADVANCED = "categ_inroom_advanced"
    const val PREF_INROOM_PIP = "pref_inroom_pip"
    const val PREF_INROOM_RECONNECTION_INTERVAL = "pref_inroom_reconnection_interval"
    const val PREF_INROOM_PERFORMANCE_UI_MODE = "pref_inroom_performance_ui_mode"
    const val PREF_INROOM_RESET_DEFAULT = "pref_inroom_reset_default"

}