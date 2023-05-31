package app.datastore

object DataStoreKeys {

    /** ------------ Joining info -------------*/
    const val DATASTORE_MISC_PREFS = "syncplay_misc_prefs"

    /* Log-in (join) info */
    const val MISC_JOIN_USERNAME = "misc_join_info_username"
    const val MISC_JOIN_ROOMNAME = "misc_join_info_roomname"
    const val MISC_JOIN_SERVER_ADDRESS = "misc_join_info_server_address"
    const val MISC_JOIN_SERVER_PORT = "misc_join_info_server_port"
    const val MISC_JOIN_SERVER_PW = "misc_join_info_server_password"

    const val MISC_NIGHTMODE = "misc_nightmode"

    const val MISC_PLAYER_ENGINE = "misc_player_engine"

    const val MISC_DISCARD_HINT_POPUP = "misc_discard_hint_popup"

    /** ---------- Global Settings ----------- */
    const val DATASTORE_GLOBAL_SETTINGS = "syncplay_global_settings"

    /* General */
    const val PREF_REMEMBER_INFO = "pref_remember_info"
    const val PREF_SP_MEDIA_DIRS = "pref_syncplay_media_directories"

    /* Language */
    const val PREF_DISPLAY_LANG = "pref_lang"
    const val PREF_AUDIO_LANG = "pref_audio_preferred_lang"
    const val PREF_CC_LANG = "pref_cc_preferred_lang"

    /* Syncing */
    const val PREF_READY_FIRST_HAND = "pref_ready_first_hand"
    const val PREF_PAUSE_ON_SOMEONE_LEAVE = "pref_pause_if_someone_left"
    const val PREF_FILE_MISMATCH_WARNING = "pref_file_mismatch_warning"
    const val PREF_HASH_FILENAME = "pref_hash_filename"
    const val PREF_HASH_FILESIZE = "pref_hash_filesize"

    /* Vidplayer */
    const val PREF_MAX_BUFFER = "pref_max_buffer_size"
    const val PREF_MIN_BUFFER = "pref_min_buffer_size"
    const val PREF_SEEK_BUFFER = "pref_seek_buffer_size"

    /* Network */
    const val PREF_TLS_ENABLE = "pref_tls"
    const val PREF_RECONNECTION_INTERVAL = "pref_reconnection_interval"

    /* Advanced */
    const val PREF_GLOBAL_CLEAR_ALL = "pref_global_clear_all"

    /** ---------- In-room Preferences ----------- */
    const val DATASTORE_INROOM_PREFERENCES = "syncplay_in_room_preferences"

    /* Message Colors */
    const val PREF_INROOM_COLOR_TIMESTAMP = "pref_inroom_color_timestamp"
    const val PREF_INROOM_COLOR_SELFTAG = "pref_inroom_color_selftag"
    const val PREF_INROOM_COLOR_FRIENDTAG = "pref_inroom_color_friendtag"
    const val PREF_INROOM_COLOR_SYSTEMMSG = "pref_inroom_color_systemmsg"
    const val PREF_INROOM_COLOR_USERMSG = "pref_inroom_color_usermsg"
    const val PREF_INROOM_COLOR_ERRORMSG = "pref_inroom_color_errormsg"

    /* Message Properties */
    const val PREF_INROOM_MSG_ACTIVATE_STAMP = "pref_inroom_msg_activate_stamp"
    const val PREF_INROOM_MSG_BG_OPACITY = "pref_inroom_msg_bg_opacity"
    const val PREF_INROOM_MSG_FONTSIZE = "pref_inroom_msg_fontsize"
    const val PREF_INROOM_MSG_MAXCOUNT = "pref_inroom_msg_maxcount"
    const val PREF_INROOM_MSG_FADING_DURATION = "pref_inroom_fading_msg_duration"
    const val PREF_INROOM_MSG_BOX_ACTION = "pref_inroom_msg_box_action"

    /* Player Settings */
    const val PREF_INROOM_PLAYER_SUBTITLE_SIZE = "pref_inroom_subtitle_size"
    const val PREF_INROOM_PLAYER_AUDIO_DELAY = "pref_inroom_audio_delay"
    const val PREF_INROOM_PLAYER_SUBTITLE_DELAY = "pref_inroom_subtitle_delay"
    const val PREF_INROOM_PLAYER_SEEK_FORWARD_JUMP = "pref_inroom_seek_forward_jump"
    const val PREF_INROOM_PLAYER_SEEK_BACKWARD_JUMP = "pref_inroom_seek_backward_jump"

    /* Advanced */
    const val PREF_INROOM_RECONNECTION_INTERVAL = "pref_inroom_reconnection_interval"
    const val PREF_INROOM_PERFORMANCE_UI_MODE = "pref_inroom_performance_ui_mode"
    const val PREF_INROOM_RESET_DEFAULT = "pref_inroom_reset_default"

}