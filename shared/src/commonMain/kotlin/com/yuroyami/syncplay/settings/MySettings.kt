package com.yuroyami.syncplay.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Chat
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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VideoLabel
import androidx.compose.material.icons.filled.VideoSettings
import androidx.compose.material.icons.filled.Web
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import com.yuroyami.syncplay.compose.popups.PopupMediaDirs.MediaDirsPopup
import com.yuroyami.syncplay.datastore.DataStoreKeys
import com.yuroyami.syncplay.datastore.DataStoreKeys.PREF_INROOM_COLOR_ERRORMSG
import com.yuroyami.syncplay.datastore.DataStoreKeys.PREF_INROOM_COLOR_FRIENDTAG
import com.yuroyami.syncplay.datastore.DataStoreKeys.PREF_INROOM_COLOR_SELFTAG
import com.yuroyami.syncplay.datastore.DataStoreKeys.PREF_INROOM_COLOR_SYSTEMMSG
import com.yuroyami.syncplay.datastore.DataStoreKeys.PREF_INROOM_COLOR_TIMESTAMP
import com.yuroyami.syncplay.datastore.DataStoreKeys.PREF_INROOM_COLOR_USERMSG
import com.yuroyami.syncplay.datastore.DataStoreKeys.PREF_INROOM_MSG_ACTIVATE_STAMP
import com.yuroyami.syncplay.datastore.DataStoreKeys.PREF_INROOM_MSG_BG_OPACITY
import com.yuroyami.syncplay.datastore.DataStoreKeys.PREF_INROOM_MSG_BOX_ACTION
import com.yuroyami.syncplay.datastore.DataStoreKeys.PREF_INROOM_MSG_FADING_DURATION
import com.yuroyami.syncplay.datastore.DataStoreKeys.PREF_INROOM_MSG_FONTSIZE
import com.yuroyami.syncplay.datastore.DataStoreKeys.PREF_INROOM_MSG_MAXCOUNT
import com.yuroyami.syncplay.datastore.DataStoreKeys.PREF_INROOM_MSG_OUTLINE
import com.yuroyami.syncplay.datastore.DataStoreKeys.PREF_INROOM_MSG_SHADOW
import com.yuroyami.syncplay.datastore.DataStoreKeys.PREF_INROOM_PIP
import com.yuroyami.syncplay.datastore.DataStoreKeys.PREF_REMEMBER_INFO
import com.yuroyami.syncplay.datastore.DataStoreKeys.PREF_SP_MEDIA_DIRS
import com.yuroyami.syncplay.locale.Localization
import com.yuroyami.syncplay.ui.Paletting
import com.yuroyami.syncplay.lyricist.langMap
import com.yuroyami.syncplay.watchroom.homeCallback
import com.yuroyami.syncplay.watchroom.isSoloMode
import com.yuroyami.syncplay.watchroom.player

object MySettings {

    @Composable
    fun globalSettings(): List<SettingCategory> {
        val ds = DataStoreKeys.DATASTORE_GLOBAL_SETTINGS

        val settingStyling = SettingStyling(
            titleFilling = listOf(Paletting.OLD_SP_YELLOW),
            titleShadow = Paletting.SP_GRADIENT,
            iconSize = 32f,
            iconTints = listOf(Paletting.OLD_SP_YELLOW),
            iconShadows = Paletting.SP_GRADIENT
        )

        return listOf(
            /** Setting Card Number 01: General */
            SettingCategory(
                title = "General",
                icon = Icons.Filled.SettingsSuggest,
                settingList = listOf(
                    Setting(
                        type = SettingType.CheckboxSetting,
                        title = { Localization.stringResource("setting_remember_join_info_title") },
                        summary = { Localization.stringResource("setting_remember_join_info_summary") },
                        defaultValue = true,
                        key = PREF_REMEMBER_INFO,
                        icon = Icons.Filled.Face,
                        datastorekey = ds,
                        styling = settingStyling
                    ),
                    Setting(
                        type = SettingType.PopupSetting,
                        title = { Localization.stringResource("media_directories") },
                        summary = { Localization.stringResource("media_directories_setting_summary") },
                        key = PREF_SP_MEDIA_DIRS,
                        icon = Icons.Filled.QueueMusic,
                        datastorekey = ds,
                        styling = settingStyling,
                        popupComposable = {
                                s -> MediaDirsPopup(s)
                        }
                    )
                )
            ),

            /** Setting Card N°02: Language */
            SettingCategory(
                title = "Language",
                icon = Icons.Filled.Translate,
                settingList = listOf(
                    Setting(
                        type = SettingType.MultiChoicePopupSetting,
                        title = { Localization.stringResource("setting_display_language_title") },
                        summary = {Localization.stringResource("setting_display_language_summry") },
                        defaultValue = "en",
                        key = DataStoreKeys.PREF_DISPLAY_LANG,
                        entryKeys = { langMap.keys.toList() },
                        entryValues = { langMap.keys.toList() },
                        icon = Icons.Filled.Translate,
                        onItemChosen = { _, v ->
                            homeCallback.onLanguageChanged(v)
                        },
                        datastorekey = ds,
                        styling = settingStyling,
                    ),
                    Setting(
                        type = SettingType.TextFieldSetting,
                        title = { Localization.stringResource("setting_audio_default_language_title") },
                        summary = { Localization.stringResource("setting_audio_default_language_summry") },
                        defaultValue = "und",
                        key = DataStoreKeys.PREF_AUDIO_LANG,
                        icon = Icons.Filled.GraphicEq,
                        datastorekey = ds,
                        styling = settingStyling,
                    ),
                    Setting(
                        type = SettingType.TextFieldSetting,
                        title = { Localization.stringResource("setting_cc_default_language_title") },
                        summary = { Localization.stringResource("setting_cc_default_language_summry") },
                        defaultValue = "eng",
                        key = DataStoreKeys.PREF_CC_LANG,
                        icon = Icons.Filled.ClosedCaptionOff,
                        datastorekey = ds,
                        styling = settingStyling,
                    )
                )
            ),

            /** Setting Card N°03: Syncing */
            SettingCategory(
                title = "Syncing",
                icon = Icons.Filled.ConnectWithoutContact,
                settingList = listOf(
                    Setting(
                        type = SettingType.CheckboxSetting,
                        title = { Localization.stringResource("setting_ready_firsthand_title") },
                        summary = { Localization.stringResource("setting_ready_firsthand_summary") },
                        defaultValue = true,
                        key = DataStoreKeys.PREF_READY_FIRST_HAND,
                        icon = Icons.Filled.TaskAlt,
                        datastorekey = ds,
                        styling = settingStyling,
                    ),
                    Setting(
                        type = SettingType.CheckboxSetting,
                        title = { Localization.stringResource("setting_pause_if_someone_left_title") },
                        summary = { Localization.stringResource("setting_pause_if_someone_left_summary") },
                        defaultValue = true,
                        key = DataStoreKeys.PREF_PAUSE_ON_SOMEONE_LEAVE,
                        icon = Icons.Filled.FrontHand,
                        datastorekey = ds,
                        styling = settingStyling,
                    ),
                    Setting(
                        type = SettingType.CheckboxSetting,
                        title = { Localization.stringResource("setting_warn_file_mismatch_title") },
                        summary = { Localization.stringResource("setting_warn_file_mismatch_summary") },
                        defaultValue = true,
                        key = DataStoreKeys.PREF_FILE_MISMATCH_WARNING,
                        icon = Icons.Filled.ErrorOutline,
                        datastorekey = ds,
                        styling = settingStyling,
                    ),
                    Setting(
                        type = SettingType.MultiChoicePopupSetting,
                        title = { Localization.stringResource("setting_fileinfo_behaviour_name_title") },
                        summary = { Localization.stringResource("setting_fileinfo_behaviour_name_summary") },
                        key = DataStoreKeys.PREF_HASH_FILENAME,
                        defaultValue = "1",
                        entryKeys = {
                            listOf(
                                Localization.stringResource("setting_fileinfoBehavior_a"),
                                Localization.stringResource("setting_fileinfoBehavior_b"),
                                Localization.stringResource("setting_fileinfoBehavior_c")
                            )
                        },
                        entryValues = { listOf("1", "2", "3") },
                        icon = Icons.Filled.DesignServices,
                        datastorekey = ds,
                        styling = settingStyling,
                    ),
                    Setting(
                        type = SettingType.MultiChoicePopupSetting,
                        title = { Localization.stringResource("setting_fileinfo_behaviour_size_title") },
                        summary = { Localization.stringResource("setting_fileinfo_behaviour_size_summary") },
                        key = DataStoreKeys.PREF_HASH_FILESIZE,
                        defaultValue = "1",
                        entryKeys = {
                            listOf(
                                Localization.stringResource("setting_fileinfoBehavior_a"),
                                Localization.stringResource("setting_fileinfoBehavior_b"),
                                Localization.stringResource("setting_fileinfoBehavior_c")
                            )
                        },
                        entryValues = { listOf("1", "2", "3") },
                        icon = Icons.Filled.DesignServices,
                        datastorekey = ds,
                        styling = settingStyling,
                    )
                )
            ),


            SettingCategory(
                title = "Exoplayer",
                icon = Icons.Filled.VideoSettings,
                settingList = listOf(
                    Setting(
                        type = SettingType.SliderSetting,
                        title = { Localization.stringResource("setting_max_buffer_title") },
                        summary = { Localization.stringResource("setting_max_buffer_summary") },
                        defaultValue = 30,
                        minValue = 1,
                        maxValue = 60,
                        key = DataStoreKeys.PREF_MAX_BUFFER,
                        icon = Icons.Filled.HourglassTop,
                        datastorekey = ds,
                        styling = settingStyling,
                    ),
                    Setting(
                        type = SettingType.SliderSetting,
                        title = { Localization.stringResource("setting_min_buffer_title") },
                        summary = { Localization.stringResource("setting_min_buffer_summary") },
                        defaultValue = 15,
                        minValue = 1,
                        maxValue = 30,
                        key = DataStoreKeys.PREF_MIN_BUFFER,
                        icon = Icons.Filled.HourglassBottom,
                        datastorekey = ds,
                        styling = settingStyling,
                    ),
                    Setting(
                        type = SettingType.SliderSetting,
                        title = { Localization.stringResource("setting_playback_buffer_title") },
                        summary = { Localization.stringResource("setting_playback_buffer_summary") },
                        defaultValue = 2500,
                        minValue = 100,
                        maxValue = 15000,
                        key = DataStoreKeys.PREF_SEEK_BUFFER,
                        icon = Icons.Filled.HourglassEmpty,
                        datastorekey = ds,
                        styling = settingStyling,
                    )

                )
            ),

            SettingCategory(
                title = "Network",
                icon = Icons.Filled.Hub,
                settingList = listOf(
                    Setting(
                        type = SettingType.ToggleSetting,
                        title = { Localization.stringResource("setting_tls_title") },
                        summary = { Localization.stringResource("setting_tls_summary") },
                        defaultValue = false,
                        key = "tls",
                        enabled = false,
                        icon = Icons.Filled.Key,
                        datastorekey = ds,
                        styling = settingStyling,
                    )
                )
            ),

            SettingCategory(
                title = "Advanced",
                icon = Icons.Filled.Stream,
                settingList = listOf(
                    Setting(
                        type = SettingType.OneClickSetting,
                        title = { Localization.stringResource("setting_resetdefault_title") },
                        summary = { Localization.stringResource("setting_resetdefault_summary") },
                        isResetDefault = true,
                        key = "reset_default",
                        icon = Icons.Filled.ClearAll,
                        datastorekey = ds,
                        styling = settingStyling,
                    )
                )
            ),
        )
    }

    fun inRoomPreferences(): List<SettingCategory> {
        val ds = DataStoreKeys.DATASTORE_INROOM_PREFERENCES

        val ss = SettingStyling(
            titleFilling = listOf(Paletting.OLD_SP_YELLOW),
            titleShadow = Paletting.SP_GRADIENT,
            titleSize = 11f,
            summarySize = 8f,
            iconTints = listOf(Paletting.OLD_SP_YELLOW),
            iconShadows = Paletting.SP_GRADIENT
        )

        val list = mutableListOf<SettingCategory>()

        if (!isSoloMode) {
            list.add(
                SettingCategory(
                    title = "Chat Colors",
                    icon = Icons.Filled.Palette,
                    settingList = listOf(
                        Setting(
                            type = SettingType.ColorSetting,
                            title = { Localization.stringResource("uisetting_timestamp_color_title") },
                            summary = { Localization.stringResource("uisetting_timestamp_color_summary") },
                            defaultValue = Paletting.MSG_TIMESTAMP.toArgb(),
                            key = PREF_INROOM_COLOR_TIMESTAMP,
                            icon = Icons.Filled.Brush,
                            datastorekey = ds,
                            styling = ss,
                        ),
                        Setting(
                            type = SettingType.ColorSetting,
                            title = { Localization.stringResource("uisetting_self_color_title") },
                            summary = { Localization.stringResource("uisetting_self_color_summary") },
                            defaultValue = Paletting.MSG_SELF_TAG.toArgb(),
                            key = PREF_INROOM_COLOR_SELFTAG,
                            icon = Icons.Filled.Brush,
                            datastorekey = ds,
                            styling = ss,
                        ),
                        Setting(
                            type = SettingType.ColorSetting,
                            title = { Localization.stringResource("uisetting_friend_color_title") },
                            summary = { Localization.stringResource("uisetting_friend_color_summary") },
                            defaultValue = Paletting.MSG_FRIEND_TAG.toArgb(),
                            key = PREF_INROOM_COLOR_FRIENDTAG,
                            icon = Icons.Filled.Brush,
                            datastorekey = ds,
                            styling = ss,
                        ),
                        Setting(
                            type = SettingType.ColorSetting,
                            title = { Localization.stringResource("uisetting_system_color_title") },
                            summary = { Localization.stringResource("uisetting_system_color_summary") },
                            defaultValue = Paletting.MSG_SYSTEM.toArgb(),
                            key = PREF_INROOM_COLOR_SYSTEMMSG,
                            icon = Icons.Filled.Brush,
                            datastorekey = ds,
                            styling = ss,
                        ),
                        Setting(
                            type = SettingType.ColorSetting,
                            title = { Localization.stringResource("uisetting_human_color_title") },
                            summary = { Localization.stringResource("uisetting_human_color_summary") },
                            defaultValue = Paletting.MSG_CHAT.toArgb(),
                            key = PREF_INROOM_COLOR_USERMSG,
                            icon = Icons.Filled.Brush,
                            datastorekey = ds,
                            styling = ss,
                        ),
                        Setting(
                            type = SettingType.ColorSetting,
                            title = { Localization.stringResource("uisetting_error_color_title") },
                            summary = { Localization.stringResource("uisetting_error_color_summary") },
                            defaultValue = Paletting.MSG_ERROR.toArgb(),
                            key = PREF_INROOM_COLOR_ERRORMSG,
                            icon = Icons.Filled.Brush,
                            datastorekey = ds,
                            styling = ss,
                        ),

                        )
                )
            )

            list.add(
                SettingCategory(
                    title = "Chat Properties",
                    icon = Icons.Filled.Chat,
                    settingList = listOf(
                        Setting(
                            type = SettingType.ToggleSetting,
                            title = { Localization.stringResource("uisetting_timestamp_title") },
                            summary = { Localization.stringResource("uisetting_timestamp_summary") },
                            defaultValue = true,
                            key = PREF_INROOM_MSG_ACTIVATE_STAMP,
                            icon = Icons.Filled.Pin,
                            datastorekey = ds,
                            styling = ss,
                        ),
                        Setting(
                            type = SettingType.ToggleSetting,
                            title = { Localization.stringResource("uisetting_msgoutline_title") },
                            summary = { Localization.stringResource("uisetting_msgoutline_title") },
                            defaultValue = true,
                            key = PREF_INROOM_MSG_OUTLINE,
                            icon = Icons.Filled.BorderColor,
                            datastorekey = ds,
                            styling = ss,
                        ),
                        Setting(
                            type = SettingType.ToggleSetting,
                            title = { Localization.stringResource("uisetting_msgshadow_title") },
                            summary = { Localization.stringResource("uisetting_msgshadow_title") },
                            defaultValue = false,
                            key = PREF_INROOM_MSG_SHADOW,
                            icon = Icons.Filled.BorderColor,
                            datastorekey = ds,
                            styling = ss,
                        ),
                        Setting(
                            type = SettingType.SliderSetting,
                            title = { Localization.stringResource("uisetting_messagery_alpha_title") },
                            summary = { Localization.stringResource("uisetting_messagery_alpha_summary") },
                            defaultValue = 0,
                            minValue = 0,
                            maxValue = 255,
                            key = PREF_INROOM_MSG_BG_OPACITY,
                            icon = Icons.Filled.Opacity,
                            datastorekey = ds,
                            styling = ss,
                        ),
                        Setting(
                            type = SettingType.SliderSetting,
                            title = { Localization.stringResource("uisetting_msgsize_title") },
                            summary = { Localization.stringResource("uisetting_msgsize_summary") },
                            defaultValue = 9,
                            minValue = 6,
                            maxValue = 28,
                            key = PREF_INROOM_MSG_FONTSIZE,
                            icon = Icons.Filled.FormatSize,
                            datastorekey = ds,
                            styling = ss,
                        ),
                        Setting(
                            type = SettingType.SliderSetting,
                            title = { Localization.stringResource("uisetting_msgcount_title") },
                            summary = { Localization.stringResource("uisetting_msgcount_summary") },
                            defaultValue = 10,
                            minValue = 1,
                            maxValue = 30,
                            key = PREF_INROOM_MSG_MAXCOUNT,
                            icon = Icons.Filled.FormatListNumbered,
                            datastorekey = ds,
                            styling = ss,
                        ),
                        Setting(
                            type = SettingType.SliderSetting,
                            title = { Localization.stringResource("uisetting_msglife_title") },
                            summary = { Localization.stringResource("uisetting_msglife_summary") },
                            defaultValue = 3,
                            minValue = 1,
                            maxValue = 10,
                            key = PREF_INROOM_MSG_FADING_DURATION,
                            icon = Icons.Filled.Timer,
                            datastorekey = ds,
                            styling = ss,
                        ),
                        Setting(
                            type = SettingType.ToggleSetting,
                            title = { Localization.stringResource("uisetting_msgboxaction_title") },
                            summary = { Localization.stringResource("uisetting_msgboxaction_summary") },
                            defaultValue = true,
                            key = PREF_INROOM_MSG_BOX_ACTION,
                            icon = Icons.Filled.Keyboard,
                            datastorekey = ds,
                            styling = ss,
                        ),
                    )
                )
            )
        }

        list.add(
            SettingCategory(
                title = "Player Settings",
                icon = Icons.Filled.VideoLabel,
                settingList = listOf(
                    Setting(
                        type = SettingType.SliderSetting,
                        title = { Localization.stringResource("uisetting_subtitle_size_title") },
                        summary = { Localization.stringResource("uisetting_subtitle_size_summary") },
                        defaultValue = 16,
                        minValue = 2,
                        maxValue = 200,
                        onValueChanged = { v ->
                            player?.changeSubtitleSize(v)
                        },
                        key = DataStoreKeys.PREF_INROOM_PLAYER_SUBTITLE_SIZE,
                        icon = Icons.Filled.SortByAlpha,
                        datastorekey = ds,
                        styling = ss,
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
                    Setting(
                        type = SettingType.SliderSetting,
                        title = { Localization.stringResource("uisetting_seek_forward_jump_title") },
                        summary = { Localization.stringResource("uisetting_seek_forward_jump_summary") },
                        defaultValue = 10,
                        minValue = 1,
                        maxValue = 120,
                        key = DataStoreKeys.PREF_INROOM_PLAYER_SEEK_FORWARD_JUMP,
                        icon = Icons.Filled.FastForward,
                        datastorekey = ds,
                        styling = ss,
                    ),
                    Setting(
                        type = SettingType.SliderSetting,
                        title = { Localization.stringResource("uisetting_seek_backward_jump_title") },
                        summary = { Localization.stringResource("uisetting_seek_backward_jump_summary") },
                        defaultValue = 10,
                        minValue = 1,
                        maxValue = 120,
                        key = DataStoreKeys.PREF_INROOM_PLAYER_SEEK_BACKWARD_JUMP,
                        icon = Icons.Filled.FastRewind,
                        datastorekey = ds,
                        styling = ss,
                    ),
                )
            )
        )


        list.add(
            SettingCategory(
                title = "Advanced",
                icon = Icons.Filled.Stream,
                settingList = listOf(
                    Setting(
                        type = SettingType.ToggleSetting,
                        title = { Localization.stringResource("uisetting_pip_title") },
                        summary = { Localization.stringResource("uisetting_pip_title") },
                        defaultValue = true,
                        key = PREF_INROOM_PIP,
                        icon = Icons.Filled.PictureInPicture,
                        datastorekey = ds,
                        styling = ss,
                    ),
                    Setting(
                        type = SettingType.SliderSetting,
                        title = { Localization.stringResource("uisetting_reconnect_interval_title") } ,
                        summary = { Localization.stringResource("uisetting_reconnect_interval_summary") },
                        defaultValue = 2,
                        minValue = 0,
                        maxValue = 15,
                        key = DataStoreKeys.PREF_INROOM_RECONNECTION_INTERVAL,
                        icon = Icons.Filled.Web,
                        datastorekey = ds,
                        styling = ss,
                    ),
                    Setting(
                        type = SettingType.OneClickSetting,
                        title = { Localization.stringResource("uisetting_resetdefault_title") },
                        summary = { Localization.stringResource("uisetting_resetdefault_summary") },
                        isResetDefault = true,
                        key = "RESET",
                        icon = Icons.Filled.ClearAll,
                        datastorekey = ds,
                        styling = ss,
                    )
                )
            )
        )

        return list
    }

}