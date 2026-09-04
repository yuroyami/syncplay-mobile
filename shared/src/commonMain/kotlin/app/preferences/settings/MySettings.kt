package app.preferences.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ConnectWithoutContact
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VideoLabel
import app.preferences.Preferences.AUDIO_LANG
import app.preferences.Preferences.CHAT_COLORS_ENTRY
import app.preferences.Preferences.CLEAR_LOGS
import app.preferences.Preferences.GIF_REMEMBER_RECENTS
import app.preferences.Preferences.CC_LANG
import app.preferences.Preferences.CUSTOM_SEEK_AMOUNT
import app.preferences.Preferences.CUSTOM_SEEK_FRONT
import app.preferences.Preferences.DISPLAY_LANG
import app.preferences.Preferences.ERASE_SHORTCUTS
import app.preferences.Preferences.DISABLE_FROSTED_GLASS
import app.preferences.Preferences.EXPORT_LOGS
import app.preferences.Preferences.FILE_MISMATCH_WARNING
import app.preferences.Preferences.GLOBAL_RESET_DEFAULTS
import app.preferences.Preferences.HAPTICS_ON_CONTROLS
import app.preferences.Preferences.REDUCE_MOTION
import app.preferences.Preferences.HAPTIC_ON_CHAT
import app.preferences.Preferences.HUD_AUTO_HIDE_SECONDS
import app.preferences.Preferences.HAPTIC_ON_CONNECTION
import app.preferences.Preferences.HAPTIC_ON_JOINED
import app.preferences.Preferences.HAPTIC_ON_LEFT
import app.preferences.Preferences.HAPTIC_ON_PAUSED
import app.preferences.Preferences.HAPTIC_ON_PLAYED
import app.preferences.Preferences.HAPTIC_ON_PLAYLIST
import app.preferences.Preferences.HAPTIC_ON_SEEKED
import app.preferences.Preferences.OSD_DURATION
import app.preferences.Preferences.OSD_NON_OPERATOR
import app.preferences.Preferences.OSD_OTHER_ROOM
import app.preferences.Preferences.OSD_SAME_ROOM
import app.preferences.Preferences.OSD_SLOWDOWN
import app.preferences.Preferences.OSD_WARNINGS
import app.preferences.Preferences.CHAPTER_DOTS_CLICKABLE
import app.preferences.Preferences.SHOW_CHAPTER_DOTS
import app.preferences.Preferences.HASH_FILENAME
import app.preferences.Preferences.HASH_FILESIZE
import app.preferences.Preferences.INROOM_RESET_DEFAULTS
import app.preferences.Preferences.CHAT_SUBTITLE_CLEARANCE
import app.preferences.Preferences.MEDIA_DIRECTORIES
import app.preferences.Preferences.RESUME_PLAYBACK
import app.preferences.Preferences.MEDIA_RESOLVER_ENABLED
import app.preferences.Preferences.MSG_BG_OPACITY
import app.preferences.Preferences.MSG_BOX_ACTION
import app.preferences.Preferences.MSG_FADING_DURATION
import app.preferences.Preferences.MSG_FONTSIZE
import app.preferences.Preferences.MSG_MAXCOUNT
import app.preferences.Preferences.MSG_OUTLINE_THICKNESS
import app.preferences.Preferences.MSG_SHADOW_ACTIVATE
import app.preferences.Preferences.NETWORK_ENGINE
import app.preferences.Preferences.NEVER_SHOW_TIPS
import app.preferences.Preferences.PAUSE_ON_SOMEONE_LEAVE
import app.preferences.Preferences.READY_FIRST_HAND
import app.preferences.Preferences.RECONNECTION_INTERVAL
import app.preferences.Preferences.REMEMBER_INFO
import app.preferences.Preferences.SEEK_BACKWARD_JUMP
import app.preferences.Preferences.SEEK_FORWARD_JUMP
import app.preferences.Preferences.SHOW_SETTING_DESCRIPTIONS
import app.preferences.Preferences.ROOM_ALLOW_PORTRAIT
import app.preferences.Preferences.SUBTITLE_SIZE
import app.preferences.Preferences.VIDEO_BACKGROUND_COLOR
import app.preferences.Preferences.SYNC_DONT_SLOW_WITH_ME
import app.preferences.Preferences.SYNC_FASTFORWARD
import app.preferences.Preferences.SYNC_REWIND
import app.preferences.Preferences.AUTOPLAY
import app.preferences.Preferences.SYNC_FASTFORWARD_THRESHOLD
import app.preferences.Preferences.SYNC_REWIND_THRESHOLD
import app.preferences.Preferences.USER_TIME_OFFSET
import app.preferences.Preferences.SYNC_SLOWDOWN_THRESHOLD
import app.preferences.Preferences.SYNC_SLOWDOWN
import app.preferences.Preferences.TLS_ENABLE
import app.preferences.Preferences.TLS_REQUIRED
import app.preferences.Preferences.TRUSTED_DOMAINS
import app.preferences.Preferences.UNPAUSE_ACTION
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.settings_categ_advanced
import syncplaymobile.shared.generated.resources.settings_categ_general
import syncplaymobile.shared.generated.resources.settings_categ_language
import syncplaymobile.shared.generated.resources.settings_categ_network
import syncplaymobile.shared.generated.resources.settings_categ_syncing
import syncplaymobile.shared.generated.resources.settings_group_chapters
import syncplaymobile.shared.generated.resources.settings_group_connection
import syncplaymobile.shared.generated.resources.settings_group_links
import syncplaymobile.shared.generated.resources.settings_group_logs
import syncplaymobile.shared.generated.resources.settings_group_messages
import syncplaymobile.shared.generated.resources.settings_group_notices
import syncplaymobile.shared.generated.resources.settings_group_picture
import syncplaymobile.shared.generated.resources.settings_group_privacy
import syncplaymobile.shared.generated.resources.settings_group_readiness
import syncplaymobile.shared.generated.resources.settings_group_seeking
import syncplaymobile.shared.generated.resources.settings_group_subtitles
import syncplaymobile.shared.generated.resources.uisetting_categ_chat_properties
import syncplaymobile.shared.generated.resources.uisetting_categ_haptics
import syncplaymobile.shared.generated.resources.uisetting_categ_player_settings
import syncplaymobile.shared.generated.resources.uisetting_categ_sync_mechanisms

val GLOBAL_GENERAL = SettingCategory(
    title = Res.string.settings_categ_general,
    icon = Icons.Filled.SettingsSuggest
) {
    +REMEMBER_INFO
    +NEVER_SHOW_TIPS
    +SHOW_SETTING_DESCRIPTIONS
    +HAPTICS_ON_CONTROLS
        +REDUCE_MOTION
    +ERASE_SHORTCUTS
    +MEDIA_DIRECTORIES
    +RESUME_PLAYBACK
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
    group(Res.string.settings_group_readiness) {
        +READY_FIRST_HAND
        +UNPAUSE_ACTION
        +PAUSE_ON_SOMEONE_LEAVE
    }
    group(Res.string.settings_group_privacy) {
        +FILE_MISMATCH_WARNING
        +HASH_FILENAME
        +HASH_FILESIZE
    }
}

val GLOBAL_NETWORK = SettingCategory(
    title = Res.string.settings_categ_network,
    icon = Icons.Filled.Hub
) {
    group(Res.string.settings_group_connection) {
        +TLS_ENABLE
        +TLS_REQUIRED
        +NETWORK_ENGINE
    }
    group(Res.string.settings_group_links) {
        +MEDIA_RESOLVER_ENABLED
        +TRUSTED_DOMAINS
    }
}

val GLOBAL_ADVANCED = SettingCategory(
    title = Res.string.settings_categ_advanced,
    icon = Icons.Filled.Stream
) {
    +DISABLE_FROSTED_GLASS
    group(Res.string.settings_group_logs) {
        +EXPORT_LOGS
        +CLEAR_LOGS
    }
    +GLOBAL_RESET_DEFAULTS
}

val INROOM_SYNC = SettingCategory(
    title = Res.string.uisetting_categ_sync_mechanisms,
    icon = Icons.Filled.ConnectWithoutContact,
) {
    +SYNC_DONT_SLOW_WITH_ME
    +AUTOPLAY
    +USER_TIME_OFFSET
    // Each threshold sits under the switch it belongs to, and greys out with it.
    +SYNC_FASTFORWARD
    +SYNC_FASTFORWARD_THRESHOLD
    +SYNC_SLOWDOWN
    +SYNC_SLOWDOWN_THRESHOLD
    +SYNC_REWIND
    +SYNC_REWIND_THRESHOLD
}

val INROOM_CHAT_PROPERTIES = SettingCategory(
    title = Res.string.uisetting_categ_chat_properties,
    icon = Icons.AutoMirrored.Filled.Chat
) {
    group(Res.string.settings_group_messages) {
        +CHAT_COLORS_ENTRY
        +CHAT_SUBTITLE_CLEARANCE
        +MSG_OUTLINE_THICKNESS
        +MSG_SHADOW_ACTIVATE
        +MSG_BOX_ACTION
        +MSG_BG_OPACITY
        +MSG_FONTSIZE
        +MSG_FADING_DURATION
        +MSG_MAXCOUNT
        +GIF_REMEMBER_RECENTS
    }
    /* The notice switches the room reads; they had no row anywhere until now. */
    group(Res.string.settings_group_notices) {
        +OSD_DURATION
        +OSD_SAME_ROOM
        +OSD_NON_OPERATOR
        +OSD_OTHER_ROOM
        +OSD_SLOWDOWN
        +OSD_WARNINGS
    }
}

val INROOM_PLAYER_SETTINGS = SettingCategory(
    title = Res.string.uisetting_categ_player_settings,
    icon = Icons.Filled.VideoLabel,
) {
    +SUBTITLE_SIZE
    +HUD_AUTO_HIDE_SECONDS
    +ROOM_ALLOW_PORTRAIT
    group(Res.string.settings_group_seeking) {
        +SEEK_FORWARD_JUMP
        +SEEK_BACKWARD_JUMP
        +CUSTOM_SEEK_FRONT
        +CUSTOM_SEEK_AMOUNT
    }
    group(Res.string.settings_group_subtitles) {
        /* Preferred track languages, mirrored from the global Language category so they are
         * reachable mid-session too. */
        +CC_LANG
        +AUDIO_LANG
    }
    group(Res.string.settings_group_chapters) {
        +SHOW_CHAPTER_DOTS
        +CHAPTER_DOTS_CLICKABLE
    }
    group(Res.string.settings_group_picture) {
        +VIDEO_BACKGROUND_COLOR
    }
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
    +RECONNECTION_INTERVAL
    +INROOM_RESET_DEFAULTS
}


val SETTINGS_GLOBAL: List<SettingCategory> = listOf(GLOBAL_GENERAL, GLOBAL_LANGUAGE, GLOBAL_SYNCING, GLOBAL_NETWORK, GLOBAL_ADVANCED)

/**
 * Engine-agnostic in-room settings.
 *
 * Engine-specific rows are absent here: each [app.player.PlayerImpl] returns its own category
 * via [app.player.PlayerImpl.configurableSettings], and [roomSettings] folds the active engine's
 * rows into the player category as its last group.
 */
val SETTINGS_ROOM: List<SettingCategory> = listOf(
    INROOM_SYNC,
    INROOM_CHAT_PROPERTIES,
    INROOM_PLAYER_SETTINGS,
    INROOM_HAPTICS,
    INROOM_ADVANCED,
)

/** The room's categories, with the active engine's rows folded into the player category. */
fun roomSettings(engine: SettingCategory?): List<SettingCategory> {
    if (engine == null) return SETTINGS_ROOM
    val player = SettingCategory(INROOM_PLAYER_SETTINGS.title, INROOM_PLAYER_SETTINGS.icon) {
        INROOM_PLAYER_SETTINGS.groups.forEach { include(it) }
        include(SettingGroup(engine.title, engine.entries))
    }
    return SETTINGS_ROOM.map { if (it === INROOM_PLAYER_SETTINGS) player else it }
    }
