package app.utils

import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import app.controllers.fragment.ConnectFragment

/** A small class that delegates night mode settings */
object NightmodeUtils {


    /** This simple function will set the night mode as per the value specified.
     * 0 means apply the night mode from prefs.
     * 1 means OFF
     * 2 means AUTO (Follows the system)
     * 3 means ON
     * null means reverse the current mode (response to clicking the toggle button)
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
                    binding.connectNightswitch.setMinAndMaxFrame(0, 70)
                    binding.connectNightswitch.playAnimation()
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
                    binding.connectNightswitch.setMinAndMaxFrame(70, 141)
                    binding.connectNightswitch.playAnimation()
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