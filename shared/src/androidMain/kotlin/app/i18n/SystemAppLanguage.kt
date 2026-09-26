package app.i18n

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import app.preferences.Preferences.DISPLAY_LANG
import app.preferences.Preferences.SYSTEM_LANG_SEEN
import app.preferences.awaitPreferences
import app.preferences.flow
import app.preferences.set
import app.preferences.value

/**
 * Android 13 added a per-app language to the system settings. This class keeps that value and the
 * in-app language setting ([DISPLAY_LANG]) as one value, so a choice in either place shows in the
 * other. The system offers the languages in androidApp's `res/xml/locales_config.xml`.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class SystemAppLanguage(context: Context) {
    private val manager = context.getSystemService(LocaleManager::class.java)

    /** The system's per-app language as a language code, or blank for the device's language. */
    private fun read(): String = manager.applicationLocales.let { if (it.isEmpty) "" else it[0].language }

    /**
     * Makes the two values one again. A system value that differs from [SYSTEM_LANG_SEEN] changed
     * in the system settings, so it wins. Otherwise the in-app value goes to the system.
     */
    suspend fun reconcile() {
        awaitPreferences()
        val system = read()
        if (system != SYSTEM_LANG_SEEN.value()) {
            SYSTEM_LANG_SEEN.set(system)
            DISPLAY_LANG.set(system)
        } else {
            write(DISPLAY_LANG.value())
        }
    }

    /** Sends every in-app choice to the system. Call it after [reconcile]. It never returns. */
    suspend fun followInAppChoice() {
        DISPLAY_LANG.flow().collect { write(it) }
    }

    private suspend fun write(code: String) {
        // Recorded first: the system change comes back as a configuration change, which reconciles.
        SYSTEM_LANG_SEEN.set(code)
        if (read() != code) {
            manager.applicationLocales = if (code.isBlank()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(code)
        }
    }
}
