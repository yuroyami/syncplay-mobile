package app.datastore

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material.icons.filled.ConnectWithoutContact
import androidx.compose.material.icons.filled.DesignServices
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FrontHand
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VideoSettings
import app.R
import app.datastore.DataStoreKeys.PREF_REMEMBER_INFO
import app.datastore.DataStoreKeys.PREF_SP_MEDIA_DIRS
import app.settings.Setting
import app.settings.SettingCategory
import app.settings.SettingType
import app.ui.activities.HomeActivity
import java.util.Locale

object MySettings {

    fun HomeActivity.globalSettings(): List<SettingCategory> {
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
                        type = SettingType.OneClickSetting,
                        title = resources.getString(R.string.media_directories),
                        summary = resources.getString(R.string.media_directories_setting_summary),
                        key = PREF_SP_MEDIA_DIRS,
                        icon = Icons.Filled.QueueMusic,
                        datastorekey = ds
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
}