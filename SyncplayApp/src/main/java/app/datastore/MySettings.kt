package app.datastore

import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material.icons.filled.CompareArrows
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
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
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
import app.R
import app.datastore.DataStoreKeys.PREF_INROOM_COLOR_ERRORMSG
import app.datastore.DataStoreKeys.PREF_INROOM_COLOR_FRIENDTAG
import app.datastore.DataStoreKeys.PREF_INROOM_COLOR_SELFTAG
import app.datastore.DataStoreKeys.PREF_INROOM_COLOR_SYSTEMMSG
import app.datastore.DataStoreKeys.PREF_INROOM_COLOR_TIMESTAMP
import app.datastore.DataStoreKeys.PREF_INROOM_COLOR_USERMSG
import app.datastore.DataStoreKeys.PREF_INROOM_MSG_ACTIVATE_STAMP
import app.datastore.DataStoreKeys.PREF_INROOM_MSG_BG_OPACITY
import app.datastore.DataStoreKeys.PREF_INROOM_MSG_FADING_DURATION
import app.datastore.DataStoreKeys.PREF_INROOM_MSG_FONTSIZE
import app.datastore.DataStoreKeys.PREF_INROOM_MSG_MAXCOUNT
import app.datastore.DataStoreKeys.PREF_INROOM_PLAYER_AUDIO_DELAY
import app.datastore.DataStoreKeys.PREF_INROOM_PLAYER_SEEK_BACKWARD_JUMP
import app.datastore.DataStoreKeys.PREF_INROOM_PLAYER_SEEK_FORWARD_JUMP
import app.datastore.DataStoreKeys.PREF_INROOM_PLAYER_SUBTITLE_DELAY
import app.datastore.DataStoreKeys.PREF_INROOM_PLAYER_SUBTITLE_SIZE
import app.datastore.DataStoreKeys.PREF_REMEMBER_INFO
import app.datastore.DataStoreKeys.PREF_SP_MEDIA_DIRS
import app.settings.Setting
import app.settings.SettingCategory
import app.settings.SettingType
import app.ui.compose.MediaDirsPopup.MediaDirsPopup
import java.util.Locale

object MySettings {

    fun ComponentActivity.globalSettings(): List<SettingCategory> {
        val ds = DataStoreKeys.DATASTORE_GLOBAL_SETTINGS

        return listOf(
            /** Setting Card Number 01: General */
            SettingCategory(
                title = "General",
                icon = Icons.Filled.SettingsSuggest,
                settingList = listOf(
                    Setting(
                        type = SettingType.CheckboxSetting,
                        title = resources.getString(R.string.setting_remember_join_info_title),
                        summary = resources.getString(R.string.setting_remember_join_info_summary),
                        defaultValue = true,
                        key = PREF_REMEMBER_INFO,
                        icon = Icons.Filled.Face,
                        datastorekey = ds
                    ),
                    Setting(
                        type = SettingType.PopupSetting,
                        title = resources.getString(R.string.media_directories),
                        summary = resources.getString(R.string.media_directories_setting_summary),
                        key = PREF_SP_MEDIA_DIRS,
                        icon = Icons.Filled.QueueMusic,
                        datastorekey = ds,
                        popupComposable = { s -> MediaDirsPopup(s) }
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
                        title = resources.getString(R.string.setting_display_language_title),
                        summary = resources.getString(R.string.setting_display_language_summry),
                        defaultValue = "en",
                        key = "lang",
                        entryKeys = resources.getStringArray(R.array.Lang).toList(),
                        entryValues = resources.getStringArray(R.array.LangValues).toList(),
                        icon = Icons.Filled.Translate,
                        onItemChosen = { i, v ->
                            val locale = Locale(v)
                            Locale.setDefault(locale)
                            val config = Configuration()
                            config.setLocale(locale)
                            resources.updateConfiguration(config, resources.displayMetrics)

                            /* Let's show a toast to the user that the language has been changed */
                            Toast.makeText(
                                this, String.format(resources.getString(R.string.setting_display_language_toast), ""),
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        datastorekey = ds
                    ),
                    Setting(
                        type = SettingType.MultiChoicePopupSetting,
                        title = resources.getString(R.string.setting_audio_default_language_title),
                        summary = resources.getString(R.string.setting_audio_default_language_summry),
                        defaultValue = "en",
                        key = "audio_lang",
                        icon = Icons.Filled.GraphicEq,
                        datastorekey = ds
                    ),
                    Setting(
                        type = SettingType.MultiChoicePopupSetting,
                        title = resources.getString(R.string.setting_cc_default_language_title),
                        summary = resources.getString(R.string.setting_cc_default_language_summry),
                        defaultValue = "en",
                        key = "cc_lang",
                        icon = Icons.Filled.ClosedCaptionOff,
                        datastorekey = ds
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
                        title = resources.getString(R.string.setting_ready_firsthand_title),
                        summary = resources.getString(R.string.setting_ready_firsthand_summary),
                        defaultValue = true,
                        key = "ready_first_hand",
                        icon = Icons.Filled.TaskAlt,
                        datastorekey = ds
                    ),
                    Setting(
                        type = SettingType.CheckboxSetting,
                        title = resources.getString(R.string.setting_pause_if_someone_left_title),
                        summary = resources.getString(R.string.setting_pause_if_someone_left_summary),
                        defaultValue = true,
                        key = "pause_on_leave",
                        icon = Icons.Filled.FrontHand,
                        datastorekey = ds
                    ),
                    Setting(
                        type = SettingType.CheckboxSetting,
                        title = resources.getString(R.string.setting_warn_file_mismatch_title),
                        summary = resources.getString(R.string.setting_warn_file_mismatch_summary),
                        defaultValue = true,
                        key = "mismatch_notice",
                        icon = Icons.Filled.ErrorOutline,
                        datastorekey = ds
                    ),
                    Setting(
                        type = SettingType.MultiChoicePopupSetting,
                        title = resources.getString(R.string.setting_fileinfo_behaviour_name_title),
                        summary = resources.getString(R.string.setting_fileinfo_behaviour_name_summary),
                        key = "filename_hashing",
                        defaultValue = "1",
                        entryKeys = resources.getStringArray(R.array.fileinfoBehavior).toList(),
                        entryValues = resources.getStringArray(R.array.fileinfoBehaviorValues).toList(),
                        icon = Icons.Filled.DesignServices,
                        datastorekey = ds
                    ),
                    Setting(
                        type = SettingType.MultiChoicePopupSetting,
                        title = resources.getString(R.string.setting_fileinfo_behaviour_size_title),
                        summary = resources.getString(R.string.setting_fileinfo_behaviour_size_summary),
                        key = "filesize_hashing",
                        defaultValue = "1",
                        entryKeys = resources.getStringArray(R.array.fileinfoBehavior).toList(),
                        entryValues = resources.getStringArray(R.array.fileinfoBehaviorValues).toList(),
                        icon = Icons.Filled.DesignServices,
                        datastorekey = ds
                    )
                )
            ),


            SettingCategory(
                title = "Video Player",
                icon = Icons.Filled.VideoSettings,
                settingList = listOf(
                    Setting(
                        type = SettingType.SliderSetting,
                        title = resources.getString(R.string.setting_max_buffer_title),
                        summary = resources.getString(R.string.setting_max_buffer_summary),
                        defaultValue = 30,
                        minValue = 1,
                        maxValue = 60,
                        key = "buffer_max",
                        icon = Icons.Filled.HourglassTop,
                        datastorekey = ds
                    ),
                    Setting(
                        type = SettingType.SliderSetting,
                        title = resources.getString(R.string.setting_min_buffer_title),
                        summary = resources.getString(R.string.setting_min_buffer_summary),
                        defaultValue = 15,
                        minValue = 1,
                        maxValue = 30,
                        key = "buffer_min",
                        icon = Icons.Filled.HourglassBottom,
                        datastorekey = ds
                    ),
                    Setting(
                        type = SettingType.SliderSetting,
                        title = resources.getString(R.string.setting_playback_buffer_title),
                        summary = resources.getString(R.string.setting_playback_buffer_summary),
                        defaultValue = 2500,
                        minValue = 100,
                        maxValue = 15000,
                        key = "buffer_seek",
                        icon = Icons.Filled.HourglassEmpty,
                        datastorekey = ds
                    )

                )
            ),

            SettingCategory(
                title = "Network",
                icon = Icons.Filled.Hub,
                settingList = listOf(
                    Setting(
                        type = SettingType.ToggleSetting,
                        title = resources.getString(R.string.setting_tls_title),
                        summary = resources.getString(R.string.setting_tls_summary),
                        defaultValue = false,
                        key = "tls",
                        icon = Icons.Filled.Key,
                        datastorekey = ds
                    )
                )
            ),

            SettingCategory(
                title = "Advanced",
                icon = Icons.Filled.Stream,
                settingList = listOf(
                    Setting(
                        type = SettingType.OneClickSetting,
                        title = resources.getString(R.string.setting_resetdefault_title),
                        summary = resources.getString(R.string.setting_resetdefault_summary),
                        key = "reset_default",
                        icon = Icons.Filled.ClearAll,
                        datastorekey = ds
                    )
                )
            ),
        )
    }

    fun ComponentActivity.inRoomPreferences(): List<SettingCategory> {
        val ds = DataStoreKeys.DATASTORE_INROOM_PREFERENCES

        return listOf(
            /** Setting Card Number 01: Message Colors */
            SettingCategory(
                title = "Chat Colors",
                icon = Icons.Filled.Palette,
                settingList = listOf(
                    Setting(
                        type = SettingType.ColorSetting,
                        title = resources.getString(R.string.uisetting_timestamp_color_title),
                        summary = resources.getString(R.string.uisetting_timestamp_color_summary),
                        defaultValue = "",
                        key = PREF_INROOM_COLOR_TIMESTAMP,
                        icon = Icons.Filled.Brush,
                        datastorekey = ds
                    ),
                    Setting(
                        type = SettingType.ColorSetting,
                        title = resources.getString(R.string.uisetting_self_color_title),
                        summary = resources.getString(R.string.uisetting_self_color_summary),
                        defaultValue = "",
                        key = PREF_INROOM_COLOR_SELFTAG,
                        icon = Icons.Filled.Brush,
                        datastorekey = ds
                    ),
                    Setting(
                        type = SettingType.ColorSetting,
                        title = resources.getString(R.string.uisetting_friend_color_title),
                        summary = resources.getString(R.string.uisetting_friend_color_summary),
                        defaultValue = "",
                        key = PREF_INROOM_COLOR_FRIENDTAG,
                        icon = Icons.Filled.Brush,
                        datastorekey = ds
                    ),
                    Setting(
                        type = SettingType.ColorSetting,
                        title = resources.getString(R.string.uisetting_system_color_title),
                        summary = resources.getString(R.string.uisetting_system_color_summary),
                        defaultValue = "",
                        key = PREF_INROOM_COLOR_SYSTEMMSG,
                        icon = Icons.Filled.Brush,
                        datastorekey = ds
                    ),
                    Setting(
                        type = SettingType.ColorSetting,
                        title = resources.getString(R.string.uisetting_human_color_title),
                        summary = resources.getString(R.string.uisetting_human_color_summary),
                        defaultValue = "",
                        key = PREF_INROOM_COLOR_USERMSG,
                        icon = Icons.Filled.Brush,
                        datastorekey = ds
                    ),
                    Setting(
                        type = SettingType.ColorSetting,
                        title = resources.getString(R.string.uisetting_error_color_title),
                        summary = resources.getString(R.string.uisetting_error_color_summary),
                        defaultValue = "",
                        key = PREF_INROOM_COLOR_ERRORMSG,
                        icon = Icons.Filled.Brush,
                        datastorekey = ds
                    ),

                    )
            ),

            /** Setting Card N°02: Message Properties */
            SettingCategory(
                title = "Chat Properties",
                icon = Icons.Filled.Chat,
                settingList = listOf(
                    Setting(
                        type = SettingType.ToggleSetting,
                        title = resources.getString(R.string.uisetting_timestamp_title),
                        summary = resources.getString(R.string.uisetting_timestamp_title),
                        defaultValue = true,
                        key = PREF_INROOM_MSG_ACTIVATE_STAMP,
                        icon = Icons.Filled.Pin,
                        datastorekey = ds
                    ),
                    Setting(
                        type = SettingType.SliderSetting,
                        title = resources.getString(R.string.uisetting_messagery_alpha_title),
                        summary = resources.getString(R.string.uisetting_messagery_alpha_summary),
                        defaultValue = 40,
                        minValue = 0,
                        maxValue = 255,
                        key = PREF_INROOM_MSG_BG_OPACITY,
                        icon = Icons.Filled.Opacity,
                        datastorekey = ds
                    ),
                    Setting(
                        type = SettingType.SliderSetting,
                        title = resources.getString(R.string.uisetting_msgsize_title),
                        summary = resources.getString(R.string.uisetting_msgsize_summary),
                        defaultValue = 10,
                        minValue = 6,
                        maxValue = 48,
                        key = PREF_INROOM_MSG_FONTSIZE,
                        icon = Icons.Filled.FormatSize,
                        datastorekey = ds
                    ),
                    Setting(
                        type = SettingType.SliderSetting,
                        title = resources.getString(R.string.uisetting_msgcount_title),
                        summary = resources.getString(R.string.uisetting_msgcount_summary),
                        defaultValue = 10,
                        minValue = 1,
                        maxValue = 30,
                        key = PREF_INROOM_MSG_MAXCOUNT,
                        icon = Icons.Filled.FormatListNumbered,
                        datastorekey = ds
                    ),
                    Setting(
                        type = SettingType.SliderSetting,
                        title = resources.getString(R.string.uisetting_msglife_title),
                        summary = resources.getString(R.string.uisetting_msglife_summary),
                        defaultValue = 3,
                        minValue = 1,
                        maxValue = 30,
                        key = PREF_INROOM_MSG_FADING_DURATION,
                        icon = Icons.Filled.Timer,
                        datastorekey = ds
                    ),
                )
            ),

            /** Setting Card N°03: Player Settings */
            SettingCategory(
                title = "Player Settings",
                icon = Icons.Filled.VideoLabel,
                settingList = listOf(
                    Setting(
                        type = SettingType.SliderSetting,
                        title = resources.getString(R.string.uisetting_subtitle_size_title),
                        summary = resources.getString(R.string.uisetting_subtitle_size_summary),
                        defaultValue = 16,
                        minValue = 2,
                        maxValue = 200,
                        key = PREF_INROOM_PLAYER_SUBTITLE_SIZE,
                        icon = Icons.Filled.SortByAlpha,
                        datastorekey = ds
                    ),
                    Setting(
                        type = SettingType.SliderSetting,
                        title = resources.getString(R.string.uisetting_subtitle_delay_title),
                        summary = resources.getString(R.string.uisetting_subtitle_delay_summary),
                        defaultValue = 0,
                        minValue = -120000,
                        maxValue = +120000,
                        key = PREF_INROOM_PLAYER_SUBTITLE_DELAY,
                        icon = Icons.Filled.CompareArrows,
                        datastorekey = ds
                    ),
                    Setting(
                        type = SettingType.SliderSetting,
                        title = resources.getString(R.string.uisetting_audio_delay_title),
                        summary = resources.getString(R.string.uisetting_audio_delay_summary),
                        defaultValue = 0,
                        minValue = -120000,
                        maxValue = +120000,
                        key = PREF_INROOM_PLAYER_AUDIO_DELAY,
                        icon = Icons.Filled.CompareArrows,
                        datastorekey = ds
                    ),
                    Setting(
                        type = SettingType.SliderSetting,
                        title = resources.getString(R.string.uisetting_seek_forward_jump_title),
                        summary = resources.getString(R.string.uisetting_seek_forward_jump_summary),
                        defaultValue = 10,
                        minValue = 1,
                        maxValue = 120,
                        key = PREF_INROOM_PLAYER_SEEK_FORWARD_JUMP,
                        icon = Icons.Filled.FastForward,
                        datastorekey = ds
                    ),
                    Setting(
                        type = SettingType.SliderSetting,
                        title = resources.getString(R.string.uisetting_seek_backward_jump_title),
                        summary = resources.getString(R.string.uisetting_seek_backward_jump_summary),
                        defaultValue = 10,
                        minValue = 1,
                        maxValue = 120,
                        key = PREF_INROOM_PLAYER_SEEK_BACKWARD_JUMP,
                        icon = Icons.Filled.FastRewind,
                        datastorekey = ds
                    ),
                )
            ),


            SettingCategory(
                title = "Advanced",
                icon = Icons.Filled.Stream,
                settingList = listOf(
                    Setting(
                        type = SettingType.OneClickSetting,
                        title = resources.getString(R.string.uisetting_resetdefault_title),
                        summary = resources.getString(R.string.uisetting_resetdefault_summary),
                        key = "RESET",
                        icon = Icons.Filled.ClearAll,
                        datastorekey = ds
                    )
                )
            ),
        )
    }

}