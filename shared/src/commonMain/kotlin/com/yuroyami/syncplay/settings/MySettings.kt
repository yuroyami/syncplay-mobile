package com.yuroyami.syncplay.settings

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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.VideoLabel
import androidx.compose.material.icons.filled.VideoSettings
import androidx.compose.material.icons.filled.Web
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.preferences.core.edit
import com.yuroyami.syncplay.compose.popups.PopupMediaDirs.MediaDirsPopup
import com.yuroyami.syncplay.lyricist.langMap
import com.yuroyami.syncplay.player.BasePlayer
import com.yuroyami.syncplay.settings.DataStoreKeys.CATEG_GLOBAL_ADVANCED
import com.yuroyami.syncplay.settings.DataStoreKeys.CATEG_GLOBAL_EXOPLAYER
import com.yuroyami.syncplay.settings.DataStoreKeys.CATEG_GLOBAL_GENERAL
import com.yuroyami.syncplay.settings.DataStoreKeys.CATEG_GLOBAL_LANG
import com.yuroyami.syncplay.settings.DataStoreKeys.CATEG_GLOBAL_NETWORK
import com.yuroyami.syncplay.settings.DataStoreKeys.CATEG_GLOBAL_SYNCING
import com.yuroyami.syncplay.settings.DataStoreKeys.CATEG_INROOM_ADVANCED
import com.yuroyami.syncplay.settings.DataStoreKeys.CATEG_INROOM_CHATCOLORS
import com.yuroyami.syncplay.settings.DataStoreKeys.CATEG_INROOM_CHATPROPS
import com.yuroyami.syncplay.settings.DataStoreKeys.CATEG_INROOM_MPV
import com.yuroyami.syncplay.settings.DataStoreKeys.CATEG_INROOM_PLAYERSETTINGS
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_AUDIO_LANG
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_CC_LANG
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_DISPLAY_LANG
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_ERASE_SHORTCUTS
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_FILE_MISMATCH_WARNING
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_GLOBAL_CLEAR_ALL
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_HASH_FILENAME
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_HASH_FILESIZE
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
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_INROOM_PLAYER_CUSTOM_SEEK_FRONT
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_INROOM_RESET_DEFAULT
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_MAX_BUFFER
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_MIN_BUFFER
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_NETWORK_ENGINE
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_PAUSE_ON_SOMEONE_LEAVE
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_READY_FIRST_HAND
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_REMEMBER_INFO
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_SEEK_BUFFER
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_SP_MEDIA_DIRS
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_TLS_ENABLE
import com.yuroyami.syncplay.settings.Setting.BooleanSetting
import com.yuroyami.syncplay.settings.Setting.ColorSetting
import com.yuroyami.syncplay.settings.Setting.MultiChoiceSetting
import com.yuroyami.syncplay.settings.Setting.OneClickSetting
import com.yuroyami.syncplay.settings.Setting.SliderSetting
import com.yuroyami.syncplay.settings.Setting.TextFieldSetting
import com.yuroyami.syncplay.ui.Paletting
import com.yuroyami.syncplay.utils.PLATFORM
import com.yuroyami.syncplay.utils.getPlatform
import com.yuroyami.syncplay.watchroom.homeCallback
import com.yuroyami.syncplay.watchroom.lyricist
import com.yuroyami.syncplay.watchroom.viewmodel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

lateinit var obtainerCallback: SettingObtainerCallback

interface SettingObtainerCallback {
    fun getMoreRoomSettings(): List<Pair<Setting<out Any>, String>>
}

/* Styles */
private val settingGLOBALstyle = SettingStyling(
    titleFilling = listOf(Paletting.OLD_SP_YELLOW),
    titleShadow = Paletting.SP_GRADIENT,
    iconSize = 32f,
    iconTints = listOf(Paletting.OLD_SP_YELLOW),
    iconShadows = Paletting.SP_GRADIENT
)

val settingROOMstyle = SettingStyling(
    titleFilling = listOf(Paletting.OLD_SP_YELLOW),
    titleShadow = Paletting.SP_GRADIENT,
    titleSize = 11f,
    summarySize = 8f,
    iconTints = listOf(Paletting.OLD_SP_YELLOW),
    iconShadows = Paletting.SP_GRADIENT
)

private val settingsGLOBAL: List<Pair<Setting<out Any>, String>>
    get() {
        return mutableListOf<Pair<Setting<out Any>, String>>().apply {
            add(
                BooleanSetting(
                    type = SettingType.CheckboxSettingType,
                    key = PREF_REMEMBER_INFO,
                    title = lyricist.strings.settingRememberJoinInfoTitle,
                    summary = lyricist.strings.settingRememberJoinInfoSummary,
                    defaultValue = true,
                    icon = Icons.Filled.Face,
                    styling = settingGLOBALstyle
                ) to CATEG_GLOBAL_GENERAL
            )
            add(
                Setting.YesNoDialogSetting(
                    type = SettingType.YesNoDialogSettingType,
                    key = PREF_ERASE_SHORTCUTS,
                    title = lyricist.strings.settingEraseShortcutsTitle,
                    summary = lyricist.strings.settingEraseShortcutsSummary,
                    defaultValue = true,
                    icon = Icons.Filled.BookmarkRemove,
                    rationale = lyricist.strings.settingEraseShortcutsDialog,
                    onYes = {
                        homeCallback?.onEraseConfigShortcuts()
                    },
                    styling = settingGLOBALstyle
                ) to CATEG_GLOBAL_GENERAL
            )
            add(Setting.PopupSetting(
                type = SettingType.PopupSettingType,
                key = PREF_SP_MEDIA_DIRS,
                title = lyricist.strings.mediaDirectories,
                summary = lyricist.strings.mediaDirectoriesSettingSummary,
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                styling = settingGLOBALstyle,
                popupComposable = { s ->
                    MediaDirsPopup(s)
                }
            ) to CATEG_GLOBAL_GENERAL)

            if (getPlatform() == PLATFORM.Android) {
                add(MultiChoiceSetting(
                    type = SettingType.MultiChoicePopupSettingType,
                    key = PREF_DISPLAY_LANG,
                    title = lyricist.strings.settingDisplayLanguageTitle,
                    summary = lyricist.strings.settingDisplayLanguageSummry,
                    defaultValue = "en",
                    icon = Icons.Filled.Translate,
                    styling = settingGLOBALstyle,
                    entryKeys = langMap.keys.toList(),
                    entryValues = langMap.values.toList(),
                    onItemChosen = { _, v ->
                        homeCallback?.onLanguageChanged(v)
                    }
                ) to CATEG_GLOBAL_LANG)
            } else {
                add(
                    OneClickSetting(
                        type = SettingType.OneClickSettingType,
                        key = PREF_DISPLAY_LANG,
                        title = lyricist.strings.settingDisplayLanguageTitle,
                        summary = lyricist.strings.settingDisplayLanguageSummry,
                        icon = Icons.Filled.Translate,
                        styling = settingGLOBALstyle,
                        onClick = {
                            homeCallback?.onLanguageChanged("")
                        }
                    ) to CATEG_GLOBAL_LANG)
            }

            add(
                TextFieldSetting(
                    type = SettingType.TextFieldSettingType,
                    key = PREF_AUDIO_LANG,
                    title = lyricist.strings.settingAudioDefaultLanguageTitle,
                    summary = lyricist.strings.settingAudioDefaultLanguageSummry,
                    defaultValue = "und",
                    icon = Icons.Filled.GraphicEq,
                    styling = settingGLOBALstyle,
                ) to CATEG_GLOBAL_LANG
            )

            add(
                TextFieldSetting(
                    type = SettingType.TextFieldSettingType,
                    key = PREF_CC_LANG,
                    title = lyricist
                        .strings.settingCcDefaultLanguageTitle,
                    summary = lyricist
                        .strings.settingCcDefaultLanguageSummry,
                    defaultValue = "eng",
                    icon = Icons.Filled.ClosedCaptionOff,
                    styling = settingGLOBALstyle,
                ) to CATEG_GLOBAL_LANG
            )

            add(
                BooleanSetting(
                    type = SettingType.CheckboxSettingType,
                    key = PREF_READY_FIRST_HAND,
                    title = lyricist
                        .strings.settingReadyFirsthandTitle,
                    summary = lyricist
                        .strings.settingReadyFirsthandSummary,
                    defaultValue = true,
                    icon = Icons.Filled.TaskAlt,
                    styling = settingGLOBALstyle,
                ) to CATEG_GLOBAL_SYNCING
            )

            add(
                BooleanSetting(
                    type = SettingType.CheckboxSettingType,
                    key = PREF_PAUSE_ON_SOMEONE_LEAVE,
                    title = lyricist
                        .strings.settingPauseIfSomeoneLeftTitle,
                    summary = lyricist
                        .strings.settingPauseIfSomeoneLeftSummary,
                    defaultValue = true,
                    icon = Icons.Filled.FrontHand,
                    styling = settingGLOBALstyle,
                ) to CATEG_GLOBAL_SYNCING
            )

            add(
                BooleanSetting(
                    type = SettingType.CheckboxSettingType,
                    key = PREF_FILE_MISMATCH_WARNING,
                    title = lyricist
                        .strings.settingWarnFileMismatchTitle,
                    summary = lyricist
                        .strings.settingWarnFileMismatchSummary,
                    defaultValue = true,
                    icon = Icons.Filled.ErrorOutline,
                    styling = settingGLOBALstyle,
                ) to CATEG_GLOBAL_SYNCING
            )

            add(
                MultiChoiceSetting(
                    type = SettingType.MultiChoicePopupSettingType,
                    key = PREF_HASH_FILENAME,
                    title = lyricist
                        .strings.settingFileinfoBehaviourNameTitle,
                    summary = lyricist
                        .strings.settingFileinfoBehaviourNameSummary,
                    defaultValue = "1",
                    icon = Icons.Filled.DesignServices,
                    styling = settingGLOBALstyle,
                    entryKeys =
                    listOf(
                        lyricist
                            .strings.settingFileinfoBehaviorA,
                        lyricist
                            .strings.settingFileinfoBehaviorB,
                        lyricist
                            .strings.settingFileinfoBehaviorC
                    ),
                    entryValues = listOf("1", "2", "3")
                ) to CATEG_GLOBAL_SYNCING
            )

            add(
                MultiChoiceSetting(
                    type = SettingType.MultiChoicePopupSettingType,
                    key = PREF_HASH_FILESIZE,
                    title = lyricist
                        .strings.settingFileinfoBehaviourSizeTitle,
                    summary = lyricist
                        .strings.settingFileinfoBehaviourSizeSummary,
                    defaultValue = "1",
                    icon = Icons.Filled.DesignServices,
                    styling = settingGLOBALstyle,
                    entryKeys =
                    listOf(
                        lyricist
                            .strings.settingFileinfoBehaviorA,
                        lyricist
                            .strings.settingFileinfoBehaviorB,
                        lyricist
                            .strings.settingFileinfoBehaviorC
                    ),
                    entryValues = listOf("1", "2", "3"),
                ) to CATEG_GLOBAL_SYNCING
            )

            add(
                SliderSetting(
                    type = SettingType.SliderSettingType,
                    key = PREF_MAX_BUFFER,
                    title = lyricist
                        .strings.settingMaxBufferTitle,
                    summary = lyricist
                        .strings.settingMaxBufferSummary,
                    defaultValue = 30,
                    icon = Icons.Filled.HourglassTop,
                    styling = settingGLOBALstyle,
                    maxValue = 60,
                    minValue = 1,
                ) to CATEG_GLOBAL_EXOPLAYER
            )

            add(
                SliderSetting(
                    type = SettingType.SliderSettingType,
                    key = PREF_MIN_BUFFER,
                    title = lyricist
                        .strings.settingMinBufferTitle,
                    summary = lyricist
                        .strings.settingMinBufferSummary,
                    defaultValue = 15,
                    icon = Icons.Filled.HourglassBottom,
                    styling = settingGLOBALstyle,
                    maxValue = 30,
                    minValue = 1,
                ) to CATEG_GLOBAL_EXOPLAYER
            )

            add(
                SliderSetting(
                    type = SettingType.SliderSettingType,
                    key = PREF_SEEK_BUFFER,
                    title = lyricist
                        .strings.settingPlaybackBufferTitle,
                    summary = lyricist
                        .strings.settingPlaybackBufferSummary,
                    defaultValue = 2500,
                    icon = Icons.Filled.HourglassEmpty,
                    styling = settingGLOBALstyle,
                    maxValue = 15000,
                    minValue = 100,
                ) to CATEG_GLOBAL_EXOPLAYER
            )

            add(
                MultiChoiceSetting(
                    type = SettingType.MultiChoicePopupSettingType,
                    key = PREF_NETWORK_ENGINE,
                    title = lyricist.strings.settingNetworkEngineTitle,
                    summary = lyricist.strings.settingNetworkEngineSummary,
                    defaultValue = if (getPlatform() == PLATFORM.Android) "netty" else "swiftnio",
                    icon = Icons.Filled.Lan,
                    styling = settingGLOBALstyle,
                    entryKeys =
                    mutableListOf(lyricist.strings.settingNetworkEngineKtor).apply {
                        if (getPlatform() == PLATFORM.Android) {
                            add(lyricist.strings.settingNetworkEngineNetty)
                        } else {
                            add(lyricist.strings.settingNetworkEngineSwiftNIO)
                        }
                    },
                    entryValues = mutableListOf("ktor").apply {
                        if (getPlatform() == PLATFORM.Android) {
                            add("netty")
                        } else {
                            add("swiftnio")
                        }
                    }
                ) to CATEG_GLOBAL_NETWORK
            )

            add(
                BooleanSetting(
                    type = SettingType.ToggleSettingType,
                    key = PREF_TLS_ENABLE,
                    title = lyricist.strings.settingTlsTitle,
                    summary = lyricist.strings.settingTlsSummary,
                    defaultValue = true,
                    icon = Icons.Filled.Key,
                    styling = settingGLOBALstyle,
                ) to CATEG_GLOBAL_NETWORK
            )

            add(
                Setting.YesNoDialogSetting(
                    type = SettingType.OneClickSettingType,
                    key = PREF_GLOBAL_CLEAR_ALL,
                    title = lyricist
                        .strings.settingResetdefaultTitle,
                    summary = lyricist
                        .strings.settingResetdefaultSummary,
                    icon = Icons.Filled.ClearAll,
                    styling = settingGLOBALstyle,
                    rationale = lyricist.strings.settingResetdefaultDialog,
                    onYes = {
                        launch(Dispatchers.IO) {
                            datastore.edit { preferences ->
                                preferences.clear()
                            }
                        }
                    },
                ) to CATEG_GLOBAL_ADVANCED
            )
        }
    }

val settingsROOM: List<Pair<Setting<out Any>, String>>
    get() {
        return mutableListOf<Pair<Setting<out Any>, String>>().apply {
            add(
                ColorSetting(
                    type = SettingType.ColorSettingType,
                    key = PREF_INROOM_COLOR_TIMESTAMP,
                    title = lyricist
                        .strings.uisettingTimestampColorTitle,
                    summary = lyricist
                        .strings.uisettingTimestampSummary,
                    defaultValue = Paletting.MSG_TIMESTAMP.toArgb(),
                    icon = Icons.Filled.Brush,
                    styling = settingROOMstyle,
                ) to CATEG_INROOM_CHATCOLORS
            )

            add(
                ColorSetting(
                    type = SettingType.ColorSettingType,
                    key = PREF_INROOM_COLOR_SELFTAG,
                    title = lyricist
                        .strings.uisettingSelfColorTitle,
                    summary = lyricist
                        .strings.uisettingSelfColorSummary,
                    defaultValue = Paletting.MSG_SELF_TAG.toArgb(),
                    icon = Icons.Filled.Brush,
                    styling = settingROOMstyle,
                ) to CATEG_INROOM_CHATCOLORS
            )

            add(
                ColorSetting(
                    type = SettingType.ColorSettingType,
                    key = PREF_INROOM_COLOR_FRIENDTAG,
                    title = lyricist
                        .strings.uisettingFriendColorTitle,
                    summary = lyricist
                        .strings.uisettingFriendColorSummary,
                    defaultValue = Paletting.MSG_FRIEND_TAG.toArgb(),
                    icon = Icons.Filled.Brush,
                    styling = settingROOMstyle,
                ) to CATEG_INROOM_CHATCOLORS
            )

            add(
                ColorSetting(
                    type = SettingType.ColorSettingType,
                    key = PREF_INROOM_COLOR_SYSTEMMSG,
                    title = lyricist
                        .strings.uisettingSystemColorTitle,
                    summary = lyricist
                        .strings.uisettingSystemColorSummary,
                    defaultValue = Paletting.MSG_SYSTEM.toArgb(),
                    icon = Icons.Filled.Brush,
                    styling = settingROOMstyle,
                ) to CATEG_INROOM_CHATCOLORS
            )

            add(
                ColorSetting(
                    type = SettingType.ColorSettingType,
                    key = PREF_INROOM_COLOR_USERMSG,
                    title = lyricist
                        .strings.uisettingHumanColorTitle,
                    summary = lyricist
                        .strings.uisettingHumanColorSummary,
                    defaultValue = Paletting.MSG_CHAT.toArgb(),
                    icon = Icons.Filled.Brush,
                    styling = settingROOMstyle,
                ) to CATEG_INROOM_CHATCOLORS
            )

            add(
                ColorSetting(
                    type = SettingType.ColorSettingType,
                    key = PREF_INROOM_COLOR_ERRORMSG,
                    title = lyricist
                        .strings.uisettingErrorColorTitle,
                    summary = lyricist
                        .strings.uisettingErrorColorSummary,
                    defaultValue = Paletting.MSG_ERROR.toArgb(),
                    icon = Icons.Filled.Brush,
                    styling = settingROOMstyle,
                ) to CATEG_INROOM_CHATCOLORS
            )

            add(
                BooleanSetting(
                    type = SettingType.ToggleSettingType,
                    key = PREF_INROOM_MSG_ACTIVATE_STAMP,
                    title = lyricist
                        .strings.uisettingTimestampTitle,
                    summary = lyricist
                        .strings.uisettingTimestampSummary,
                    defaultValue = true,
                    icon = Icons.Filled.Pin,
                    styling = settingROOMstyle,
                ) to CATEG_INROOM_CHATPROPS
            )

            add(
                BooleanSetting(
                    type = SettingType.ToggleSettingType,
                    key = PREF_INROOM_MSG_OUTLINE,
                    title = lyricist
                        .strings.uisettingMsgoutlineTitle,
                    summary = lyricist
                        .strings.uisettingMsgoutlineSummary,
                    defaultValue = true,
                    icon = Icons.Filled.BorderColor,
                    styling = settingROOMstyle,
                ) to CATEG_INROOM_CHATPROPS
            )

            add(
                BooleanSetting(
                    type = SettingType.ToggleSettingType,
                    key = PREF_INROOM_MSG_SHADOW,
                    title = lyricist
                        .strings.uisettingMsgshadowTitle,
                    summary = lyricist
                        .strings.uisettingMsgshadowSummary,
                    defaultValue = false,
                    icon = Icons.Filled.BorderColor,
                    styling = settingROOMstyle,
                ) to CATEG_INROOM_CHATPROPS
            )

            add(
                SliderSetting(
                    type = SettingType.SliderSettingType,
                    key = PREF_INROOM_MSG_BG_OPACITY,
                    title = lyricist
                        .strings.uisettingMessageryAlphaTitle,
                    summary = lyricist
                        .strings.uisettingMessageryAlphaSummary,
                    defaultValue = 0,
                    icon = Icons.Filled.Opacity,
                    styling = settingROOMstyle,
                    maxValue = 255,
                    minValue = 0,
                ) to CATEG_INROOM_CHATPROPS
            )

            add(
                SliderSetting(
                    type = SettingType.SliderSettingType,
                    key = PREF_INROOM_MSG_FONTSIZE,
                    title = lyricist
                        .strings.uisettingMsgsizeTitle,
                    summary = lyricist
                        .strings.uisettingMsgsizeSummary,
                    defaultValue = 9,
                    icon = Icons.Filled.FormatSize,
                    styling = settingROOMstyle,
                    maxValue = 28,
                    minValue = 6,
                ) to CATEG_INROOM_CHATPROPS
            )

            add(
                SliderSetting(
                    type = SettingType.SliderSettingType,
                    key = PREF_INROOM_MSG_MAXCOUNT,
                    title = lyricist
                        .strings.uisettingMsgcountTitle,
                    summary = lyricist
                        .strings.uisettingMsgcountSummary,
                    defaultValue = 10,
                    icon = Icons.Filled.FormatListNumbered,
                    styling = settingROOMstyle,
                    maxValue = 30,
                    minValue = 1,
                ) to CATEG_INROOM_CHATPROPS
            )

            add(
                SliderSetting(
                    type = SettingType.SliderSettingType,
                    key = PREF_INROOM_MSG_FADING_DURATION,
                    title = lyricist
                        .strings.uisettingMsglifeTitle,
                    summary = lyricist
                        .strings.uisettingMsglifeSummary,
                    defaultValue = 3,
                    icon = Icons.Filled.Timer,
                    styling = settingROOMstyle,
                    maxValue = 10,
                    minValue = 1,
                ) to CATEG_INROOM_CHATPROPS
            )

            add(
                BooleanSetting(
                    type = SettingType.ToggleSettingType,
                    key = PREF_INROOM_MSG_BOX_ACTION,
                    title = lyricist
                        .strings.uisettingMsgboxactionTitle,
                    summary = lyricist
                        .strings.uisettingMsgboxactionSummary,
                    defaultValue = true,
                    icon = Icons.Filled.Keyboard,
                    styling = settingROOMstyle,
                ) to CATEG_INROOM_CHATPROPS
            )

            add(SliderSetting(
                type = SettingType.SliderSettingType,
                key = DataStoreKeys.PREF_INROOM_PLAYER_SUBTITLE_SIZE,
                title = lyricist
                    .strings.uisettingSubtitleSizeTitle,
                summary = lyricist
                    .strings.uisettingSubtitleSizeSummary,
                defaultValue = 16,
                icon = Icons.Filled.SortByAlpha,
                styling = settingROOMstyle,
                maxValue = 200,
                minValue = 2,
                onValueChanged = { v ->
                    viewmodel?.player?.changeSubtitleSize(v)
                }
            ) to CATEG_INROOM_PLAYERSETTINGS)
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


            add(
                BooleanSetting(
                    type = SettingType.CheckboxSettingType,
                    key = PREF_INROOM_PLAYER_CUSTOM_SEEK_FRONT,
                    title = lyricist.strings.uisettingCustomSeekFrontTitle,
                    summary = lyricist.strings.uisettingCustomSeekFrontSummary,
                    defaultValue = true,
                    icon = Icons.Filled.Update,
                    styling = settingROOMstyle,
                ) to CATEG_INROOM_PLAYERSETTINGS
            )

            add(
                SliderSetting(
                    type = SettingType.SliderSettingType,
                    key = DataStoreKeys.PREF_INROOM_PLAYER_CUSTOM_SEEK_AMOUNT,
                    title = lyricist.strings.uisettingCustomSeekAmountTitle,
                    summary = lyricist.strings.uisettingCustomSeekAmountSummary,
                    defaultValue = 90,
                    icon = Icons.Filled.Update,
                    styling = settingROOMstyle,
                    maxValue = 300,
                    minValue = 30,
                ) to CATEG_INROOM_PLAYERSETTINGS
            )

            add(
                SliderSetting(
                    type = SettingType.SliderSettingType,
                    key = DataStoreKeys.PREF_INROOM_PLAYER_SEEK_FORWARD_JUMP,
                    title = lyricist
                        .strings.uisettingSeekForwardJumpTitle,
                    summary = lyricist
                        .strings.uisettingSeekForwardJumpSummary,
                    defaultValue = 10,
                    icon = Icons.Filled.FastForward,
                    styling = settingROOMstyle,
                    maxValue = 120,
                    minValue = 1,
                ) to CATEG_INROOM_PLAYERSETTINGS
            )

            add(
                SliderSetting(
                    type = SettingType.SliderSettingType,
                    key = DataStoreKeys.PREF_INROOM_PLAYER_SEEK_BACKWARD_JUMP,
                    title = lyricist
                        .strings.uisettingSeekBackwardJumpTitle,
                    summary = lyricist
                        .strings.uisettingSeekBackwardJumpSummary,
                    defaultValue = 10,
                    icon = Icons.Filled.FastRewind,
                    styling = settingROOMstyle,
                    maxValue = 120,
                    minValue = 1,
                ) to CATEG_INROOM_PLAYERSETTINGS
            )

//            add(
//                BooleanSetting(
//                    type = SettingType.ToggleSettingType,
//                    key = PREF_INROOM_PIP,
//                    title = lyricist
//                        .strings.uisettingPipTitle,
//                    summary = lyricist
//                        .strings.uisettingPipSummary,
//                    defaultValue = true,
//                    icon = Icons.Filled.PictureInPicture,
//                    styling = settingROOMstyle,
//                ) to CATEG_INROOM_ADVANCED
//            )

            add(
                SliderSetting(
                    type = SettingType.SliderSettingType,
                    key = DataStoreKeys.PREF_INROOM_RECONNECTION_INTERVAL,
                    title = lyricist
                        .strings.uisettingReconnectIntervalTitle,
                    summary = lyricist
                        .strings.uisettingReconnectIntervalSummary,
                    defaultValue = 2,
                    icon = Icons.Filled.Web,
                    styling = settingROOMstyle,
                    maxValue = 15,
                    minValue = 0,
                ) to CATEG_INROOM_ADVANCED
            )

            add(
                Setting.YesNoDialogSetting(
                    type = SettingType.OneClickSettingType,
                    key = PREF_INROOM_RESET_DEFAULT,
                    title = lyricist
                        .strings.uisettingResetdefaultTitle,
                    summary = lyricist
                        .strings.uisettingResetdefaultSummary,
                    icon = Icons.Filled.ClearAll,
                    styling = settingROOMstyle,
                    rationale = lyricist.strings.settingResetdefaultDialog,
                    onYes = {
                        launch(Dispatchers.IO) {
                            datastore.edit { preferences ->
                                preferences.clear()
                            }
                        }
                    },
                ) to CATEG_INROOM_ADVANCED
            )
        }
    }

fun sgGLOBAL() = mutableListOf<SettingCategory>().apply {
    add(
        SettingCategory(
            keyID = CATEG_GLOBAL_GENERAL,
            title = lyricist.strings.settingsCategGeneral,
            icon = Icons.Filled.SettingsSuggest
        )
    )
    add(
        SettingCategory(
            keyID = CATEG_GLOBAL_LANG,
            title = lyricist.strings.settingsCategLanguage,
            icon = Icons.Filled.Translate,
        )
    )
    add(
        SettingCategory(
            keyID = CATEG_GLOBAL_SYNCING,
            title = lyricist.strings.settingsCategSyncing,
            icon = Icons.Filled.ConnectWithoutContact
        )
    )

    if (getPlatform() == PLATFORM.Android) {
        add(
            SettingCategory(
                keyID = CATEG_GLOBAL_EXOPLAYER,
                title = lyricist.strings.settingsCategExoplayer,
                icon = Icons.Filled.VideoSettings
            )
        )
    }
    add(
        SettingCategory(
            keyID = CATEG_GLOBAL_NETWORK,
            title = lyricist.strings.settingsCategNetwork,
            icon = Icons.Filled.Hub
        )
    )
    //}
    add(
        SettingCategory(
            keyID = CATEG_GLOBAL_ADVANCED,
            title = lyricist.strings.settingsCategAdvanced,
            icon = Icons.Filled.Stream
        )
    )
    settingsGLOBAL.populate(this)
}

fun sgROOM(): MutableList<SettingCategory> {
    val list = mutableListOf(
        SettingCategory(
            keyID = CATEG_INROOM_CHATCOLORS,
            title = lyricist
                .strings.uisettingCategChatColors,
            icon = Icons.Filled.Palette,
        ),
        SettingCategory(
            keyID = CATEG_INROOM_CHATPROPS,
            title = lyricist
                .strings.uisettingCategChatProperties,
            icon = Icons.AutoMirrored.Filled.Chat
        ),
        SettingCategory(
            keyID = CATEG_INROOM_PLAYERSETTINGS,
            title = lyricist
                .strings.uisettingCategPlayerSettings,
            icon = Icons.Filled.VideoLabel,
        ),
        SettingCategory(
            keyID = CATEG_INROOM_ADVANCED,
            title = lyricist
                .strings.settingsCategAdvanced,
            icon = Icons.Filled.Stream
        )

    )
    if (viewmodel?.player?.engine == BasePlayer.ENGINE.ANDROID_MPV) {
        list.add(
            SettingCategory(
                keyID = CATEG_INROOM_MPV,
                title = lyricist.strings.uisettingCategMpv,
                icon = Icons.Filled.SettingsInputComponent
            )
        )
    }
    val moreSettings = obtainerCallback.getMoreRoomSettings()
    val allSettings = settingsROOM + moreSettings
    allSettings.populate(list)
    return list
}