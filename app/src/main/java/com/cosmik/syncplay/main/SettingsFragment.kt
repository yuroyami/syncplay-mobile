package com.cosmik.syncplay.main

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


class SettingsFragment : PreferenceFragmentCompat(),
    PreferenceManager.OnPreferenceTreeClickListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(com.cosmik.syncplay.R.xml.settings, rootKey)

        val langPref = findPreference<ListPreference>("lang")
        langPref!!.setOnPreferenceChangeListener { pref, newVal ->

            /** AndroidX allows us now to change app language in just one line of code. This would *
             * work without issues for Android 13 and higher. But on lower Android versions, there *
             * needs to be a service to be added in AndroidManifest.xml                            *
             *                                                                                     *
             * For more info: @link https://developer.android.com/reference/androidx/appcompat/app/AppCompatDelegate#setApplicationLocales(androidx.core.os.LocaleListCompat) **/

            val localesList: LocaleListCompat = LocaleListCompat.forLanguageTags(newVal.toString())
            AppCompatDelegate.setApplicationLocales(localesList)

            Toast.makeText(requireContext(), "changed lang", Toast.LENGTH_SHORT).show()
            true
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
            "resetdefault" -> {
                PreferenceManager.getDefaultSharedPreferences(requireContext()).edit().clear()
                    .apply()
                Snackbar.make(
                    requireView(),
                    "Your settings are reset to default",
                    Snackbar.LENGTH_SHORT
                ).show()
                setPreferencesFromResource(com.cosmik.syncplay.R.xml.settings, "syncplay_settings")
                Handler(Looper.getMainLooper()).postDelayed({
                    scrollToPreference("resetdefault")
                }, 50)
                return true
            }
        }
        return super.onPreferenceTreeClick(preference)
    }
}