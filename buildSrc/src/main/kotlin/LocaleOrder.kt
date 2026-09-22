import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import java.io.File

/**
 * Puts the generated language table in the same order on every machine.
 *
 * The Lyricist symbol processor writes the `Locales` object and the `appStrings` map in the order
 * it walked the `values-*` resource folders. That order is the filesystem's, so two machines
 * generate two different files. The constants then land in a different order in the DEX, R8 gives
 * the locale classes different short names, and a published APK cannot be rebuilt and compared
 * byte for byte. Sorting both blocks by property name before anything compiles them removes the
 * difference. Which order it is does not matter, only that every machine picks the same one.
 */
object LocaleOrder {

    private val FIELD = Regex("""^(\s*)val ([A-Za-z0-9_]+) = "[^"]*"\s*$""")
    private val ENTRY = Regex("""^(\s*)Locales\.([A-Za-z0-9_]+) to ([A-Za-z0-9_]+),?\s*$""")

    /**
     * Registers `sortGeneratedLocales` and makes every compilation wait for it.
     *
     * The task always runs. It reads one small file and rewrites it only when the order is wrong,
     * which keeps it correct even if a build cache ever hands back generated output that another
     * machine produced.
     */
    fun Project.registerLocaleOrderTask(): TaskProvider<*> {
        val generatedDir = layout.buildDirectory.dir("generated/ksp/metadata/commonMain/kotlin/app/i18n")
        return tasks.register("sortGeneratedLocales") {
            group = "syncplay"
            description = "Sorts the generated language table, so every machine compiles the same file."
            dependsOn("kspCommonMainKotlinMetadata")
            outputs.upToDateWhen { false }
            val dir = generatedDir
            doLast {
                val file = dir.get().asFile.listFiles { f: File -> f.name.endsWith(".kt") }
                    ?.firstOrNull { it.readText().contains("public object Locales") }
                    ?: error("No generated file holds the Locales object. Look in ${dir.get().asFile}.")
                val sorted = sort(file.readText(), file.name)
                if (sorted != file.readText()) file.writeText(sorted)
            }
        }
    }

    /** Sorts both blocks. Fails loudly when the generated shape is not the one this reads. */
    internal fun sort(text: String, name: String): String {
        val lines = text.split("\n").toMutableList()
        sortBlock(lines, name, "public object Locales {", "}", FIELD) { it.groupValues[2] }
        sortBlock(lines, name, "public val appStrings", ")", ENTRY) { it.groupValues[2] }
        return lines.joinToString("\n")
    }

    /**
     * Sorts the lines between the line that starts with [open] and the next line that is exactly
     * [close]. Every line between the two must match [pattern], and [key] reads the name to sort
     * on. A trailing comma belongs to every entry but the last, so it is rewritten either way.
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
        if (start < 0) error("$name has no line starting with \"$open\". The generator changed shape.")
        val end = (start + 1 until lines.size).firstOrNull { lines[it].trim() == close }
            ?: error("$name has no \"$close\" closing \"$open\". The generator changed shape.")

        val entries = (start + 1 until end).map { i ->
            pattern.matchEntire(lines[i])
                ?: error("$name line ${i + 1} is not an entry this task can sort: ${lines[i]}")
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
