package app.design

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The four source checks from DESIGN/FOUNDATION, as a ratchet: each count may only go down.
 * Lower the baseline in the same commit that removes the usages.
 */
class DesignLint {

    private val root = File("src/commonMain/kotlin/app")

    private val allowedMaterialImports = setOf(
        "MaterialTheme", "Text", "Icon", "ColorScheme", "Typography", "Shapes",
        "darkColorScheme", "lightColorScheme", "ExperimentalMaterial3Api", "ExperimentalMaterial3ExpressiveApi",
    )

    /** Files that may read the colour scheme or build the Material bridge. */
    private val bridgeFiles = setOf("Tokens.kt", "AppTypography.kt", "SaveableTheme.kt")

    private val baseline = mapOf(
        "material3 component imports" to 1,
        "sp literals outside Tokens.kt" to 0,
        "MaterialTheme.typography or ripple" to 0,
        "text sizes under 11sp" to 0,
    )

    private fun sources(): List<File> = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun count(): Map<String, List<String>> {
        val hits = mutableMapOf<String, MutableList<String>>()
        fun hit(rule: String, file: File, line: Int, text: String) {
            hits.getOrPut(rule) { mutableListOf() }.add("${file.relativeTo(root)}:$line: ${text.trim()}")
        }
        val importRe = Regex("""^import androidx\.compose\.material3\.([A-Za-z0-9_]+)""")
        val spRe = Regex("""\b(\d+(?:\.\d+)?)\.sp\b""")
        for (file in sources()) {
            file.readLines().forEachIndexed { i, line ->
                val n = i + 1
                importRe.find(line)?.let { m ->
                    if (m.groupValues[1] !in allowedMaterialImports) hit("material3 component imports", file, n, line)
                }
                if (file.name != "Tokens.kt") {
                    spRe.findAll(line).forEach { m ->
                        hit("sp literals outside Tokens.kt", file, n, line)
                        if (m.groupValues[1].toFloat() < 11f && !line.contains("letterSpacing")) hit("text sizes under 11sp", file, n, line)
                    }
                }
                if (line.contains("MaterialTheme.typography") || line.contains("ripple(")) {
                    if (file.name !in bridgeFiles) hit("MaterialTheme.typography or ripple", file, n, line)
                }
            }
        }
        return hits
    }

    @Test
    fun ratchet() {
        val hits = count()
        val failures = mutableListOf<String>()
        for ((rule, max) in baseline) {
            val n = hits[rule]?.size ?: 0
            println("LINT $rule: $n (baseline $max)")
            if (n > max) failures += "$rule: $n > $max\n" + hits[rule]!!.take(20).joinToString("\n")
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n\n"))
    }
}
