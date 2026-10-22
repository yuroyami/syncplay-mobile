package io.github.yuroyami.synkplay.buildplugins.commonksp

import org.jetbrains.amper.plugins.ExecutionAvoidance
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.TaskAction
import org.w3c.dom.Element
import java.nio.file.Path
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Copies the English strings into the default-qualifier file, without any key that the
 * untranslatable file already declares. Two declarations of one key under the default qualifier
 * make the resource lookup throw on every device whose language has no translation of that key.
 *
 * The result is committed. The Compose resource tasks of the toolchain cannot wait for a plugin
 * task, so the build never writes it. The `defaultStrings` check fails when it is out of date.
 */
@TaskAction(ExecutionAvoidance.Disabled)
fun syncDefaultStrings(
    // Not outputs: the KSP task reads the resources, and it would then run this command first.
    @Input(inferTaskDependency = false) english: Path,
    @Input(inferTaskDependency = false) untranslatable: Path,
    @Input(inferTaskDependency = false) target: Path,
) {
    val text = defaultStrings(english, untranslatable) ?: return
    if (!target.exists() || target.readText() != text) {
        target.writeText(text)
        println("Wrote $target from ${english.fileName}.")
    }
}

/** Fails when the default-qualifier file differs from what [syncDefaultStrings] writes. */
@TaskAction
fun checkDefaultStrings(
    @Input(inferTaskDependency = false) english: Path,
    @Input(inferTaskDependency = false) untranslatable: Path,
    @Input(inferTaskDependency = false) target: Path,
) {
    val text = defaultStrings(english, untranslatable) ?: return
    check(target.exists() && target.readText() == text) {
        "$target is not a copy of ${english.fileName} without the untranslatable keys. Run `./kotlin do syncDefaultStrings`."
    }
}

/** The text of the default-qualifier file, or null when there is no English file. */
private fun defaultStrings(english: Path, untranslatable: Path): String? {
    if (!english.isRegularFile()) return null
    val reserved = if (untranslatable.isRegularFile()) stringNames(untranslatable) else emptySet()
    // Space around the equals sign is allowed, as the XML parser allows it.
    val nameOf = Regex("""^\s*<string\s+name\s*=\s*"([^"]+)"""")
    return english.readLines()
        .filterNot { nameOf.find(it)?.groupValues?.get(1) in reserved }
        .joinToString("\n") + "\n"
}

/** The `name` of every `<string>` in a resource file, read with an XML parser. */
private fun stringNames(file: Path): Set<String> {
    val factory = DocumentBuilderFactory.newInstance().apply {
        // No DTD and no external entities: a resource file never needs either.
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        isExpandEntityReferences = false
    }
    val strings = factory.newDocumentBuilder().parse(file.toFile()).getElementsByTagName("string")
    return (0 until strings.length).map { (strings.item(it) as Element).getAttribute("name") }.toSet()
}
