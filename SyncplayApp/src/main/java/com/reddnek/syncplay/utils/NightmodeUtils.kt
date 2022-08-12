package com.reddnek.syncplay.utils

import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.reddnek.syncplay.R
import com.reddnek.syncplay.main.ConnectFragment

/** A small class that delegates night mode settings */
object NightmodeUtils {

    /** This simple function will set the night mode as per the value specified.
     * 0 means apply the night mode from prefs.
     * 1 means OFF
     * 2 means AUTO (Follows the system)
     * 3 means ON
     * null means switch the mode from ON to OFF or OFF to ON
     *
     * This method also saves the value in the SharedPreferences (if value == null) */
    fun Fragment.setNightMode(value: String?) {
        val sp = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val savedPreference = sp.getString("night_mode", "3")

        when (value) {
            "0" -> {
                setNightMode(savedPreference)
            }
            "1" -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                if (this is ConnectFragment) {
                    val d = ResourcesCompat.getDrawable(resources, R.drawable.ic_sun, null)
                    binding.connectNightswitch.setImageDrawable(d)
                    binding.connectNightswitch.visibility = View.VISIBLE

                }
            }
            "2" -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                if (this is ConnectFragment) {
                    binding.connectNightswitch.visibility = View.GONE
                }
            }
            "3" -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                if (this is ConnectFragment) {
                    val d = ResourcesCompat.getDrawable(resources, R.drawable.ic_crescent, null)
                    binding.connectNightswitch.setImageDrawable(d)
                    binding.connectNightswitch.visibility = View.VISIBLE

                }
            }
            null -> {
                if (savedPreference == "3") {
                    sp.edit().putString("night_mode", "1").apply()
                    setNightMode("1")
                } else {
                    sp.edit().putString("night_mode", "3").apply()
                    setNightMode("3")
                }
            }
        }
    }
}