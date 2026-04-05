package app.preferences.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ConnectWithoutContact
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VideoLabel
import app.preferences.Preferences.AUDIO_LANG
import app.preferences.Preferences.CC_LANG
import app.preferences.Preferences.COLOR_ERRORMSG
import app.preferences.Preferences.HAPTIC_ON_CHAT
import app.preferences.Preferences.HAPTIC_ON_CONNECTION
import app.preferences.Preferences.HAPTIC_ON_JOINED
import app.preferences.Preferences.HAPTIC_ON_LEFT
import app.preferences.Preferences.HAPTIC_ON_PAUSED
import app.preferences.Preferences.HAPTIC_ON_PLAYED
import app.preferences.Preferences.HAPTIC_ON_PLAYLIST
import app.preferences.Preferences.HAPTIC_ON_SEEKED
import app.preferences.Preferences.COLOR_FRIENDTAG
import app.preferences.Preferences.COLOR_SELFTAG
import app.preferences.Preferences.COLOR_SYSTEMMSG
import app.preferences.Preferences.COLOR_TIMESTAMP
import app.preferences.Preferences.COLOR_USERMSG
import app.preferences.Preferences.CUSTOM_SEEK_AMOUNT
import app.preferences.Preferences.CUSTOM_SEEK_FRONT
import app.preferences.Preferences.DISPLAY_LANG
import app.preferences.Preferences.ERASE_SHORTCUTS
import app.preferences.Preferences.EXPORT_LOGS
import app.preferences.Preferences.FILE_MISMATCH_WARNING
import app.preferences.Preferences.GLOBAL_RESET_DEFAULTS
import app.preferences.Preferences.HASH_FILENAME
import app.preferences.Preferences.HASH_FILESIZE
import app.preferences.Preferences.INROOM_RESET_DEFAULTS
import app.preferences.Preferences.MEDIA_DIRECTORIES
import app.preferences.Preferences.MSG_ACTIVATE_STAMP
import app.preferences.Preferences.MSG_BG_OPACITY
import app.preferences.Preferences.MSG_BOX_ACTION
import app.preferences.Preferences.MSG_FADING_DURATION
import app.preferences.Preferences.MSG_FONTSIZE
import app.preferences.Preferences.MSG_MAXCOUNT
import app.preferences.Preferences.MSG_OUTLINE_ACTIVATE
import app.preferences.Preferences.MSG_OUTLINE_THICKNESS
import app.preferences.Preferences.MSG_SHADOW_ACTIVATE
import app.preferences.Preferences.NETWORK_ENGINE
import app.preferences.Preferences.NEVER_SHOW_TIPS
import app.preferences.Preferences.PAUSE_ON_SOMEONE_LEAVE
import app.preferences.Preferences.READY_FIRST_HAND
import app.preferences.Preferences.UNPAUSE_ACTION
import app.preferences.Preferences.RECONNECTION_INTERVAL
import app.preferences.Preferences.REMEMBER_INFO
import app.preferences.Preferences.ROOM_UI_OPACITY
import app.preferences.Preferences.SEEK_BACKWARD_JUMP
import app.preferences.Preferences.SEEK_FORWARD_JUMP
import app.preferences.Preferences.SUBTITLE_SIZE
import app.preferences.Preferences.TLS_ENABLE
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.settings_categ_advanced
import syncplaymobile.shared.generated.resources.settings_categ_general
import syncplaymobile.shared.generated.resources.settings_categ_language
import syncplaymobile.shared.generated.resources.settings_categ_network
import syncplaymobile.shared.generated.resources.settings_categ_syncing
import syncplaymobile.shared.generated.resources.uisetting_categ_chat_colors
import syncplaymobile.shared.generated.resources.uisetting_categ_chat_properties
import syncplaymobile.shared.generated.resources.uisetting_categ_haptics
import syncplaymobile.shared.generated.resources.uisetting_categ_player_settings

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
}

val GLOBAL_ADVANCED = SettingCategory(
    title = Res.string.settings_categ_advanced,
    icon = Icons.Filled.Stream
) {
    +EXPORT_LOGS
    +GLOBAL_RESET_DEFAULTS
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
    +MSG_MAXCOUNT
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
    //+AUDIO_DELAY
    //+SUBTITLE_DELAY

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

val INROOM_ADVANCED = SettingCategory(
    title = Res.string.settings_categ_advanced,
    icon = Icons.Filled.Stream
) {
    +ROOM_UI_OPACITY
    +RECONNECTION_INTERVAL
    +INROOM_RESET_DEFAULTS
}


val SETTINGS_GLOBAL = listOf(GLOBAL_GENERAL, GLOBAL_LANGUAGE, GLOBAL_SYNCING, GLOBAL_NETWORK, GLOBAL_ADVANCED)
val SETTINGS_ROOM = listOf(INROOM_CHATCOLORS, INROOM_CHAT_PROPERTIES, INROOM_PLAYER_SETTINGS, INROOM_HAPTICS, INROOM_ADVANCED)