package com.cosmik.syncplay.room

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar


class RoomSettingsFragment : PreferenceFragmentCompat(),
    PreferenceManager.OnPreferenceTreeClickListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(com.cosmik.syncplay.R.xml.ui_settings, rootKey)
        loadSettings()
    }


    private fun loadSettings() {
        val sp = PreferenceManager.getDefaultSharedPreferences(requireContext())
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
            "ui_resetdefault" -> {
                PreferenceManager.getDefaultSharedPreferences(requireContext()).edit().clear()
                    .apply()
                Snackbar.make(
                    requireView(),
                    "Your settings are reset to default",
                    Snackbar.LENGTH_SHORT
                ).show()
                setPreferencesFromResource(
                    com.cosmik.syncplay.R.xml.ui_settings,
                    "syncplay_settings"
                )
                Handler(Looper.getMainLooper()).postDelayed({
                    scrollToPreference("resetdefault")
                }, 50)
                return true
            }
        }
        return super.onPreferenceTreeClick(preference)
    }


}