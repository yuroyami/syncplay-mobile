package app.preferences

import androidx.compose.runtime.Composable
import app.i18n.strings
import androidx.compose.runtime.collectAsState
import app.i18n.Localization
import app.utils.localizedLanguageName

/**
 * One media language that the settings offer. The track preferences store [iso6392], and the
 * engines match on it. [iso6391] is the code that the platform can name. [englishName] is the
 * fallback for a platform that has no name for the language.
 */
internal data class MediaLanguage(val iso6392: String, val iso6391: String, val englishName: String)

internal val mediaLanguages = listOf(
    MediaLanguage("eng", "en", "English"),
    MediaLanguage("spa", "es", "Spanish"),
    MediaLanguage("fra", "fr", "French"),
    MediaLanguage("deu", "de", "German"),
    MediaLanguage("ita", "it", "Italian"),
    MediaLanguage("por", "pt", "Portuguese"),
    MediaLanguage("rus", "ru", "Russian"),
    MediaLanguage("jpn", "ja", "Japanese"),
    MediaLanguage("kor", "ko", "Korean"),
    MediaLanguage("zho", "zh", "Chinese"),
    MediaLanguage("ara", "ar", "Arabic"),
    MediaLanguage("hin", "hi", "Hindi"),
    MediaLanguage("tur", "tr", "Turkish"),
    MediaLanguage("pol", "pl", "Polish"),
    MediaLanguage("nld", "nl", "Dutch"),
    MediaLanguage("swe", "sv", "Swedish"),
    MediaLanguage("nor", "no", "Norwegian"),
    MediaLanguage("dan", "da", "Danish"),
    MediaLanguage("fin", "fi", "Finnish"),
    MediaLanguage("hun", "hu", "Hungarian"),
    MediaLanguage("ces", "cs", "Czech"),
    MediaLanguage("ron", "ro", "Romanian"),
    MediaLanguage("ell", "el", "Greek"),
    MediaLanguage("heb", "he", "Hebrew"),
    MediaLanguage("tha", "th", "Thai"),
    MediaLanguage("vie", "vi", "Vietnamese"),
    MediaLanguage("ind", "id", "Indonesian"),
    MediaLanguage("msa", "ms", "Malay"),
    MediaLanguage("ukr", "uk", "Ukrainian"),
    MediaLanguage("bul", "bg", "Bulgarian"),
    MediaLanguage("hrv", "hr", "Croatian"),
    MediaLanguage("srp", "sr", "Serbian"),
    MediaLanguage("slk", "sk", "Slovak"),
    MediaLanguage("slv", "sl", "Slovenian"),
    MediaLanguage("cat", "ca", "Catalan"),
    MediaLanguage("fil", "fil", "Filipino"),
    MediaLanguage("tam", "ta", "Tamil"),
    MediaLanguage("tel", "te", "Telugu"),
    MediaLanguage("ben", "bn", "Bengali"),
    MediaLanguage("urd", "ur", "Urdu"),
    MediaLanguage("fas", "fa", "Persian"),
    MediaLanguage("lav", "lv", "Latvian"),
    MediaLanguage("lit", "lt", "Lithuanian"),
    MediaLanguage("est", "et", "Estonian"),
    MediaLanguage("isl", "is", "Icelandic"),
    MediaLanguage("swa", "sw", "Swahili"),
)

/**
 * The offered languages, named in the app's display language and mapped to their stored codes.
 * A French user picks "Espagnol", not "Spanish". The stored value is still the ISO 639-2 code,
 * so a change of the app's language keeps every saved preference valid.
 */
@Composable
internal fun mediaLanguageEntries(): Map<String, String> = buildMap {
    val appLanguage = Localization.lyricist.state.collectAsState().value.languageTag
    put(strings.settingLanguageNoPreference, "und")
    for (language in mediaLanguages) {
        put(localizedLanguageName(language.iso6391, appLanguage) ?: language.englishName, language.iso6392)
    }
}
