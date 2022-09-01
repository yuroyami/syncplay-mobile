package com.reddnek.syncplay.controllers.fragment

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import com.reddnek.syncplay.controllers.activity.RoomActivity
import com.reddnek.syncplay.utils.UIUtils.applyUISettings

class RoomSettingsFragment : PreferenceFragmentCompat(),
    PreferenceManager.OnPreferenceTreeClickListener {

    /************************************************************************************************
     * This is the little fragment that appears when users click 'Settings' from the Room overflow  *
     * menu. The goal of this fragment is to adjust some UI/Room settings without having to leave   *
     * the room. Therefore, the settings must be applied on-the-fly. However, in order to do that,  *
     * we have some choices:                                                                        *
     *                                                                                              *
     * a) We show users a small button, upon clicking which, the preferences are applied.           *
     *    -- This can be done through a simple click listener.                                      *
     * b) Closing the fragment will apply the preferences.                                          *
     *    -- This can be done on the event callback of closing the fragment.                        *
     * c) Clicking any preference will apply the settings for all preferences.                      *
     *    -- We can do that through the onPreferenceTreeClick callback, provided by this class.     *
     * d) Changing any preference will apply the settings for all preferences.                      *
     *    -- We can do that by adding change listeners on all preferences.                          *
     *                                                                                              *
     * At first glance, all options are great but a button that takes visible space in the screen   *
     * is not that ideal. Which is why we're gonna do the choices (b) (c) and (d).                  *
     *                                                                                              *
     ***********************************************************************************************/

    /*************************************************************************************************
     * Preference screens are functional on their own once you provide the following 3 lines. That's *
     * all a Preference Fragment needs to function. Overriding the onCreatePreferences method and    *
     * specifying which settings XML file they contain, is enough for it to function. But, as we     *
     * mentioned before, we need more than a simple Preference Fragment. We want a fragment that     *
     * applies preferences on-the-go. So instead of just saving the settings to storage, we want 'em *
     * also applied as we change them. We do that using the choices we mentioned above.              *
     *************************************************************************************************/

    /** This list will serve a double purpose after we add our UI Prefs to it :
     *   - First, it allows us to add pref change listeners to each one of them to get callbacks.
     *   - Second, it allows us to reset only the UI prefs to default when we need so, not all sharedprefs.*/
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(com.reddnek.syncplay.R.xml.ui_settings, rootKey)

        /** Applying settings as they change, according to Choice "D" from above **/
        for (i in (0 until preferenceScreen.preferenceCount)) {
            preferenceScreen.getPreference(i).setOnPreferenceChangeListener { _, _ ->
                applyPrefs()
                return@setOnPreferenceChangeListener true
            }
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        /** This is how we apply Choice "C" from above, clicking any pref applies all prefs **/
        applyPrefs()

        /** Now here, we must handle resetting the UI settings only without affecting other settings */
        when (preference.key) {
            "ui_resetdefault" -> {
                /** This allows us to retrieve only the prefs related to this screen, then we clear 'em **/
                preferenceScreen.preferenceManager.sharedPreferences?.edit()?.clear()?.apply()

                /** Show a snackbar telling the user that the preferences have gotten defaulted **/
                Snackbar.make(
                    requireView(),
                    "Your room settings are reset to default",
                    Snackbar.LENGTH_SHORT
                ).show()

                /** Let's re-inflate the new default state of preferences again **/
                setPreferencesFromResource(
                    com.reddnek.syncplay.R.xml.ui_settings,
                    "syncplay_ui_settings"
                )

                /** FIXME: Now, let's scroll to the reset default preference, where user last was **/
                Handler(Looper.getMainLooper()).postDelayed({
                    scrollToPreference("resetdefault")
                }, 50)
                return true
            }
        }
        return super.onPreferenceTreeClick(preference)
    }

    /** Just a convenience/shortcut method **/
    private fun applyPrefs() {
        (activity as RoomActivity).applyUISettings()
    }
}