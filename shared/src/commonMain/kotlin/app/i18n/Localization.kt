package app.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.intl.Locale
import cafe.adriel.lyricist.Lyricist

/**
 * Holds the app's display language in one place.
 *
 * Composables read [strings]. Code outside a composable reads [Localization.strings]. Both come
 * from the same [Lyricist], so a language change updates the whole app at once, with no restart.
 *
 * The layout stays left to right in every language, Arabic included. Only the words change.
 * This is a deliberate choice.
 */
object Localization {

    /** The only instance. Composables get it through [ProvideAppStrings] at the root. */
    val lyricist: Lyricist<AppStrings> = Lyricist(Locales.En, appStrings)

    /** Strings for code that is not a composable: the protocol, the engines, notifications. */
    val strings: AppStrings get() = lyricist.strings

    /**
     * The language tag of the device. [Lyricist] shows English when the app does not ship that
     * language.
     */
    fun deviceLanguage(): String = Locale.current.toLanguageTag()

    /**
     * Applies a saved language preference. A blank value means "follow the device". The language
     * setting stores a blank value when nothing is chosen.
     */
    fun apply(saved: String) {
        lyricist.languageTag = saved.ifBlank { deviceLanguage() }
    }
}

/** The strings of the current language. The whole subtree re-reads them when it changes. */
val strings: AppStrings
    @Composable @ReadOnlyComposable get() = LocalAppStrings.current
