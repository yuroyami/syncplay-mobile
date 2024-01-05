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
import com.yuroyami.syncplay.lyricist.langMap
import com.yuroyami.syncplay.lyricist.rememberStrings
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_INROOM_COLOR_ERRORMSG
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_INROOM_COLOR_FRIENDTAG
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_INROOM_COLOR_SELFTAG
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_INROOM_COLOR_SYSTEMMSG
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_INROOM_COLOR_TIMESTAMP
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_INROOM_COLOR_USERMSG
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_INROOM_MSG_ACTIVATE_STAMP
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_INROOM_MSG_BG_OPACITY
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_INROOM_MSG_BOX_ACTION
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_INROOM_MSG_FADING_DURATION
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_INROOM_MSG_FONTSIZE
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_INROOM_MSG_MAXCOUNT
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_INROOM_MSG_OUTLINE
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_INROOM_MSG_SHADOW
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_INROOM_PIP
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_REMEMBER_INFO
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_SP_MEDIA_DIRS
import com.yuroyami.syncplay.ui.Paletting
import com.yuroyami.syncplay.watchroom.homeCallback
import com.yuroyami.syncplay.watchroom.isSoloMode
import com.yuroyami.syncplay.watchroom.viewmodel

object MySettings {

    @Composable
    fun globalSettings(): List<SettingCategory> {
        val localz = rememberStrings()

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
                title = localz.strings.settingsCategGeneral,
                icon = Icons.Filled.SettingsSuggest,
                settingList = listOf(
                    Setting(
                        type = SettingType.CheckboxSetting,
                        key = PREF_REMEMBER_INFO,
                        title = { localz.strings.settingRememberJoinInfoTitle },
                        summary = { localz.strings.settingRememberJoinInfoSummary },
                        defaultValue = true,
                        icon = Icons.Filled.Face,
                        styling = settingStyling
                    ),
                    Setting(
                        type = SettingType.PopupSetting,
                        key = PREF_SP_MEDIA_DIRS,
                        title = { localz.strings.mediaDirectories },
                        summary = { localz.strings.mediaDirectoriesSettingSummary },
                        icon = Icons.Filled.QueueMusic,
                        styling = settingStyling,
                        popupComposable = { s ->
                            MediaDirsPopup(s)
                        }
                    )
                )
            ),

            /** Setting Card N°02: Language */
            SettingCategory(
                title = localz.strings.settingsCategLanguage,
                icon = Icons.Filled.Translate,
                settingList = listOf(
                    Setting(
                        type = SettingType.MultiChoicePopupSetting,
                        key = DataStoreKeys.PREF_DISPLAY_LANG,
                        title = { localz.strings.settingDisplayLanguageTitle },
                        summary = { localz.strings.settingDisplayLanguageSummry },
                        defaultValue = "en",
                        icon = Icons.Filled.Translate,
                        styling = settingStyling,
                        entryKeys = { langMap.keys.toList() },
                        entryValues = { langMap.values.toList() },
                        onItemChosen = { _, v ->
                            homeCallback?.onLanguageChanged(v)
                        },
                    ),
                    Setting(
                        type = SettingType.TextFieldSetting,
                        key = DataStoreKeys.PREF_AUDIO_LANG,
                        title = { localz.strings.settingAudioDefaultLanguageTitle },
                        summary = { localz.strings.settingAudioDefaultLanguageSummry },
                        defaultValue = "und",
                        icon = Icons.Filled.GraphicEq,
                        styling = settingStyling,
                    ),
                    Setting(
                        type = SettingType.TextFieldSetting,
                        key = DataStoreKeys.PREF_CC_LANG,
                        title = { localz.strings.settingCcDefaultLanguageTitle },
                        summary = { localz.strings.settingCcDefaultLanguageSummry },
                        defaultValue = "eng",
                        icon = Icons.Filled.ClosedCaptionOff,
                        styling = settingStyling,
                    )
                )
            ),

            /** Setting Card N°03: Syncing */
            SettingCategory(
                title = localz.strings.settingsCategSyncing,
                icon = Icons.Filled.ConnectWithoutContact,
                settingList = listOf(
                    Setting(
                        type = SettingType.CheckboxSetting,
                        key = DataStoreKeys.PREF_READY_FIRST_HAND,
                        title = { localz.strings.settingReadyFirsthandTitle },
                        summary = { localz.strings.settingReadyFirsthandSummary },
                        defaultValue = true,
                        icon = Icons.Filled.TaskAlt,
                        styling = settingStyling,
                    ),
                    Setting(
                        type = SettingType.CheckboxSetting,
                        key = DataStoreKeys.PREF_PAUSE_ON_SOMEONE_LEAVE,
                        title = { localz.strings.settingPauseIfSomeoneLeftTitle },
                        summary = { localz.strings.settingPauseIfSomeoneLeftSummary },
                        defaultValue = true,
                        icon = Icons.Filled.FrontHand,
                        styling = settingStyling,
                    ),
                    Setting(
                        type = SettingType.CheckboxSetting,
                        key = DataStoreKeys.PREF_FILE_MISMATCH_WARNING,
                        title = { localz.strings.settingWarnFileMismatchTitle },
                        summary = { localz.strings.settingWarnFileMismatchSummary },
                        defaultValue = true,
                        icon = Icons.Filled.ErrorOutline,
                        styling = settingStyling,
                    ),
                    Setting(
                        type = SettingType.MultiChoicePopupSetting,
                        key = DataStoreKeys.PREF_HASH_FILENAME,
                        title = { localz.strings.settingFileinfoBehaviourNameTitle },
                        summary = { localz.strings.settingFileinfoBehaviourNameSummary },
                        defaultValue = "1",
                        icon = Icons.Filled.DesignServices,
                        styling = settingStyling,
                        entryKeys = {
                            listOf(
                                localz.strings.settingFileinfoBehaviorA,
                                localz.strings.settingFileinfoBehaviorB,
                                localz.strings.settingFileinfoBehaviorC
                            )
                        },
                        entryValues = { listOf("1", "2", "3") },
                    ),
                    Setting(
                        type = SettingType.MultiChoicePopupSetting,
                        key = DataStoreKeys.PREF_HASH_FILESIZE,
                        title = { localz.strings.settingFileinfoBehaviourSizeTitle },
                        summary = { localz.strings.settingFileinfoBehaviourSizeSummary },
                        defaultValue = "1",
                        icon = Icons.Filled.DesignServices,
                        styling = settingStyling,
                        entryKeys = {
                            listOf(
                                localz.strings.settingFileinfoBehaviorA,
                                localz.strings.settingFileinfoBehaviorB,
                                localz.strings.settingFileinfoBehaviorC
                            )
                        },
                        entryValues = { listOf("1", "2", "3") },
                    )
                )
            ),


            SettingCategory(
                title = localz.strings.settingsCategExoplayer,
                icon = Icons.Filled.VideoSettings,
                settingList = listOf(
                    Setting(
                        type = SettingType.SliderSetting,
                        key = DataStoreKeys.PREF_MAX_BUFFER,
                        title = { localz.strings.settingMaxBufferTitle },
                        summary = { localz.strings.settingMaxBufferSummary },
                        defaultValue = 30,
                        icon = Icons.Filled.HourglassTop,
                        styling = settingStyling,
                        maxValue = 60,
                        minValue = 1,
                    ),
                    Setting(
                        type = SettingType.SliderSetting,
                        key = DataStoreKeys.PREF_MIN_BUFFER,
                        title = { localz.strings.settingMinBufferTitle },
                        summary = { localz.strings.settingMinBufferSummary },
                        defaultValue = 15,
                        icon = Icons.Filled.HourglassBottom,
                        styling = settingStyling,
                        maxValue = 30,
                        minValue = 1,
                    ),
                    Setting(
                        type = SettingType.SliderSetting,
                        key = DataStoreKeys.PREF_SEEK_BUFFER,
                        title = { localz.strings.settingPlaybackBufferTitle },
                        summary = { localz.strings.settingPlaybackBufferSummary },
                        defaultValue = 2500,
                        icon = Icons.Filled.HourglassEmpty,
                        styling = settingStyling,
                        maxValue = 15000,
                        minValue = 100,
                    )

                )
            ),

            SettingCategory(
                title = localz.strings.settingsCategNetwork,
                icon = Icons.Filled.Hub,
                settingList = listOf(
                    Setting(
                        type = SettingType.ToggleSetting,
                        key = DataStoreKeys.PREF_TLS_ENABLE,
                        title = { localz.strings.settingTlsTitle },
                        summary = { localz.strings.settingTlsSummary },
                        defaultValue = true,
                        icon = Icons.Filled.Key,
                        styling = settingStyling,
                    )
                )
            ),

            SettingCategory(
                title = localz.strings.settingsCategAdvanced,
                icon = Icons.Filled.Stream,
                settingList = listOf(
                    Setting(
                        type = SettingType.OneClickSetting,
                        key = DataStoreKeys.PREF_GLOBAL_CLEAR_ALL,
                        title = { localz.strings.settingResetdefaultTitle },
                        summary = { localz.strings.settingResetdefaultSummary },
                        icon = Icons.Filled.ClearAll,
                        styling = settingStyling,
                        isResetDefault = true,
                    )
                )
            ),
        )
    }

    @Composable
    fun inRoomPreferences(): List<SettingCategory> {
        val localz = rememberStrings()

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
                    title = localz.strings.uisettingCategChatColors,
                    icon = Icons.Filled.Palette,
                    settingList = listOf(
                        Setting(
                            type = SettingType.ColorSetting,
                            key = PREF_INROOM_COLOR_TIMESTAMP,
                            title = { localz.strings.uisettingTimestampColorTitle },
                            summary = { localz.strings.uisettingTimestampSummary },
                            defaultValue = Paletting.MSG_TIMESTAMP.toArgb(),
                            icon = Icons.Filled.Brush,
                            styling = ss,
                        ),
                        Setting(
                            type = SettingType.ColorSetting,
                            key = PREF_INROOM_COLOR_SELFTAG,
                            title = { localz.strings.uisettingSelfColorTitle },
                            summary = { localz.strings.uisettingSelfColorSummary },
                            defaultValue = Paletting.MSG_SELF_TAG.toArgb(),
                            icon = Icons.Filled.Brush,
                            styling = ss,
                        ),
                        Setting(
                            type = SettingType.ColorSetting,
                            key = PREF_INROOM_COLOR_FRIENDTAG,
                            title = { localz.strings.uisettingFriendColorTitle },
                            summary = { localz.strings.uisettingFriendColorSummary },
                            defaultValue = Paletting.MSG_FRIEND_TAG.toArgb(),
                            icon = Icons.Filled.Brush,
                            styling = ss,
                        ),
                        Setting(
                            type = SettingType.ColorSetting,
                            key = PREF_INROOM_COLOR_SYSTEMMSG,
                            title = { localz.strings.uisettingSystemColorTitle },
                            summary = { localz.strings.uisettingSystemColorSummary },
                            defaultValue = Paletting.MSG_SYSTEM.toArgb(),
                            icon = Icons.Filled.Brush,
                            styling = ss,
                        ),
                        Setting(
                            type = SettingType.ColorSetting,
                            key = PREF_INROOM_COLOR_USERMSG,
                            title = { localz.strings.uisettingHumanColorTitle },
                            summary = { localz.strings.uisettingHumanColorSummary },
                            defaultValue = Paletting.MSG_CHAT.toArgb(),
                            icon = Icons.Filled.Brush,
                            styling = ss,
                        ),
                        Setting(
                            type = SettingType.ColorSetting,
                            key = PREF_INROOM_COLOR_ERRORMSG,
                            title = { localz.strings.uisettingErrorColorTitle },
                            summary = { localz.strings.uisettingErrorColorSummary },
                            defaultValue = Paletting.MSG_ERROR.toArgb(),
                            icon = Icons.Filled.Brush,
                            styling = ss,
                        ),

                        )
                )
            )

            list.add(
                SettingCategory(
                    title = localz.strings.uisettingCategChatProperties,
                    icon = Icons.Filled.Chat,
                    settingList = listOf(
                        Setting(
                            type = SettingType.ToggleSetting,
                            key = PREF_INROOM_MSG_ACTIVATE_STAMP,
                            title = { localz.strings.uisettingTimestampTitle },
                            summary = { localz.strings.uisettingTimestampSummary },
                            defaultValue = true,
                            icon = Icons.Filled.Pin,
                            styling = ss,
                        ),
                        Setting(
                            type = SettingType.ToggleSetting,
                            key = PREF_INROOM_MSG_OUTLINE,
                            title = { localz.strings.uisettingMsgoutlineTitle },
                            summary = { localz.strings.uisettingMsgoutlineSummary },
                            defaultValue = true,
                            icon = Icons.Filled.BorderColor,
                            styling = ss,
                        ),
                        Setting(
                            type = SettingType.ToggleSetting,
                            key = PREF_INROOM_MSG_SHADOW,
                            title = { localz.strings.uisettingMsgshadowTitle },
                            summary = { localz.strings.uisettingMsgshadowSummary },
                            defaultValue = false,
                            icon = Icons.Filled.BorderColor,
                            styling = ss,
                        ),
                        Setting(
                            type = SettingType.SliderSetting,
                            key = PREF_INROOM_MSG_BG_OPACITY,
                            title = { localz.strings.uisettingMessageryAlphaTitle },
                            summary = { localz.strings.uisettingMessageryAlphaSummary },
                            defaultValue = 0,
                            icon = Icons.Filled.Opacity,
                            styling = ss,
                            maxValue = 255,
                            minValue = 0,
                        ),
                        Setting(
                            type = SettingType.SliderSetting,
                            key = PREF_INROOM_MSG_FONTSIZE,
                            title = { localz.strings.uisettingMsgsizeTitle },
                            summary = { localz.strings.uisettingMsgsizeSummary },
                            defaultValue = 9,
                            icon = Icons.Filled.FormatSize,
                            styling = ss,
                            maxValue = 28,
                            minValue = 6,
                        ),
                        Setting(
                            type = SettingType.SliderSetting,
                            key = PREF_INROOM_MSG_MAXCOUNT,
                            title = { localz.strings.uisettingMsgcountTitle },
                            summary = { localz.strings.uisettingMsgcountSummary },
                            defaultValue = 10,
                            icon = Icons.Filled.FormatListNumbered,
                            styling = ss,
                            maxValue = 30,
                            minValue = 1,
                        ),
                        Setting(
                            type = SettingType.SliderSetting,
                            key = PREF_INROOM_MSG_FADING_DURATION,
                            title = { localz.strings.uisettingMsglifeTitle },
                            summary = { localz.strings.uisettingMsglifeSummary },
                            defaultValue = 3,
                            icon = Icons.Filled.Timer,
                            styling = ss,
                            maxValue = 10,
                            minValue = 1,
                        ),
                        Setting(
                            type = SettingType.ToggleSetting,
                            key = PREF_INROOM_MSG_BOX_ACTION,
                            title = { localz.strings.uisettingMsgboxactionTitle },
                            summary = { localz.strings.uisettingMsgboxactionSummary },
                            defaultValue = true,
                            icon = Icons.Filled.Keyboard,
                            styling = ss,
                        ),
                    )
                )
            )
        }

        list.add(
            SettingCategory(
                title = localz.strings.uisettingCategPlayerSettings,
                icon = Icons.Filled.VideoLabel,
                settingList = listOf(
                    Setting(
                        type = SettingType.SliderSetting,
                        key = DataStoreKeys.PREF_INROOM_PLAYER_SUBTITLE_SIZE,
                        title = { localz.strings.uisettingSubtitleSizeTitle },
                        summary = { localz.strings.uisettingSubtitleSizeSummary },
                        defaultValue = 16,
                        icon = Icons.Filled.SortByAlpha,
                        styling = ss,
                        maxValue = 200,
                        minValue = 2,
                        onValueChanged = { v ->
                            viewmodel?.player?.changeSubtitleSize(v)
                        },
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
                        key = DataStoreKeys.PREF_INROOM_PLAYER_SEEK_FORWARD_JUMP,
                        title = { localz.strings.uisettingSeekForwardJumpTitle },
                        summary = { localz.strings.uisettingSeekForwardJumpSummary },
                        defaultValue = 10,
                        icon = Icons.Filled.FastForward,
                        styling = ss,
                        maxValue = 120,
                        minValue = 1,
                    ),
                    Setting(
                        type = SettingType.SliderSetting,
                        key = DataStoreKeys.PREF_INROOM_PLAYER_SEEK_BACKWARD_JUMP,
                        title = { localz.strings.uisettingSeekBackwardJumpTitle },
                        summary = { localz.strings.uisettingSeekBackwardJumpSummary },
                        defaultValue = 10,
                        icon = Icons.Filled.FastRewind,
                        styling = ss,
                        maxValue = 120,
                        minValue = 1,
                    ),
                )
            )
        )


        list.add(
            SettingCategory(
                title = localz.strings.settingsCategAdvanced,
                icon = Icons.Filled.Stream,
                settingList = listOf(
                    Setting(
                        type = SettingType.ToggleSetting,
                        key = PREF_INROOM_PIP,
                        title = { localz.strings.uisettingPipTitle },
                        summary = { localz.strings.uisettingPipSummary },
                        defaultValue = true,
                        icon = Icons.Filled.PictureInPicture,
                        styling = ss,
                    ),
                    Setting(
                        type = SettingType.SliderSetting,
                        key = DataStoreKeys.PREF_INROOM_RECONNECTION_INTERVAL,
                        title = { localz.strings.uisettingReconnectIntervalTitle },
                        summary = { localz.strings.uisettingReconnectIntervalSummary },
                        defaultValue = 2,
                        icon = Icons.Filled.Web,
                        styling = ss,
                        maxValue = 15,
                        minValue = 0,
                    ),
                    Setting(
                        type = SettingType.OneClickSetting,
                        key = "RESET",
                        title = { localz.strings.uisettingResetdefaultTitle },
                        summary = { localz.strings.uisettingResetdefaultSummary },
                        icon = Icons.Filled.ClearAll,
                        styling = ss,
                        isResetDefault = true,
                    )
                )
            )
        )
        return list
    }
}