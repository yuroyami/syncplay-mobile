package app.design

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Source checks for the design rules. Each rule has a baseline count that may only go down. Lower
 * the baseline in the same commit that removes the usages.
 *
 * The last two rules are about reachability, not looks. A `.clickable {}` with no role and no
 * description is invisible to a screen reader and unreachable by a remote: it is a tap target
 * that announces nothing. The shared controls carry their own semantics, so any direct call to
 * clickable must say what it is. A source scan cannot tell whether a role comes with a name, so
 * [DesignHarness.render] also fails on a rendered control that has no spoken name.
 */
class DesignLint {

    private val root = File("src/commonMain/kotlin/app")

    /** MaterialKolor returns the colour scheme as this Material class, which only holds values. */
    private val allowedMaterialImports = setOf("ColorScheme")

    /** The two files that read that scheme to build the palette. */
    private val bridgeFiles = setOf("Tokens.kt", "SaveableTheme.kt")

    private val baseline = mapOf(
        "material3 component imports" to 0,
        "sp literals outside Tokens.kt" to 0,
        "MaterialTheme or ripple" to 0,
        "text sizes under 11sp" to 0,
        "clickable without semantics" to 0,
        // Both hide a switch inside a row that already speaks as that switch. Raise this only
        // after checking that the new use hides no control from a screen reader.
        "clearAndSetSemantics" to 2,
    )

    private fun sources(): List<File> = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun count(): Map<String, List<String>> {
        val hits = mutableMapOf<String, MutableList<String>>()
        fun hit(rule: String, file: File, line: Int, text: String) {
            hits.getOrPut(rule) { mutableListOf() }.add("${file.relativeTo(root)}:$line: ${text.trim()}")
        }
        val importRe = Regex("""^import androidx\.compose\.material3\.([A-Za-z0-9_]+)""")
        val clickableRe = Regex("""\.(clickable|combinedClickable|selectable|toggleable)\s*[({]""")
        val spRe = Regex("""\b(\d+(?:\.\d+)?)\.sp\b""")
        for (file in sources()) {
            val allLines = file.readLines()
            allLines.forEachIndexed { i, line ->
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
                /* clearAndSetSemantics hides every node inside, controls included, and a render
                 * cannot see what it hid. So each use needs a person to check it. */
                if (line.contains("clearAndSetSemantics") && !line.trimStart().startsWith("import ")) {
                    hit("clearAndSetSemantics", file, n, line)
                }
                if (line.contains("MaterialTheme") || line.contains("ripple(")) {
                    if (file.name !in bridgeFiles) hit("MaterialTheme or ripple", file, n, line)
                }
                if (clickableRe.containsMatchIn(line)) {
                    /* A role names the control for assistive technology, and selectable and
                     * toggleable take one directly. A nearby semantics block or contentDescription
                     * does the same job for a custom clickable. clearAndSetSemantics is the
                     * deliberate opposite: it marks a surface that is not a control at all.
                     *
                     * The window is wide because these modifier chains run long, and the
                     * semantics for a clickable often sit well below it. */
                    val window = allLines
                        .subList(maxOf(0, i - 8), minOf(allLines.size, i + 20))
                        .joinToString("\n")
                    val answered = listOf(
                        "role =", "role=", "semantics", "clearAndSetSemantics",
                        "contentDescription", "onClickLabel",
                    ).any { it in window }
                    if (!answered) hit("clickable without semantics", file, n, line)
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
