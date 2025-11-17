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
import com.yuroyami.syncplay.managers.preferences.Preferences.CC_LANG
import com.yuroyami.syncplay.managers.preferences.Preferences.COLOR_ERRORMSG
import com.yuroyami.syncplay.managers.preferences.Preferences.COLOR_FRIENDTAG
import com.yuroyami.syncplay.managers.preferences.Preferences.COLOR_SELFTAG
import com.yuroyami.syncplay.managers.preferences.Preferences.COLOR_SYSTEMMSG
import com.yuroyami.syncplay.managers.preferences.Preferences.COLOR_TIMESTAMP
import com.yuroyami.syncplay.managers.preferences.Preferences.COLOR_USERMSG
import com.yuroyami.syncplay.managers.preferences.Preferences.CUSTOM_SEEK_AMOUNT
import com.yuroyami.syncplay.managers.preferences.Preferences.CUSTOM_SEEK_FRONT
import com.yuroyami.syncplay.managers.preferences.Preferences.DISPLAY_LANG
import com.yuroyami.syncplay.managers.preferences.Preferences.ERASE_SHORTCUTS
import com.yuroyami.syncplay.managers.preferences.Preferences.FILE_MISMATCH_WARNING
import com.yuroyami.syncplay.managers.preferences.Preferences.HASH_FILENAME
import com.yuroyami.syncplay.managers.preferences.Preferences.HASH_FILESIZE
import com.yuroyami.syncplay.managers.preferences.Preferences.MEDIA_DIRECTORIES
import com.yuroyami.syncplay.managers.preferences.Preferences.MSG_ACTIVATE_STAMP
import com.yuroyami.syncplay.managers.preferences.Preferences.MSG_BG_OPACITY
import com.yuroyami.syncplay.managers.preferences.Preferences.MSG_BOX_ACTION
import com.yuroyami.syncplay.managers.preferences.Preferences.MSG_FADING_DURATION
import com.yuroyami.syncplay.managers.preferences.Preferences.MSG_FONTSIZE
import com.yuroyami.syncplay.managers.preferences.Preferences.MSG_MAXCOUNT
import com.yuroyami.syncplay.managers.preferences.Preferences.MSG_OUTLINE
import com.yuroyami.syncplay.managers.preferences.Preferences.MSG_SHADOW
import com.yuroyami.syncplay.managers.preferences.Preferences.NETWORK_ENGINE
import com.yuroyami.syncplay.managers.preferences.Preferences.NEVER_SHOW_TIPS
import com.yuroyami.syncplay.managers.preferences.Preferences.PAUSE_ON_SOMEONE_LEAVE
import com.yuroyami.syncplay.managers.preferences.Preferences.READY_FIRST_HAND
import com.yuroyami.syncplay.managers.preferences.Preferences.RECONNECTION_INTERVAL
import com.yuroyami.syncplay.managers.preferences.Preferences.REMEMBER_INFO
import com.yuroyami.syncplay.managers.preferences.Preferences.SEEK_BACKWARD_JUMP
import com.yuroyami.syncplay.managers.preferences.Preferences.SEEK_FORWARD_JUMP
import com.yuroyami.syncplay.managers.preferences.Preferences.SUBTITLE_SIZE
import com.yuroyami.syncplay.managers.preferences.Preferences.TLS_ENABLE
import com.yuroyami.syncplay.managers.preferences.datastore
import com.yuroyami.syncplay.ui.popups.PopupMediaDirs.MediaDirsPopup
import com.yuroyami.syncplay.ui.theme.Theming
import com.yuroyami.syncplay.utils.PLATFORM
import com.yuroyami.syncplay.utils.platform
import com.yuroyami.syncplay.utils.platformCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.language_codes
import syncplaymobile.shared.generated.resources.language_names
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
                keyID = "global_general",
                title = Res.string.settings_categ_general,
                icon = Icons.Filled.SettingsSuggest
            ),
            listOf(
                Setting.BooleanSetting(
                    type = SettingType.CheckboxSettingType,
                    key = REMEMBER_INFO,
                    title = Res.string.setting_remember_join_info_title,
                    summary = Res.string.setting_remember_join_info_summary,
                    icon = Icons.Filled.Face
                ),
                Setting.BooleanSetting(
                    type = SettingType.CheckboxSettingType,
                    key = NEVER_SHOW_TIPS,
                    title = Res.string.setting_never_show_tips_title,
                    summary = Res.string.setting_never_show_tips_summary,
                    icon = Icons.Filled.Lightbulb
                ),
                Setting.YesNoDialogSetting(
                    type = SettingType.YesNoDialogSettingType,
                    key = ERASE_SHORTCUTS,
                    title = Res.string.setting_erase_shortcuts_title,
                    summary = Res.string.setting_erase_shortcuts_summary,
                    icon = Icons.Filled.BookmarkRemove,
                    rationale = Res.string.setting_erase_shortcuts_dialog,
                    onYes = {
                        platformCallback.onEraseConfigShortcuts()
                    }
                ),
                Setting.PopupSetting(
                    type = SettingType.PopupSettingType,
                    key = MEDIA_DIRECTORIES,
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
                keyID = "global_language",
                title = Res.string.settings_categ_language,
                icon = Icons.Filled.Translate,
            ),
            listOf(
                if (platform == PLATFORM.Android) {
                    Setting.MultiChoiceSetting(
                        type = SettingType.MultiChoicePopupSettingType,
                        key = DISPLAY_LANG,
                        title = Res.string.setting_display_language_title,
                        summary = Res.string.setting_display_language_summry,
                        icon = Icons.Filled.Translate,
                        entries = {
                            val langNames = stringArrayResource(Res.array.language_names)
                            val langCodes = stringArrayResource(Res.array.language_codes)
                            return@MultiChoiceSetting langNames.zip(langCodes).toMap()
                        },
                        onItemChosen = { v ->
                            platformCallback.onLanguageChanged(v)
                        }
                    )
                } else {
                    Setting.OneClickSetting(
                        type = SettingType.OneClickSettingType,
                        key = DISPLAY_LANG,
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
                    key = CC_LANG,
                    title = Res.string.setting_cc_default_language_title,
                    summary = Res.string.setting_cc_default_language_summry,
                    icon = Icons.Filled.ClosedCaptionOff,
                )
            )
        )

        put(
            SettingCategory(
                keyID = "global_syncing",
                title = Res.string.settings_categ_syncing,
                icon = Icons.Filled.ConnectWithoutContact
            ),
            listOf(
                Setting.BooleanSetting(
                    type = SettingType.CheckboxSettingType,
                    key = READY_FIRST_HAND,
                    title = Res.string.setting_ready_firsthand_title,
                    summary = Res.string.setting_ready_firsthand_summary,
                    icon = Icons.Filled.TaskAlt,
                ),
                Setting.BooleanSetting(
                    type = SettingType.CheckboxSettingType,
                    key = PAUSE_ON_SOMEONE_LEAVE,
                    title = Res.string.setting_pause_if_someone_left_title,
                    summary = Res.string.setting_pause_if_someone_left_summary,
                    icon = Icons.Filled.FrontHand,
                ),
                Setting.BooleanSetting(
                    type = SettingType.CheckboxSettingType,
                    key = FILE_MISMATCH_WARNING,
                    title = Res.string.setting_warn_file_mismatch_title,
                    summary = Res.string.setting_warn_file_mismatch_summary,
                    icon = Icons.Filled.ErrorOutline,
                ),
                Setting.MultiChoiceSetting(
                    type = SettingType.MultiChoicePopupSettingType,
                    key = HASH_FILENAME,
                    title = Res.string.setting_fileinfo_behaviour_name_title,
                    summary = Res.string.setting_fileinfo_behaviour_name_summary,
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
                    key = HASH_FILESIZE,
                    title = Res.string.setting_fileinfo_behaviour_size_title,
                    summary = Res.string.setting_fileinfo_behaviour_size_summary,
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
                keyID = "global_network",
                title = Res.string.settings_categ_network,
                icon = Icons.Filled.Hub
            ),
            listOf(
                Setting.BooleanSetting(
                    type = SettingType.ToggleSettingType,
                    key = TLS_ENABLE,
                    title = Res.string.setting_tls_title,
                    summary = Res.string.setting_tls_summary,
                    icon = Icons.Filled.Key,
                ),

                Setting.MultiChoiceSetting(
                    type = SettingType.MultiChoicePopupSettingType,
                    key = NETWORK_ENGINE,
                    title = Res.string.setting_network_engine_title,
                    summary = Res.string.setting_network_engine_summary,
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
                keyID = "global_advanced",
                title = Res.string.settings_categ_advanced,
                icon = Icons.Filled.Stream
            ),
            listOf(
                Setting.YesNoDialogSetting(
                    type = SettingType.OneClickSettingType,
                    key = "PREF_CLEAR_ALL", //TODO should offload to a string?
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
            keyID = "inroom_chatcolors",
            title = Res.string.uisetting_categ_chat_colors,
            icon = Icons.Filled.Palette,
        ),
        listOf(
            Setting.ColorSetting(
                type = SettingType.ColorSettingType,
                key = COLOR_TIMESTAMP,
                title = Res.string.uisetting_timestamp_color_title,
                summary = Res.string.uisetting_timestamp_summary,
                icon = Icons.Filled.Brush,
            ),
            Setting.ColorSetting(
                type = SettingType.ColorSettingType,
                key = COLOR_SELFTAG,
                title = Res.string.uisetting_self_color_title,
                summary = Res.string.uisetting_self_color_summary,
                icon = Icons.Filled.Brush,
            ), Setting.ColorSetting(
                type = SettingType.ColorSettingType,
                key = COLOR_FRIENDTAG,
                title = Res.string.uisetting_friend_color_title,
                summary = Res.string.uisetting_friend_color_summary,
                icon = Icons.Filled.Brush,
            ), Setting.ColorSetting(
                type = SettingType.ColorSettingType,
                key = COLOR_SYSTEMMSG,
                title = Res.string.uisetting_system_color_title,
                summary = Res.string.uisetting_system_color_summary,
                icon = Icons.Filled.Brush,
            ),
            Setting.ColorSetting(
                type = SettingType.ColorSettingType,
                key = COLOR_USERMSG,
                title = Res.string.uisetting_human_color_title,
                summary = Res.string.uisetting_human_color_summary,
                icon = Icons.Filled.Brush,
            ), Setting.ColorSetting(
                type = SettingType.ColorSettingType,
                key = COLOR_ERRORMSG,
                title = Res.string.uisetting_error_color_title,
                summary = Res.string.uisetting_error_color_summary,
                icon = Icons.Filled.Brush,
            )
        )
    )

    put(
        SettingCategory(
            keyID = "inroom_chat_properties",
            title = Res.string.uisetting_categ_chat_properties,
            icon = Icons.AutoMirrored.Filled.Chat
        ),
        listOf(
            Setting.BooleanSetting(
                type = SettingType.ToggleSettingType,
                key = MSG_ACTIVATE_STAMP,
                title = Res.string.uisetting_timestamp_title,
                summary = Res.string.uisetting_timestamp_summary,
                icon = Icons.Filled.Pin,
            ),
            Setting.BooleanSetting(
                type = SettingType.ToggleSettingType,
                key = MSG_OUTLINE,
                title = Res.string.uisetting_msgoutline_title,
                summary = Res.string.uisetting_msgoutline_summary,
                icon = Icons.Filled.BorderColor,
            ), Setting.BooleanSetting(
                type = SettingType.ToggleSettingType,
                key = MSG_SHADOW,
                title = Res.string.uisetting_msgshadow_title,
                summary = Res.string.uisetting_msgshadow_summary,
                icon = Icons.Filled.BorderColor,
            ), Setting.SliderSetting(
                type = SettingType.SliderSettingType,
                key = MSG_BG_OPACITY,
                title = Res.string.uisetting_messagery_alpha_title,
                summary = Res.string.uisetting_messagery_alpha_summary,
                icon = Icons.Filled.Opacity,
                maxValue = 255,
                minValue = 0,
            ), Setting.SliderSetting(
                type = SettingType.SliderSettingType,
                key = MSG_FONTSIZE,
                title = Res.string.uisetting_msgsize_title,
                summary = Res.string.uisetting_msgsize_summary,
                icon = Icons.Filled.FormatSize,
                maxValue = 28,
                minValue = 6,
            ), Setting.SliderSetting(
                type = SettingType.SliderSettingType,
                key = MSG_MAXCOUNT,
                title = Res.string.uisetting_msgcount_title,
                summary = Res.string.uisetting_msgcount_summary,
                icon = Icons.Filled.FormatListNumbered,
                maxValue = 30,
                minValue = 1,
            ), Setting.SliderSetting(
                type = SettingType.SliderSettingType,
                key = MSG_FADING_DURATION,
                title = Res.string.uisetting_msglife_title,
                summary = Res.string.uisetting_msglife_summary,
                icon = Icons.Filled.Timer,
                maxValue = 10,
                minValue = 1,
            ), Setting.BooleanSetting(
                type = SettingType.ToggleSettingType,
                key = MSG_BOX_ACTION,
                title = Res.string.uisetting_msgboxaction_title,
                summary = Res.string.uisetting_msgboxaction_summary,
                icon = Icons.Filled.Keyboard,
            )
        )
    )
    put(
        SettingCategory(
            keyID = "inroom_player_settings",
            title = Res.string.uisetting_categ_player_settings,
            icon = Icons.Filled.VideoLabel,
        ),
        listOf(
            Setting.SliderSetting(
                type = SettingType.SliderSettingType,
                key = SUBTITLE_SIZE,
                title = Res.string.uisetting_subtitle_size_title,
                summary = Res.string.uisetting_subtitle_size_summary,
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
                key = CUSTOM_SEEK_FRONT,
                title = Res.string.uisetting_custom_seek_front_title,
                summary = Res.string.uisetting_custom_seek_front_summary,
                icon = Icons.Filled.Update,
            ),
            Setting.SliderSetting(
                type = SettingType.SliderSettingType,
                key = CUSTOM_SEEK_AMOUNT,
                title = Res.string.uisetting_custom_seek_amount_title,
                summary = Res.string.uisetting_custom_seek_amount_summary,
                icon = Icons.Filled.Update,
                maxValue = 300,
                minValue = 30,
            ), Setting.SliderSetting(
                type = SettingType.SliderSettingType,
                key = SEEK_FORWARD_JUMP,
                title = Res.string.uisetting_seek_forward_jump_title,
                summary = Res.string.uisetting_seek_forward_jump_summary,
                icon = Icons.Filled.FastForward,
                maxValue = 120,
                minValue = 1,
            ), Setting.SliderSetting(
                type = SettingType.SliderSettingType,
                key = SEEK_BACKWARD_JUMP,
                title = Res.string.uisetting_seek_backward_jump_title,
                summary = Res.string.uisetting_seek_backward_jump_summary,
                icon = Icons.Filled.FastRewind,
                maxValue = 120,
                minValue = 1,
            )
        )
    )
    put(
        SettingCategory(
            keyID = "inroom_advanced",
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
                key = RECONNECTION_INTERVAL,
                title = Res.string.uisetting_reconnect_interval_title,
                summary = Res.string.uisetting_reconnect_interval_summary,
                icon = Icons.Filled.Web,
                maxValue = 15,
                minValue = 0,
            ),
            Setting.YesNoDialogSetting(
                type = SettingType.OneClickSettingType,
                key = "RESET_DEFAULT", //TODO should offload to a string?
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