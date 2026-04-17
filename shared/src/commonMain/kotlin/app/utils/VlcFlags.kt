package app.utils

import app.preferences.Preferences
import app.preferences.value

/**
 * Splits a user-provided VLC flags string into individual argument tokens suitable for handing to
 * `LibVLC(ctx, args)` (Android) or `VLCLibrary(args)` (iOS).
 *
 * The splitter respects single- and double-quoted runs so values containing whitespace can be
 * passed, e.g. `--foo="a b c"` becomes one token. Quote characters are stripped from the token.
 * Empty tokens are dropped.
 *
 * This is deliberately a small hand-rolled splitter rather than a full shell parser: backslash
 * escapes, env-var expansion, comments, etc. are NOT supported — users who need those can use
 * mpv.conf instead.
 */
fun tokenizeVlcFlags(raw: String): List<String> {
    if (raw.isBlank()) return emptyList()
    val out = ArrayList<String>()
    val current = StringBuilder()
    var quote: Char? = null
    for (c in raw) {
        when {
            quote != null -> {
                if (c == quote) quote = null else current.append(c)
            }
            c == '"' || c == '\'' -> quote = c
            c.isWhitespace() -> {
                if (current.isNotEmpty()) {
                    out.add(current.toString())
                    current.clear()
                }
            }
            else -> current.append(c)
        }
    }
    if (current.isNotEmpty()) out.add(current.toString())
    return out
}

/**
 * Convenience: reads [Preferences.VLC_CUSTOM_FLAGS] and returns the parsed token list.
 * Returns an empty list if the preference is blank or unreadable.
 */
fun vlcCustomFlags(): List<String> {
    return try {
        tokenizeVlcFlags(Preferences.VLC_CUSTOM_FLAGS.value())
    } catch (_: Throwable) {
        emptyList()
    }
}
