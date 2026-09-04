package app.preferences

import androidx.compose.runtime.Composable
import app.utils.localizedLanguageName
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.setting_language_no_preference

/**
 * One offered media language. [iso6392] is what the track preferences store and what the player
 * engines match on; [iso6391] is the code the platform knows how to name; [englishName] is the
 * fallback for a platform that has no name for it.
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
 * Language names in the reader's own language. A French user picks "Espagnol", not "Spanish".
 * The stored value is still the ISO 639-2 code, so switching the app's language does not
 * invalidate anyone's saved preference.
 */
@Composable
internal fun mediaLanguageEntries(): Map<String, String> = buildMap {
    put(stringResource(Res.string.setting_language_no_preference), "und")
    for (language in mediaLanguages) {
        put(localizedLanguageName(language.iso6391) ?: language.englishName, language.iso6392)
    }
}
