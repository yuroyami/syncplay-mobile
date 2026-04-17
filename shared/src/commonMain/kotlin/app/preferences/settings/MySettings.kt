package app.preferences.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ConnectWithoutContact
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VideoLabel
import app.preferences.Preferences.AUDIO_LANG
import app.preferences.Preferences.CLEAR_LOGS
import app.preferences.Preferences.CC_LANG
import app.preferences.Preferences.COLOR_ERRORMSG
import app.preferences.Preferences.COLOR_FRIENDTAG
import app.preferences.Preferences.COLOR_SELFTAG
import app.preferences.Preferences.COLOR_SYSTEMMSG
import app.preferences.Preferences.COLOR_TIMESTAMP
import app.preferences.Preferences.COLOR_USERMSG
import app.preferences.Preferences.CUSTOM_SEEK_AMOUNT
import app.preferences.Preferences.CUSTOM_SEEK_FRONT
import app.preferences.Preferences.DOUBLETAP_SEEK
import app.preferences.Preferences.DISPLAY_LANG
import app.preferences.Preferences.ERASE_SHORTCUTS
import app.preferences.Preferences.EXPORT_LOGS
import app.preferences.Preferences.FILE_MISMATCH_WARNING
import app.preferences.Preferences.GLOBAL_RESET_DEFAULTS
import app.preferences.Preferences.HAPTIC_ON_CHAT
import app.preferences.Preferences.HAPTIC_ON_CONNECTION
import app.preferences.Preferences.HAPTIC_ON_JOINED
import app.preferences.Preferences.HAPTIC_ON_LEFT
import app.preferences.Preferences.HAPTIC_ON_PAUSED
import app.preferences.Preferences.HAPTIC_ON_PLAYED
import app.preferences.Preferences.HAPTIC_ON_PLAYLIST
import app.preferences.Preferences.HAPTIC_ON_SEEKED
import app.preferences.Preferences.HUD_AUTO_HIDE_TIMEOUT
import app.preferences.Preferences.OSD_DURATION
import app.preferences.Preferences.OSD_NON_OPERATOR
import app.preferences.Preferences.OSD_OTHER_ROOM
import app.preferences.Preferences.OSD_SAME_ROOM
import app.preferences.Preferences.OSD_SLOWDOWN
import app.preferences.Preferences.OSD_WARNINGS
import app.preferences.Preferences.CHAPTER_DOTS_CLICKABLE
import app.preferences.Preferences.SHOW_CHAPTER_DOTS
import app.preferences.Preferences.SWIPE_GESTURES
import app.preferences.Preferences.HASH_FILENAME
import app.preferences.Preferences.HASH_FILESIZE
import app.preferences.Preferences.INROOM_RESET_DEFAULTS
import app.preferences.Preferences.MEDIA_DIRECTORIES
import app.preferences.Preferences.MPV_EXPORT_CONF
import app.preferences.Preferences.MPV_IMPORT_CONF
import app.preferences.Preferences.MSG_ACTIVATE_STAMP
import app.preferences.Preferences.MSG_BG_OPACITY
import app.preferences.Preferences.MSG_BOX_ACTION
import app.preferences.Preferences.MSG_FADING_DURATION
import app.preferences.Preferences.MSG_FONTSIZE
import app.preferences.Preferences.MSG_OUTLINE_ACTIVATE
import app.preferences.Preferences.MSG_OUTLINE_THICKNESS
import app.preferences.Preferences.MSG_SHADOW_ACTIVATE
import app.preferences.Preferences.NETWORK_ENGINE
import app.preferences.Preferences.NEVER_SHOW_TIPS
import app.preferences.Preferences.PAUSE_ON_SOMEONE_LEAVE
import app.preferences.Preferences.READY_FIRST_HAND
import app.preferences.Preferences.RECONNECTION_INTERVAL
import app.preferences.Preferences.REMEMBER_INFO
import app.preferences.Preferences.ROOM_UI_OPACITY
import app.preferences.Preferences.SEEK_BACKWARD_JUMP
import app.preferences.Preferences.SEEK_FORWARD_JUMP
import app.preferences.Preferences.SUBTITLE_SIZE
import app.preferences.Preferences.SYNC_DONT_SLOW_WITH_ME
import app.preferences.Preferences.SYNC_FASTFORWARD
import app.preferences.Preferences.SYNC_REWIND
import app.preferences.Preferences.SYNC_SLOWDOWN
import app.preferences.Preferences.TLS_ENABLE
import app.preferences.Preferences.TRUSTED_DOMAINS
import app.preferences.Preferences.UNPAUSE_ACTION
import app.preferences.Preferences.VLC_CUSTOM_FLAGS
import app.utils.Platform
import app.utils.platform
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.settings_categ_advanced
import syncplaymobile.shared.generated.resources.settings_categ_general
import syncplaymobile.shared.generated.resources.settings_categ_language
import syncplaymobile.shared.generated.resources.settings_categ_network
import syncplaymobile.shared.generated.resources.settings_categ_syncing
import syncplaymobile.shared.generated.resources.uisetting_categ_chat_colors
import syncplaymobile.shared.generated.resources.uisetting_categ_chat_properties
import syncplaymobile.shared.generated.resources.uisetting_categ_haptics
import syncplaymobile.shared.generated.resources.uisetting_categ_mpv
import syncplaymobile.shared.generated.resources.uisetting_categ_osd
import syncplaymobile.shared.generated.resources.uisetting_categ_player_settings
import syncplaymobile.shared.generated.resources.uisetting_categ_sync_mechanisms
import syncplaymobile.shared.generated.resources.uisetting_categ_vlc

/* Styles */
val settingGLOBALstyle = SettingStyling(
    iconSize = 30
)

val settingROOMstyle = SettingStyling(
    titleSize = 11f,
    summarySize = 8f,
    paddingUsed = 6f
)

val GLOBAL_GENERAL = SettingCategory(
    title = Res.string.settings_categ_general,
    icon = Icons.Filled.SettingsSuggest
) {
    +REMEMBER_INFO
    +NEVER_SHOW_TIPS
    +ERASE_SHORTCUTS
    +MEDIA_DIRECTORIES
}

val GLOBAL_LANGUAGE = SettingCategory(
    title = Res.string.settings_categ_language,
    icon = Icons.Filled.Translate
) {
    +DISPLAY_LANG
    +AUDIO_LANG
    +CC_LANG
}

val GLOBAL_SYNCING = SettingCategory(
    title = Res.string.settings_categ_syncing,
    icon = Icons.Filled.ConnectWithoutContact
) {
    +READY_FIRST_HAND
    +UNPAUSE_ACTION
    +PAUSE_ON_SOMEONE_LEAVE
    +FILE_MISMATCH_WARNING
    +HASH_FILENAME
    +HASH_FILESIZE
}

val GLOBAL_NETWORK = SettingCategory(
    title = Res.string.settings_categ_network,
    icon = Icons.Filled.Hub
) {
    +TLS_ENABLE
    +NETWORK_ENGINE
    +TRUSTED_DOMAINS
}

val GLOBAL_ADVANCED = SettingCategory(
    title = Res.string.settings_categ_advanced,
    icon = Icons.Filled.Stream
) {
    +EXPORT_LOGS
    +CLEAR_LOGS
    +GLOBAL_RESET_DEFAULTS
}

val INROOM_SYNC = SettingCategory(
    title = Res.string.uisetting_categ_sync_mechanisms,
    icon = Icons.Filled.ConnectWithoutContact,
) {
    +SYNC_DONT_SLOW_WITH_ME
    +SYNC_FASTFORWARD
    +SYNC_SLOWDOWN
    +SYNC_REWIND
}

val INROOM_CHATCOLORS = SettingCategory(
    title = Res.string.uisetting_categ_chat_colors,
    icon = Icons.Filled.Palette,
) {
    +COLOR_TIMESTAMP; +COLOR_SELFTAG; +COLOR_FRIENDTAG; +COLOR_SYSTEMMSG; +COLOR_USERMSG; +COLOR_ERRORMSG
}

val INROOM_CHAT_PROPERTIES = SettingCategory(
    title = Res.string.uisetting_categ_chat_properties,
    icon = Icons.AutoMirrored.Filled.Chat
) {
    +MSG_ACTIVATE_STAMP
    +MSG_OUTLINE_ACTIVATE
    +MSG_OUTLINE_THICKNESS
    +MSG_SHADOW_ACTIVATE
    +MSG_BOX_ACTION
    +MSG_BG_OPACITY
    +MSG_FONTSIZE
    +MSG_FADING_DURATION
}

val INROOM_PLAYER_SETTINGS = SettingCategory(
    title = Res.string.uisetting_categ_player_settings,
    icon = Icons.Filled.VideoLabel,
) {
    +CUSTOM_SEEK_FRONT
    +CUSTOM_SEEK_AMOUNT
    +SUBTITLE_SIZE
    +SEEK_FORWARD_JUMP
    +SEEK_BACKWARD_JUMP
    +SHOW_CHAPTER_DOTS
    +CHAPTER_DOTS_CLICKABLE
    +DOUBLETAP_SEEK
    +SWIPE_GESTURES

}

val INROOM_HAPTICS = SettingCategory(
    title = Res.string.uisetting_categ_haptics,
    icon = Icons.Filled.Vibration,
) {
    +HAPTIC_ON_JOINED
    +HAPTIC_ON_LEFT
    +HAPTIC_ON_CHAT
    +HAPTIC_ON_PAUSED
    +HAPTIC_ON_PLAYED
    +HAPTIC_ON_SEEKED
    +HAPTIC_ON_PLAYLIST
    +HAPTIC_ON_CONNECTION
}

val INROOM_OSD = SettingCategory(
    title = Res.string.uisetting_categ_osd,
    icon = Icons.Filled.NotificationsActive,
) {
    +OSD_DURATION
    +OSD_SAME_ROOM
    +OSD_NON_OPERATOR
    +OSD_OTHER_ROOM
    +OSD_SLOWDOWN
    +OSD_WARNINGS
}

/**
 * mpv config import/export — only meaningful on Android, where the mpv library is initialized
 * with `config-dir={filesDir}` and reads `mpv.conf` from there. On iOS the mpv engine does not
 * honor a user config file, so the category is omitted from the settings list below.
 */
val INROOM_MPV = SettingCategory(
    title = Res.string.uisetting_categ_mpv,
    icon = Icons.Filled.Memory,
) {
    +MPV_IMPORT_CONF
    +MPV_EXPORT_CONF
}

/**
 * VLC custom launch flags — applies to both Android (LibVLC) and iOS (VLCKit/VLCLibrary).
 * Tokens entered here are appended to the base args each VLC instance is constructed with.
 */
val INROOM_VLC = SettingCategory(
    title = Res.string.uisetting_categ_vlc,
    icon = Icons.Filled.Keyboard,
) {
    +VLC_CUSTOM_FLAGS
}

val INROOM_ADVANCED = SettingCategory(
    title = Res.string.settings_categ_advanced,
    icon = Icons.Filled.Stream
) {
    +ROOM_UI_OPACITY
    +HUD_AUTO_HIDE_TIMEOUT
    +RECONNECTION_INTERVAL
    +INROOM_RESET_DEFAULTS
}


val SETTINGS_GLOBAL = listOf(GLOBAL_GENERAL, GLOBAL_LANGUAGE, GLOBAL_SYNCING, GLOBAL_NETWORK, GLOBAL_ADVANCED)
val SETTINGS_ROOM: List<SettingCategory> = buildList {
    add(INROOM_SYNC)
    add(INROOM_CHATCOLORS)
    add(INROOM_CHAT_PROPERTIES)
    add(INROOM_PLAYER_SETTINGS)
    add(INROOM_OSD)
    // mpv.conf import/export is Android-only (iOS mpv does not read a config file).
    if (platform == Platform.Android) add(INROOM_MPV)
    add(INROOM_VLC)
    add(INROOM_ADVANCED)
}