package com.reddnek.syncplay.controllers.fragment

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import com.reddnek.syncplay.controllers.activity.DirectoriesActivity
import com.reddnek.syncplay.utils.NightmodeUtils.setNightMode
import com.reddnek.syncplay.R as X

class SettingsFragment : PreferenceFragmentCompat(),
    PreferenceManager.OnPreferenceTreeClickListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(X.xml.settings, rootKey)

        findPreference<ListPreference>("lang")?.setOnPreferenceChangeListener { pref, newVal ->
            val localesList: LocaleListCompat = LocaleListCompat.forLanguageTags(newVal.toString())
            AppCompatDelegate.setApplicationLocales(localesList) /* One line of code, capable of localizing the app at runtime */

            /* Let's show a toast to the user that the language has been changed */
            Toast.makeText(
                requireContext(), String.format(
                    requireContext().resources.getString(X.string.setting_display_language_toast),
                    ""
                ),
                Toast.LENGTH_SHORT
            ).show()
            true
        }

        findPreference<ListPreference>("night_mode")?.setOnPreferenceChangeListener { pref, newVal ->
            setNightMode(newVal.toString())
            true
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
            "resetdefault" -> {
                /** This allows us to retrieve only the prefs related to this screen, then we clear 'em **/
                preferenceManager.sharedPreferences?.edit()?.clear()?.apply()

                /** Show a snackbar telling the user that the preferences have gotten defaulted **/
                Snackbar.make(
                    requireView(),
                    "Your settings are reset to default",
                    Snackbar.LENGTH_SHORT
                ).show()

                /** To visually see the default-ation, we inflate our preference screen again **/
                setPreferencesFromResource(
                    com.reddnek.syncplay.R.xml.settings,
                    "syncplay_settings"
                )

                /** Scrolling to the reset default preference again **/
                Handler(Looper.getMainLooper()).postDelayed(
                    { scrollToPreference("resetdefault") },
                    50
                )
                return true
            }
            "media_directories" -> {
                val intent = Intent(requireContext(), DirectoriesActivity::class.java)
                startActivity(intent)
            }
        }
        return super.onPreferenceTreeClick(preference)
    }
}