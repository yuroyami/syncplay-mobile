package app.i18n

/**
 * Fills the placeholders in a generated string.
 *
 * The Lyricist generator removes the `%1$` position markers from the XML. So only plain `%s`
 * and `%d` placeholders arrive here, and they are filled left to right. `%%` writes one literal
 * percent, which is the Android convention that translators know. Anything else, a lone `%`
 * included, is copied as is.
 *
 * This function is in the package of the generated code on purpose. The generated files import
 * nothing, so they resolve `format` from here. The `kotlin.text` version is JVM-only and does
 * not exist on iOS.
 */
internal fun String.format(vararg args: Any?): String {
    val out = StringBuilder(length + args.size * 8)
    var arg = 0
    var i = 0
    while (i < length) {
        val c = this[i]
        val next = if (i + 1 < length) this[i + 1] else ' '
        when {
            c == '%' && next == '%' -> { out.append('%'); i += 2 }
            c == '%' && (next == 's' || next == 'd') && arg < args.size -> {
                out.append(args[arg++].toString())
                i += 2
            }
            else -> { out.append(c); i++ }
        }
    }
    return out.toString()
}
