package app.design

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** The copy rules from DESIGN/COPY: titles fit a row, summaries fit a line, no Android escapes, no marketing words. */
class CopyLint {

    private val banned = listOf("seamless", "robust", "powerful", "leverage", "empower", "enhance", "optimize", "elevate", "streamline", "intuitive", "effortless", "delve")

    @Test
    fun rules() {
        val xml = File("src/commonMain/composeResources/values-en/strings.xml").readText()
        val entries = Regex("""<string name="([^"]+)">([^<]*)</string>""").findAll(xml).map { it.groupValues[1] to it.groupValues[2] }.toList()
        val failures = mutableListOf<String>()
        for ((name, value) in entries) {
            if (name.endsWith("_title") && value.length > 26) failures += "$name: title ${value.length} > 26: $value"
            if (name.endsWith("_summary") && value.length > 48) failures += "$name: summary ${value.length} > 48: $value"
            if (value.contains("\\'") || (value.startsWith("\"") && value.endsWith("\""))) failures += "$name: Android style escape: $value"
            if (name.endsWith("_title") || name.endsWith("_summary") || name.endsWith("_detail")) {
                banned.firstOrNull { Regex("\\b$it", RegexOption.IGNORE_CASE).containsMatchIn(value) }?.let { failures += "$name: banned word $it: $value" }
            }
        }
        println("COPY ${entries.size} strings checked, ${failures.size} failures")
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }
}
