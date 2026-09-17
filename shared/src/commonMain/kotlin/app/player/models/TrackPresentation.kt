package app.player.models

import app.preferences.mediaLanguages

internal data class TrackLanguage(val code: String, val flag: String, val fallbackName: String?)

/** Explicit regions win. A globe covers unspecified, multilingual and unrecognised languages. */
internal fun trackLanguage(raw: String?): TrackLanguage {
    val parts = raw.orEmpty().trim().replace('_', '-').split('-')
    val primary = parts.first().lowercase()
    if (primary in listOf("", "und", "mul", "zxx")) return TrackLanguage("und", "🌐", null)
    val canonical = LANGUAGE_ALIASES[primary] ?: primary
    val language = mediaLanguages.firstOrNull { it.iso6391 == canonical || it.iso6392 == canonical }
    val code = language?.iso6391 ?: canonical
    val region = parts.drop(1).takeWhile { it.length != 1 }
        .firstOrNull { it.length == 2 && it.all { c -> c.uppercaseChar() in 'A'..'Z' } }
        ?.uppercase() ?: LANGUAGE_REGIONS[code]
    val flag = region?.map { c ->
        val point = 0x1F1E6 + (c - 'A') - 0x10000
        "${(0xD800 + (point shr 10)).toChar()}${(0xDC00 + (point and 0x3FF)).toChar()}"
    }?.joinToString("") ?: "🌐"
    return TrackLanguage(code, flag, language?.englishName)
}

/** Channel count alone cannot distinguish 5.1 from 6.0, so do not invent a speaker layout. */
internal fun channelBadge(count: Int?, layout: String?): String? {
    val surround = Regex("[1-9]\\.[0-9](?:\\([^)]*\\))?").matchEntire(layout.orEmpty())
    if (surround != null) return layout!!.substringBefore('(') + " ch"
    return count?.takeIf { it > 0 }?.let { "$it ch" }
}

internal fun codecBadge(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }?.let {
    when (it.lowercase()) {
        "mp4a-latm", "aac" -> "AAC"
        "eac3", "eac3-joc" -> "E-AC-3"
        "ac3" -> "AC-3"
        "truehd" -> "TrueHD"
        else -> it.uppercase().take(12)
    }
}

private val LANGUAGE_ALIASES = mapOf(
    "fre" to "fra", "ger" to "deu", "chi" to "zho", "dut" to "nld", "cze" to "ces",
    "rum" to "ron", "gre" to "ell", "per" to "fas", "slo" to "slk", "ice" to "isl",
    "may" to "msa", "iw" to "he", "in" to "id", "nb" to "no", "nn" to "no",
)
private val LANGUAGE_REGIONS = mapOf(
    "en" to "GB", "es" to "ES", "fr" to "FR", "de" to "DE", "it" to "IT", "pt" to "PT",
    "ru" to "RU", "ja" to "JP", "ko" to "KR", "zh" to "CN", "hi" to "IN", "tr" to "TR",
    "pl" to "PL", "nl" to "NL", "sv" to "SE", "no" to "NO", "da" to "DK", "fi" to "FI",
    "hu" to "HU", "cs" to "CZ", "ro" to "RO", "el" to "GR", "he" to "IL", "th" to "TH",
    "vi" to "VN", "id" to "ID", "ms" to "MY", "uk" to "UA", "bg" to "BG", "hr" to "HR",
    "sr" to "RS", "sk" to "SK", "sl" to "SI", "ca" to "ES", "fil" to "PH", "ta" to "IN",
    "te" to "IN", "bn" to "BD", "ur" to "PK", "fa" to "IR", "lv" to "LV", "lt" to "LT",
    "et" to "EE", "is" to "IS",
)
