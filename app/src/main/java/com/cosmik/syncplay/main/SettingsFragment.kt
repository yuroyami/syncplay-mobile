package com.cosmik.syncplay.main

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar


class SettingsFragment : PreferenceFragmentCompat(),
    PreferenceManager.OnPreferenceTreeClickListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(com.cosmik.syncplay.R.xml.settings, rootKey)
        loadSettings()
    }


    private fun loadSettings() {
        val sp = PreferenceManager.getDefaultSharedPreferences(requireContext())
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