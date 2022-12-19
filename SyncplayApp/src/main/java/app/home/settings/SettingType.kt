package app.home.settings

import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreference
import app.home.settings.SettingType.CheckboxSetting
import app.home.settings.SettingType.MultiChoicePopupSetting
import app.home.settings.SettingType.OneClickSetting
import app.home.settings.SettingType.SliderSetting
import app.home.settings.SettingType.ToggleSetting

/** Defines the type of a setting (preference).
 * @property [OneClickSetting]
 * @property [MultiChoicePopupSetting]
 * @property [CheckboxSetting]
 * @property [ToggleSetting]
 * @property [SliderSetting]
 */
enum class SettingType {
    /** OneClickSetting is a [SettingType] for a setting whose function is based upon one click only.
     * That means, that the setting will not read/write any value,
     * but instead, just initiates a lambda function (onClick).
     *
     * In XML Preferences, this is equivalent to the plain [Preference] setting. */
    OneClickSetting,

    /** MultiChoicePopupSetting is a [SettingType] for a setting that shows a multi-choice popup dialog.
     * This type of setting receives a list of entry pairs, and only one can be chosen.
     *
     * In XML preferences, this is equivalent to [ListPreference] setting.*/
    MultiChoicePopupSetting,

    /** CheckboxSetting is a [SettingType] for a setting that can be checked on/off, with a checbox.
     * This means it works with booleans.
     *
     * In XML preferences, this is equivalent to [CheckBoxPreference] setting. */
    CheckboxSetting,

    /** Basically the same as [CheckboxSetting] but uses a toggle button instead.
     *
     * In XML preferences, this is equivalent to [SwitchPreference] setting. */
    ToggleSetting,

    /** SliderSetting is a [SettingType] that uses a slider, works with integers.
     *
     * In XML preferences, this is equivalent to [SeekBarPreference] setting. */
    SliderSetting,

}