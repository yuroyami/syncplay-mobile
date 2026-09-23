import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import java.io.File

/**
 * Puts the generated language table in the same order on every machine.
 *
 * The Lyricist symbol processor (the KSP step that generates the string classes) writes the
 * `Locales` object and the `appStrings` map in the order that it walked the `values-*` resource
 * folders. That order comes from the filesystem, so two machines generate two different files.
 * The constants then land in a different order in the DEX, R8 gives the locale classes different
 * short names, and nobody can rebuild a published APK and compare it byte for byte. Sorting both
 * blocks by property name before any compilation removes the difference. The order itself does
 * not matter, only that every machine uses the same one.
 */
object LocaleOrder {

    private val FIELD = Regex("""^(\s*)val ([A-Za-z0-9_]+) = "[^"]*"\s*$""")
    private val ENTRY = Regex("""^(\s*)Locales\.([A-Za-z0-9_]+) to ([A-Za-z0-9_]+),?\s*$""")

    /**
     * Registers `sortGeneratedLocales`. The caller makes every compilation depend on it (see
     * shared/build.gradle.kts).
     *
     * The task always runs. It reads one small file and rewrites it only when the order is wrong,
     * so the result stays correct even if a build cache returns generated output from another
     * machine.
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

    /** Sorts both blocks. Fails when the generated file does not have the expected shape. */
    internal fun sort(text: String, name: String): String {
        val lines = text.split("\n").toMutableList()
        sortBlock(lines, name, "public object Locales {", "}", FIELD) { it.groupValues[2] }
        sortBlock(lines, name, "public val appStrings", ")", ENTRY) { it.groupValues[2] }
        return lines.joinToString("\n")
    }

    /**
     * Sorts the lines between the line that starts with [open] and the next line that is exactly
     * [close]. Every line between the two must match [pattern], and [key] reads the name to sort
     * on. When the entries use trailing commas, every entry except the last gets one after the
     * sort.
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
