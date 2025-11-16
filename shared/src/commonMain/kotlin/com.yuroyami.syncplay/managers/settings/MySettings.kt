package com.yuroyami.syncplay.managers.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material.icons.filled.ConnectWithoutContact
import androidx.compose.material.icons.filled.DesignServices
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FrontHand
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.VideoLabel
import androidx.compose.material.icons.filled.Web
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.preferences.core.edit
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.CATEG_GLOBAL_ADVANCED
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.CATEG_GLOBAL_GENERAL
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.CATEG_GLOBAL_LANG
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.CATEG_GLOBAL_NETWORK
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.CATEG_GLOBAL_SYNCING
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.CATEG_INROOM_ADVANCED
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.CATEG_INROOM_CHATCOLORS
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.CATEG_INROOM_CHATPROPS
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.CATEG_INROOM_PLAYERSETTINGS
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.PREF_CC_LANG
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.PREF_DISPLAY_LANG
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.PREF_ERASE_SHORTCUTS
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.PREF_FILE_MISMATCH_WARNING
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.PREF_GLOBAL_CLEAR_ALL
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.PREF_HASH_FILENAME
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.PREF_HASH_FILESIZE
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.PREF_INROOM_COLOR_ERRORMSG
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.PREF_INROOM_COLOR_FRIENDTAG
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.PREF_INROOM_COLOR_SELFTAG
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.PREF_INROOM_COLOR_SYSTEMMSG
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.PREF_INROOM_COLOR_TIMESTAMP
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.PREF_INROOM_COLOR_USERMSG
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.PREF_INROOM_MSG_ACTIVATE_STAMP
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.PREF_INROOM_MSG_BG_OPACITY
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.PREF_INROOM_MSG_BOX_ACTION
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.PREF_INROOM_MSG_FADING_DURATION
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.PREF_INROOM_MSG_FONTSIZE
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.PREF_INROOM_MSG_MAXCOUNT
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.PREF_INROOM_MSG_OUTLINE
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.PREF_INROOM_MSG_SHADOW
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.PREF_INROOM_PLAYER_CUSTOM_SEEK_FRONT
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.PREF_INROOM_RESET_DEFAULT
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.PREF_NETWORK_ENGINE
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.PREF_NEVER_SHOW_TIPS
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.PREF_PAUSE_ON_SOMEONE_LEAVE
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.PREF_READY_FIRST_HAND
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.PREF_REMEMBER_INFO
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.PREF_SP_MEDIA_DIRS
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.PREF_TLS_ENABLE
import com.yuroyami.syncplay.managers.datastore.datastore
import com.yuroyami.syncplay.ui.popups.PopupMediaDirs.MediaDirsPopup
import com.yuroyami.syncplay.ui.theme.Theming
import com.yuroyami.syncplay.utils.PLATFORM
import com.yuroyami.syncplay.utils.langMap
import com.yuroyami.syncplay.utils.platform
import com.yuroyami.syncplay.utils.platformCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.media_directories
import syncplaymobile.shared.generated.resources.media_directories_setting_summary
import syncplaymobile.shared.generated.resources.setting_cc_default_language_summry
import syncplaymobile.shared.generated.resources.setting_cc_default_language_title
import syncplaymobile.shared.generated.resources.setting_display_language_summry
import syncplaymobile.shared.generated.resources.setting_display_language_title
import syncplaymobile.shared.generated.resources.setting_erase_shortcuts_dialog
import syncplaymobile.shared.generated.resources.setting_erase_shortcuts_summary
import syncplaymobile.shared.generated.resources.setting_erase_shortcuts_title
import syncplaymobile.shared.generated.resources.setting_fileinfo_behavior_a
import syncplaymobile.shared.generated.resources.setting_fileinfo_behavior_b
import syncplaymobile.shared.generated.resources.setting_fileinfo_behavior_c
import syncplaymobile.shared.generated.resources.setting_fileinfo_behaviour_name_summary
import syncplaymobile.shared.generated.resources.setting_fileinfo_behaviour_name_title
import syncplaymobile.shared.generated.resources.setting_fileinfo_behaviour_size_summary
import syncplaymobile.shared.generated.resources.setting_fileinfo_behaviour_size_title
import syncplaymobile.shared.generated.resources.setting_network_engine_ktor
import syncplaymobile.shared.generated.resources.setting_network_engine_netty
import syncplaymobile.shared.generated.resources.setting_network_engine_summary
import syncplaymobile.shared.generated.resources.setting_network_engine_swift_nio
import syncplaymobile.shared.generated.resources.setting_network_engine_title
import syncplaymobile.shared.generated.resources.setting_never_show_tips_summary
import syncplaymobile.shared.generated.resources.setting_never_show_tips_title
import syncplaymobile.shared.generated.resources.setting_pause_if_someone_left_summary
import syncplaymobile.shared.generated.resources.setting_pause_if_someone_left_title
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
import syncplaymobile.shared.generated.resources.settings_categ_advanced
import syncplaymobile.shared.generated.resources.settings_categ_general
import syncplaymobile.shared.generated.resources.settings_categ_language
import syncplaymobile.shared.generated.resources.settings_categ_network
import syncplaymobile.shared.generated.resources.settings_categ_syncing
import syncplaymobile.shared.generated.resources.uisetting_categ_chat_colors
import syncplaymobile.shared.generated.resources.uisetting_categ_chat_properties
import syncplaymobile.shared.generated.resources.uisetting_categ_player_settings
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
import syncplaymobile.shared.generated.resources.uisetting_subtitle_size_summary
import syncplaymobile.shared.generated.resources.uisetting_subtitle_size_title
import syncplaymobile.shared.generated.resources.uisetting_system_color_summary
import syncplaymobile.shared.generated.resources.uisetting_system_color_title
import syncplaymobile.shared.generated.resources.uisetting_timestamp_color_title
import syncplaymobile.shared.generated.resources.uisetting_timestamp_summary
import syncplaymobile.shared.generated.resources.uisetting_timestamp_title


typealias SettingSet = List<Setting<out Any>>
typealias SettingCollection = Map<SettingCategory, SettingSet>

typealias ExtraSettingBundle = Pair<SettingCategory, SettingSet>


/* Styles */
val settingGLOBALstyle = SettingStyling(
    iconSize = 30
)

val settingROOMstyle = SettingStyling(
    titleSize = 11f,
    summarySize = 8f
)

val SETTINGS_GLOBAL: SettingCollection by lazy {
    buildMap {
        put(
            SettingCategory(
                keyID = CATEG_GLOBAL_GENERAL,
                title = Res.string.settings_categ_general,
                icon = Icons.Filled.SettingsSuggest
            ),
            listOf(
                Setting.BooleanSetting(
                    type = SettingType.CheckboxSettingType,
                    key = PREF_REMEMBER_INFO,
                    title = Res.string.setting_remember_join_info_title,
                    summary = Res.string.setting_remember_join_info_summary,
                    defaultValue = true,
                    icon = Icons.Filled.Face
                ),
                Setting.BooleanSetting(
                    type = SettingType.CheckboxSettingType,
                    key = PREF_NEVER_SHOW_TIPS,
                    title = Res.string.setting_never_show_tips_title,
                    summary = Res.string.setting_never_show_tips_summary,
                    defaultValue = false,
                    icon = Icons.Filled.Lightbulb
                ),
                Setting.YesNoDialogSetting(
                    type = SettingType.YesNoDialogSettingType,
                    key = PREF_ERASE_SHORTCUTS,
                    title = Res.string.setting_erase_shortcuts_title,
                    summary = Res.string.setting_erase_shortcuts_summary,
                    defaultValue = true,
                    icon = Icons.Filled.BookmarkRemove,
                    rationale = Res.string.setting_erase_shortcuts_dialog,
                    onYes = {
                        platformCallback.onEraseConfigShortcuts()
                    }
                ),
                Setting.PopupSetting(
                    type = SettingType.PopupSettingType,
                    key = PREF_SP_MEDIA_DIRS,
                    title = Res.string.media_directories,
                    summary = Res.string.media_directories_setting_summary,
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    popupComposable = { s ->
                        MediaDirsPopup(s)
                    }
                )
            )
        )

        put(
            SettingCategory(
                keyID = CATEG_GLOBAL_LANG,
                title = Res.string.settings_categ_language,
                icon = Icons.Filled.Translate,
            ),
            listOf(
                if (platform == PLATFORM.Android) {
                    Setting.MultiChoiceSetting(
                        type = SettingType.MultiChoicePopupSettingType,
                        key = PREF_DISPLAY_LANG,
                        title = Res.string.setting_display_language_title,
                        summary = Res.string.setting_display_language_summry,
                        defaultValue = "en",
                        icon = Icons.Filled.Translate,
                        entries = { langMap },
                        onItemChosen = { v ->
                            platformCallback.onLanguageChanged(v)
                        }
                    )
                } else {
                    Setting.OneClickSetting(
                        type = SettingType.OneClickSettingType,
                        key = PREF_DISPLAY_LANG,
                        title = Res.string.setting_display_language_title,
                        summary = Res.string.setting_display_language_summry,
                        icon = Icons.Filled.Translate,
                        onClick = {
                            platformCallback.onLanguageChanged("")
                        }
                    )
                },
                Setting.TextFieldSetting(
                    type = SettingType.TextFieldSettingType,
                    key = PREF_CC_LANG,
                    title = Res.string.setting_cc_default_language_title,
                    summary = Res.string.setting_cc_default_language_summry,
                    defaultValue = "eng",
                    icon = Icons.Filled.ClosedCaptionOff,
                )
            )
        )

        put(
            SettingCategory(
                keyID = CATEG_GLOBAL_SYNCING,
                title = Res.string.settings_categ_syncing,
                icon = Icons.Filled.ConnectWithoutContact
            ),
            listOf(
                Setting.BooleanSetting(
                    type = SettingType.CheckboxSettingType,
                    key = PREF_READY_FIRST_HAND,
                    title = Res.string.setting_ready_firsthand_title,
                    summary = Res.string.setting_ready_firsthand_summary,
                    defaultValue = true,
                    icon = Icons.Filled.TaskAlt,
                ),
                Setting.BooleanSetting(
                    type = SettingType.CheckboxSettingType,
                    key = PREF_PAUSE_ON_SOMEONE_LEAVE,
                    title = Res.string.setting_pause_if_someone_left_title,
                    summary = Res.string.setting_pause_if_someone_left_summary,
                    defaultValue = true,
                    icon = Icons.Filled.FrontHand,
                ),
                Setting.BooleanSetting(
                    type = SettingType.CheckboxSettingType,
                    key = PREF_FILE_MISMATCH_WARNING,
                    title = Res.string.setting_warn_file_mismatch_title,
                    summary = Res.string.setting_warn_file_mismatch_summary,
                    defaultValue = true,
                    icon = Icons.Filled.ErrorOutline,
                ),
                Setting.MultiChoiceSetting(
                    type = SettingType.MultiChoicePopupSettingType,
                    key = PREF_HASH_FILENAME,
                    title = Res.string.setting_fileinfo_behaviour_name_title,
                    summary = Res.string.setting_fileinfo_behaviour_name_summary,
                    defaultValue = "1",
                    icon = Icons.Filled.DesignServices,
                    entries = {
                        mapOf(
                            stringResource(Res.string.setting_fileinfo_behavior_a) to "1",
                            stringResource(Res.string.setting_fileinfo_behavior_b) to "2",
                            stringResource(Res.string.setting_fileinfo_behavior_c) to "3"
                        )
                    },
                ),
                Setting.MultiChoiceSetting(
                    type = SettingType.MultiChoicePopupSettingType,
                    key = PREF_HASH_FILESIZE,
                    title = Res.string.setting_fileinfo_behaviour_size_title,
                    summary = Res.string.setting_fileinfo_behaviour_size_summary,
                    defaultValue = "1",
                    icon = Icons.Filled.DesignServices,
                    entries = {
                        mapOf(
                            stringResource(Res.string.setting_fileinfo_behavior_a) to "1",
                            stringResource(Res.string.setting_fileinfo_behavior_b) to "2",
                            stringResource(Res.string.setting_fileinfo_behavior_c) to "3"
                        )
                    }
                )
            )
        )

        put(
            SettingCategory(
                keyID = CATEG_GLOBAL_NETWORK,
                title = Res.string.settings_categ_network,
                icon = Icons.Filled.Hub
            ),
            listOf(
                Setting.BooleanSetting(
                    type = SettingType.ToggleSettingType,
                    key = PREF_TLS_ENABLE,
                    title = Res.string.setting_tls_title,
                    summary = Res.string.setting_tls_summary,
                    defaultValue = true,
                    icon = Icons.Filled.Key,
                ),

                Setting.MultiChoiceSetting(
                    type = SettingType.MultiChoicePopupSettingType,
                    key = PREF_NETWORK_ENGINE,
                    title = Res.string.setting_network_engine_title,
                    summary = Res.string.setting_network_engine_summary,
                    defaultValue = if (platform == PLATFORM.Android) "netty" else "swiftnio",
                    icon = Icons.Filled.Lan,
                    entries = @Composable {
                        buildMap {
                            if (platform == PLATFORM.Android) {
                                put(
                                    stringResource(Res.string.setting_network_engine_netty),
                                    "netty"
                                )
                            } else {
                                put(
                                    stringResource(Res.string.setting_network_engine_swift_nio),
                                    "swiftnio"
                                )
                            }

                            put(
                                stringResource(Res.string.setting_network_engine_ktor),
                                "ktor"
                            )
                        }
                    }
                )
            )
        )

        put(
            SettingCategory(
                keyID = CATEG_GLOBAL_ADVANCED,
                title = Res.string.settings_categ_advanced,
                icon = Icons.Filled.Stream
            ),
            listOf(
                Setting.YesNoDialogSetting(
                    type = SettingType.OneClickSettingType,
                    key = PREF_GLOBAL_CLEAR_ALL,
                    title = Res.string.setting_resetdefault_title,
                    summary = Res.string.setting_resetdefault_summary,
                    icon = Icons.Filled.ClearAll,
                    rationale = Res.string.setting_resetdefault_dialog,
                    onYes = {
                        launch(Dispatchers.IO) {
                            datastore.edit { preferences ->
                                preferences.clear()
                            }
                        }
                    },
                )
            )
        )
    }
}

val SETTINGS_ROOM: SettingCollection = buildMap {
    put(
        SettingCategory(
            keyID = CATEG_INROOM_CHATCOLORS,
            title = Res.string.uisetting_categ_chat_colors,
            icon = Icons.Filled.Palette,
        ),
        listOf(
            Setting.ColorSetting(
                type = SettingType.ColorSettingType,
                key = PREF_INROOM_COLOR_TIMESTAMP,
                title = Res.string.uisetting_timestamp_color_title,
                summary = Res.string.uisetting_timestamp_summary,
                defaultValue = Theming.MSG_TIMESTAMP.toArgb(),
                icon = Icons.Filled.Brush,
            ),
            Setting.ColorSetting(
                type = SettingType.ColorSettingType,
                key = PREF_INROOM_COLOR_SELFTAG,
                title = Res.string.uisetting_self_color_title,
                summary = Res.string.uisetting_self_color_summary,
                defaultValue = Theming.MSG_SELF_TAG.toArgb(),
                icon = Icons.Filled.Brush,
            ), Setting.ColorSetting(
                type = SettingType.ColorSettingType,
                key = PREF_INROOM_COLOR_FRIENDTAG,
                title = Res.string.uisetting_friend_color_title,
                summary = Res.string.uisetting_friend_color_summary,
                defaultValue = Theming.MSG_FRIEND_TAG.toArgb(),
                icon = Icons.Filled.Brush,
            ), Setting.ColorSetting(
                type = SettingType.ColorSettingType,
                key = PREF_INROOM_COLOR_SYSTEMMSG,
                title = Res.string.uisetting_system_color_title,
                summary = Res.string.uisetting_system_color_summary,
                defaultValue = Theming.MSG_SYSTEM.toArgb(),
                icon = Icons.Filled.Brush,
            ),
            Setting.ColorSetting(
                type = SettingType.ColorSettingType,
                key = PREF_INROOM_COLOR_USERMSG,
                title = Res.string.uisetting_human_color_title,
                summary = Res.string.uisetting_human_color_summary,
                defaultValue = Theming.MSG_CHAT.toArgb(),
                icon = Icons.Filled.Brush,
            ), Setting.ColorSetting(
                type = SettingType.ColorSettingType,
                key = PREF_INROOM_COLOR_ERRORMSG,
                title = Res.string.uisetting_error_color_title,
                summary = Res.string.uisetting_error_color_summary,
                defaultValue = Theming.MSG_ERROR.toArgb(),
                icon = Icons.Filled.Brush,
            )
        )
    )

    put(
        SettingCategory(
            keyID = CATEG_INROOM_CHATPROPS,
            title = Res.string.uisetting_categ_chat_properties,
            icon = Icons.AutoMirrored.Filled.Chat
        ),
        listOf(
            Setting.BooleanSetting(
                type = SettingType.ToggleSettingType,
                key = PREF_INROOM_MSG_ACTIVATE_STAMP,
                title = Res.string.uisetting_timestamp_title,
                summary = Res.string.uisetting_timestamp_summary,
                defaultValue = true,
                icon = Icons.Filled.Pin,
            ),
            Setting.BooleanSetting(
                type = SettingType.ToggleSettingType,
                key = PREF_INROOM_MSG_OUTLINE,
                title = Res.string.uisetting_msgoutline_title,
                summary = Res.string.uisetting_msgoutline_summary,
                defaultValue = true,
                icon = Icons.Filled.BorderColor,
            ), Setting.BooleanSetting(
                type = SettingType.ToggleSettingType,
                key = PREF_INROOM_MSG_SHADOW,
                title = Res.string.uisetting_msgshadow_title,
                summary = Res.string.uisetting_msgshadow_summary,
                defaultValue = false,
                icon = Icons.Filled.BorderColor,
            ), Setting.SliderSetting(
                type = SettingType.SliderSettingType,
                key = PREF_INROOM_MSG_BG_OPACITY,
                title = Res.string.uisetting_messagery_alpha_title,
                summary = Res.string.uisetting_messagery_alpha_summary,
                defaultValue = 0,
                icon = Icons.Filled.Opacity,
                maxValue = 255,
                minValue = 0,
            ), Setting.SliderSetting(
                type = SettingType.SliderSettingType,
                key = PREF_INROOM_MSG_FONTSIZE,
                title = Res.string.uisetting_msgsize_title,
                summary = Res.string.uisetting_msgsize_summary,
                defaultValue = 9,
                icon = Icons.Filled.FormatSize,
                maxValue = 28,
                minValue = 6,
            ), Setting.SliderSetting(
                type = SettingType.SliderSettingType,
                key = PREF_INROOM_MSG_MAXCOUNT,
                title = Res.string.uisetting_msgcount_title,
                summary = Res.string.uisetting_msgcount_summary,
                defaultValue = 10,
                icon = Icons.Filled.FormatListNumbered,
                maxValue = 30,
                minValue = 1,
            ), Setting.SliderSetting(
                type = SettingType.SliderSettingType,
                key = PREF_INROOM_MSG_FADING_DURATION,
                title = Res.string.uisetting_msglife_title,
                summary = Res.string.uisetting_msglife_summary,
                defaultValue = 3,
                icon = Icons.Filled.Timer,
                maxValue = 10,
                minValue = 1,
            ), Setting.BooleanSetting(
                type = SettingType.ToggleSettingType,
                key = PREF_INROOM_MSG_BOX_ACTION,
                title = Res.string.uisetting_msgboxaction_title,
                summary = Res.string.uisetting_msgboxaction_summary,
                defaultValue = true,
                icon = Icons.Filled.Keyboard,
            )
        )
    )
    put(
        SettingCategory(
            keyID = CATEG_INROOM_PLAYERSETTINGS,
            title = Res.string.uisetting_categ_player_settings,
            icon = Icons.Filled.VideoLabel,
        ),
        listOf(
            Setting.SliderSetting(
                type = SettingType.SliderSettingType,
                key = DataStoreKeys.PREF_INROOM_PLAYER_SUBTITLE_SIZE,
                title = Res.string.uisetting_subtitle_size_title,
                summary = Res.string.uisetting_subtitle_size_summary,
                defaultValue = 16,
                icon = Icons.Filled.SortByAlpha,
                maxValue = 200,
                minValue = 2,
                onValueChanged = { v ->
                    //TODO viewmodel?.player?.changeSubtitleSize(v)
                }
            ),
            //                    Setting(
//                        type = SettingType.SliderSetting,
//                        title = resources.getString(R.string.uisetting_subtitle_delay_title),
//                        summary = resources.getString(R.string.uisetting_subtitle_delay_summary),
//                        defaultValue = 0,
//                        minValue = -120000,
//                        maxValue = +120000,
//                        key = PREF_INROOM_PLAYER_SUBTITLE_DELAY,
//                        icon = Icons.Filled.CompareArrows,
//                        datastorekey = ds
//                    ),
//                    Setting(
//                        type = SettingType.SliderSetting,
//                        title = resources.getString(R.string.uisetting_audio_delay_title),
//                        summary = resources.getString(R.string.uisetting_audio_delay_summary),
//                        defaultValue = 0,
//                        minValue = -120000,
//                        maxValue = +120000,
//                        key = PREF_INROOM_PLAYER_AUDIO_DELAY,
//                        icon = Icons.Filled.CompareArrows,
//                        datastorekey = ds
//                    ),
            Setting.BooleanSetting(
                type = SettingType.CheckboxSettingType,
                key = PREF_INROOM_PLAYER_CUSTOM_SEEK_FRONT,
                title = Res.string.uisetting_custom_seek_front_title,
                summary = Res.string.uisetting_custom_seek_front_summary,
                defaultValue = true,
                icon = Icons.Filled.Update,
            ),
            Setting.SliderSetting(
                type = SettingType.SliderSettingType,
                key = DataStoreKeys.PREF_INROOM_PLAYER_CUSTOM_SEEK_AMOUNT,
                title = Res.string.uisetting_custom_seek_amount_title,
                summary = Res.string.uisetting_custom_seek_amount_summary,
                defaultValue = 90,
                icon = Icons.Filled.Update,
                maxValue = 300,
                minValue = 30,
            ), Setting.SliderSetting(
                type = SettingType.SliderSettingType,
                key = DataStoreKeys.PREF_INROOM_PLAYER_SEEK_FORWARD_JUMP,
                title = Res.string.uisetting_seek_forward_jump_title,
                summary = Res.string.uisetting_seek_forward_jump_summary,
                defaultValue = 10,
                icon = Icons.Filled.FastForward,
                maxValue = 120,
                minValue = 1,
            ), Setting.SliderSetting(
                type = SettingType.SliderSettingType,
                key = DataStoreKeys.PREF_INROOM_PLAYER_SEEK_BACKWARD_JUMP,
                title = Res.string.uisetting_seek_backward_jump_title,
                summary = Res.string.uisetting_seek_backward_jump_summary,
                defaultValue = 10,
                icon = Icons.Filled.FastRewind,
                maxValue = 120,
                minValue = 1,
            )
        )
    )
    put(
        SettingCategory(
            keyID = CATEG_INROOM_ADVANCED,
            title = Res.string.settings_categ_advanced,
            icon = Icons.Filled.Stream
        ),
        listOf(
//          BooleanSetting(
//                    type = SettingType.ToggleSettingType,
//                    key = PREF_INROOM_PIP,
//                    title = lyricist
//                        .strings.uisettingPipTitle,
//                    summary = lyricist
//                        .strings.uisettingPipSummary,
//                    defaultValue = true,
//                    icon = Icons.Filled.PictureInPicture,
//                    styling = settingROOMstyle,
//                ),
            Setting.SliderSetting(
                type = SettingType.SliderSettingType,
                key = DataStoreKeys.PREF_INROOM_RECONNECTION_INTERVAL,
                title = Res.string.uisetting_reconnect_interval_title,
                summary = Res.string.uisetting_reconnect_interval_summary,
                defaultValue = 2,
                icon = Icons.Filled.Web,
                maxValue = 15,
                minValue = 0,
            ),
            Setting.YesNoDialogSetting(
                type = SettingType.OneClickSettingType,
                key = PREF_INROOM_RESET_DEFAULT,
                title = Res.string.uisetting_resetdefault_title,
                summary = Res.string.uisetting_resetdefault_summary,
                icon = Icons.Filled.ClearAll,
                rationale = Res.string.setting_resetdefault_dialog,
                onYes = {
                    launch(Dispatchers.IO) {
                        datastore.edit { preferences ->
                            preferences.clear()
                        }
                    }
                },
            )
        )
    )
}