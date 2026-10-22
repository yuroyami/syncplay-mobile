package io.github.yuroyami.synkplay.buildplugins.commonksp

/** Sorts the two blocks of the generated Lyricist file that list the languages. */
internal object LocaleOrder {
    private val FIELD = Regex("""^(\s*)val ([A-Za-z0-9_]+) = "[^"]*"\s*$""")
    private val ENTRY = Regex("""^(\s*)Locales\.([A-Za-z0-9_]+) to ([A-Za-z0-9_]+),?\s*$""")

    /** Sorts both blocks. Fails when the generated file does not have the expected shape. */
    fun sort(text: String, name: String): String {
        val lines = text.split("\n").toMutableList()
        sortBlock(lines, name, "public object Locales {", "}", FIELD) { it.groupValues[2] }
        sortBlock(lines, name, "public val appStrings", ")", ENTRY) { it.groupValues[2] }
        return lines.joinToString("\n")
    }

    /**
     * Sorts the lines between the line that starts with [open] and the next line that is exactly
     * [close]. Every line between the two must match [pattern]. When the entries use trailing
     * commas, every entry except the last gets one after the sort.
     */
    private fun sortBlock(
        lines: MutableList<String>,
        name: String,
        open: String,
        close: String,
        pattern: Regex,
        key: (MatchResult) -> String,
    ) {
        val start = lines.indexOfFirst { it.trimStart().startsWith(open) }
        check(start >= 0) { "$name has no line starting with \"$open\". The generator changed shape." }
        val end = (start + 1 until lines.size).firstOrNull { lines[it].trim() == close }
            ?: error("$name has no \"$close\" closing \"$open\". The generator changed shape.")
        val entries = (start + 1 until end).map { i ->
            pattern.matchEntire(lines[i]) ?: error("$name line ${i + 1} is not an entry this task can sort: ${lines[i]}")
        }
        if (entries.isEmpty()) return
        val comma = entries.first().value.trimEnd().endsWith(",")
        val body = entries.sortedBy { key(it) }.mapIndexed { i, m ->
            val line = m.value.trimEnd().removeSuffix(",")
            if (comma && i < entries.size - 1) "$line," else line
        }
        for ((offset, line) in body.withIndex()) lines[start + 1 + offset] = line
    }
}
