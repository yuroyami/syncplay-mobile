package io.github.yuroyami.synkplay.buildplugins.commonksp

/**
 * Sorts the members of the generated Lyricist string interface and of each language object by name.
 *
 * Lyricist writes the members in the order that it reads `strings.xml` and
 * `strings_untranslatable.xml`, and that order comes from the file system (#166).
 */
internal object StringOrder {
    private const val INTERFACE_START = "public interface AppStrings {"
    private val INTERFACE_MEMBER = Regex("""^\s*val ([A-Za-z0-9_]+): \S.*$""")
    private val OBJECT_START = Regex("""^val [A-Za-z0-9_]+ = object : AppStrings \{$""")
    private val OBJECT_MEMBER = Regex("""^\s*override val ([A-Za-z0-9_]+) = (\S.*)$""")

    /** True when [text] declares the string interface. */
    fun hasInterface(text: String): Boolean = text.lineSequence().any { it == INTERFACE_START }

    /** True when [text] declares a language object. */
    fun hasObject(text: String): Boolean = text.lineSequence().any { OBJECT_START.matches(it) }

    /** Sorts the members of the string interface. Fails when the generated file has another shape. */
    fun sortInterface(text: String, name: String): String {
        val lines = text.split("\n").toMutableList()
        val (start, end) = body(lines, name, INTERFACE_START)
        val members = (start + 1 until end).map { i ->
            val match = INTERFACE_MEMBER.matchEntire(lines[i])
                ?: error("$name line ${i + 1} is not an interface member this task can sort: ${lines[i]}")
            match.groupValues[1] to lines[i]
        }
        for ((offset, member) in members.sortedBy { it.first }.withIndex()) lines[start + 1 + offset] = member.second
        return lines.joinToString("\n")
    }

    /**
     * Sorts the members of the language object. A member starts on an `override val` line and runs
     * to the next one, such as a `listOf(` block or a format lambda. Blank lines separate them.
     */
    fun sortObject(text: String, name: String): String {
        val lines = text.split("\n")
        val startLine = lines.first { OBJECT_START.matches(it) }
        val (start, end) = body(lines, name, startLine)
        val members = mutableListOf<Pair<String, MutableList<String>>>()
        for (i in start + 1 until end) {
            val match = OBJECT_MEMBER.matchEntire(lines[i])
            when {
                match != null -> members += match.groupValues[1] to mutableListOf("    " + lines[i].trimStart())
                members.isEmpty() && lines[i].isNotBlank() ->
                    error("$name line ${i + 1} comes before the first member: ${lines[i]}")
                members.isNotEmpty() -> members.last().second += lines[i]
            }
        }
        val sorted = members.map { (key, member) ->
            val trimmed = member.dropLastWhile { it.isBlank() }
            check(balanced(trimmed)) { "$name: the member $key does not close its brackets. The generator changed shape." }
            key to trimmed
        }.sortedBy { it.first }
        check(sorted.map { it.first }.toSet().size == sorted.size) { "$name declares a member twice. The generator changed shape." }
        val body = sorted.joinToString("\n\n") { it.second.joinToString("\n") }
        return (lines.subList(0, start + 1) + body + lines.subList(end, lines.size)).joinToString("\n")
    }

    /** True when the brackets in [lines] close, outside of string literals. */
    private fun balanced(lines: List<String>): Boolean {
        var depth = 0
        var inString = false
        val code = lines.joinToString("\n")
        var i = 0
        while (i < code.length) {
            val c = code[i]
            when {
                inString && c == '\\' -> i++
                c == '"' -> inString = !inString
                !inString && c in "([{" -> depth++
                !inString && c in ")]}" -> if (--depth < 0) return false
            }
            i++
        }
        return depth == 0 && !inString
    }

    /** The index of the line that is [open] and of the next line that is exactly `}`. */
    private fun body(lines: List<String>, name: String, open: String): Pair<Int, Int> {
        val start = lines.indexOf(open)
        check(start >= 0) { "$name has no line \"$open\". The generator changed shape." }
        val end = (start + 1 until lines.size).firstOrNull { lines[it] == "}" }
            ?: error("$name has no \"}\" closing \"$open\". The generator changed shape.")
        return start to end
    }
}
