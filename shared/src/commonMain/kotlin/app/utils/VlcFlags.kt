package app.utils

import app.preferences.Preferences
import app.preferences.value

/**
 * Splits the user's VLC flags string into argument tokens for `VLCLibrary(args)` on iOS.
 *
 * Single- and double-quoted runs stay together, so a value can contain spaces: `--foo="a b c"`
 * becomes one token. The quote characters are removed from the token, and empty tokens are
 * dropped.
 *
 * This is a small splitter on purpose, not a full shell parser. It does not support backslash
 * escapes, environment variable expansion or comments.
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
 * Reads [Preferences.VLC_CUSTOM_FLAGS] and returns its tokens. Returns an empty list when the
 * preference is blank or cannot be read.
 */
fun vlcCustomFlags(): List<String> {
    return try {
        tokenizeVlcFlags(Preferences.VLC_CUSTOM_FLAGS.value())
    } catch (_: Throwable) {
        emptyList()
    }
}
