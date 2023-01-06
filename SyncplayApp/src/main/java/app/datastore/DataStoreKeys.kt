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
}