package io.github.yuroyami.synkplay.buildplugins.qualitygates

import org.jetbrains.amper.plugins.ExecutionAvoidance
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

/** `SYNKPLAY_STRICT_LOCALES=true` makes the locale gate fail on missing translations too. */
private const val STRICT_LOCALES_ENV = "SYNKPLAY_STRICT_LOCALES"

/** The folders of this repository that the gates read. */
private class Layout(val root: Path) {
    val shared: Path = root.resolve("shared")
    val resources: Path = shared.resolve("composeResources")
    val english: Path = resources.resolve("values-en/strings.xml")
    val localeConfig: Path = root.resolve("androidApp/res/xml/locales_config.xml")

    /** `shared/src` and every `shared/src@<platform>` folder, sorted by name. */
    val sharedMain: List<Path> = shared.listDirectoryEntries()
        .filter { it.isDirectory() && (it.name == "src" || it.name.startsWith("src@")) }
        .sortedBy { it.name }

    /** The source folders of the app modules: Kotlin, the Android manifest and the Android resources. */
    val apps: List<Path> = listOf("androidApp/src", "androidApp/res", "desktopApp/src", "webApp/src", "webApp/resources")
        .map(root::resolve).filter { it.isDirectory() }

    /** The `values-xx` folders except the English source, sorted. */
    val translations: List<String> = resources.listDirectoryEntries()
        .filter { it.isDirectory() && it.name.startsWith("values-") && it.name != "values-en" }
        .map { it.name }.sorted()

    fun where(file: Path): String = file.relativeTo(root).invariantSeparatorsPathString
}

@OptIn(ExperimentalPathApi::class)
private fun List<Path>.files(vararg extensions: String): List<Path> =
    flatMap { if (it.isDirectory()) it.walk().toList() else listOf(it) }
        .filter { it.isRegularFile() && it.extension in extensions }
        .sortedBy { it.invariantSeparatorsPathString }

/**
 * Runs every quality gate and fails with the problems of all failed gates. Each gate covers a rule
 * that broke once, or a standing decision that no other tool checks. They are plain file scans,
 * because each one reads files that a Kotlin analyzer does not read: the locale XML files, the
 * resource tree, the settings registry.
 */
@TaskAction(ExecutionAvoidance.Disabled)
fun qualityGates(
    // The gates read other modules too. Reading them must not make the toolchain build them first.
    @Input(inferTaskDependency = false) projectDir: Path,
) {
    val layout = Layout(projectDir)
    val gates = listOf(
        "protocol throws" to ::checkProtocolThrows,
        "string resources" to ::checkStringResources,
        "locale parity" to ::checkLocaleParity,
        "string arguments" to ::checkStringArguments,
        "placeholder comments" to ::checkPlaceholderComments,
        "dead resources" to ::reportDeadResources,
        "settings reachability" to ::checkSettingsReachable,
        "destroy contract" to ::checkDestroyContract,
        "blocking reads" to ::checkBlockingReads,
    )
    val failures = gates.mapNotNull { (name, gate) ->
        runCatching { gate(layout) }.exceptionOrNull()?.let { "[$name] ${it.message}" }
    }
    check(failures.isEmpty()) { failures.joinToString("\n\n") }
    println("All ${gates.size} quality gates passed.")
}

/**
 * Inbound protocol code must throw SerializationException and nothing else. Both sides catch every
 * exception, but only that type reads as a malformed line. An `error()`, `check()` or `require()`
 * there makes the server answer "Internal server error" and log a broken handler instead.
 */
private fun checkProtocolThrows(layout: Layout) {
    // The protocol folder of every main source folder, so a transport in a new folder is checked too.
    // listOf: a Path is an Iterable of its name parts, so `list + path` would add each part.
    val sources = layout.sharedMain.map { it.resolve("app/protocol") } + listOf(layout.shared.resolve("src/app/server/ClientConnection.kt"))
    val banned = Regex("""(^|[^\w."])(error|check|checkNotNull|require|requireNotNull)\s*\(""")
    val offenders = sources.files("kt").flatMap { file ->
        file.readLines().withIndex()
            .filter { (_, line) ->
                val code = line.substringBefore("//").trim()
                // A declaration named error() is the WireMessage builder, not a throw.
                !code.startsWith("*") && !code.contains("fun error(") && banned.containsMatchIn(code)
            }
            .map { (i, line) -> "${layout.where(file)}:${i + 1}: ${line.trim()}" }
    }
    check(offenders.isEmpty()) {
        "Inbound protocol code must throw SerializationException, nothing else.\n" +
            "These throw IllegalStateException or IllegalArgumentException instead,\n" +
            "which the skip-a-poisoned-line catch does not cover:\n" + offenders.joinToString("\n") { "  $it" }
    }
}

/** String resources must load, and each key must have one value. */
private fun checkStringResources(layout: Layout) {
    val problems = mutableListOf<String>()
    layout.resources.listDirectoryEntries().sortedBy { it.name }.forEach { dir ->
        val xml = dir.resolve("strings.xml").takeIf { it.isRegularFile() } ?: return@forEach
        val entries = StringResources.read(xml)
        val where = layout.where(xml)
        val names = entries.filter { it.kind == ResourceKind.STRING }.map { it.name }
        names.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted().forEach { problems += "$where: duplicate key '$it'" }
        val plurals = entries.filter { it.kind == ResourceKind.PLURALS }.map { it.name }
        plurals.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted().forEach { problems += "$where: duplicate plural '$it'" }
        (names.toSet() intersect plurals.toSet()).sorted().forEach { problems += "$where: '$it' is both a string and a plural" }
        // No apostrophe rule, on purpose: these are Compose resources, so an apostrophe needs no escape.
    }
    check(problems.isEmpty()) { "String resources will not load:\n" + problems.joinToString("\n") { "  $it" } }
}

/**
 * Locale parity. Missing translations are reported, not failed, because translations arrive through
 * Weblate on their own schedule. A key that exists only in a translation fails: the source key was
 * renamed or deleted. Set SYNKPLAY_STRICT_LOCALES=true to fail on missing keys as well.
 */
private fun checkLocaleParity(layout: Layout) {
    fun keysIn(dir: String): Set<String> {
        val xml = layout.resources.resolve("$dir/strings.xml")
        return if (xml.isRegularFile()) StringResources.keys(xml).toSet() else emptySet()
    }
    val source = keysIn("values-en")
    check(source.isNotEmpty()) { "values-en/strings.xml has no keys; the source of truth is missing" }

    val orphans = mutableListOf<String>()
    println("Locale parity against values-en (${source.size} keys):")
    for (locale in layout.translations) {
        val keys = keysIn(locale)
        val missing = source - keys
        val extra = keys - source
        val percent = (keys.count { it in source } * 100) / source.size
        println("  $locale: $percent%, ${missing.size} missing, ${extra.size} not in the source")
        extra.sorted().forEach { orphans += "$locale: '$it' does not exist in values-en" }
    }

    // Android's per-app language setting offers only the languages that this file lists.
    val shipped = (layout.translations + "values-en").map { it.removePrefix("values-") }.toSortedSet()
    val declared = StringResources.localeConfigNames(layout.localeConfig).toSortedSet()
    check(declared == shipped) {
        "${layout.where(layout.localeConfig)} lists ${declared.joinToString()}, but the app ships ${shipped.joinToString()}.\n" +
            "Android's per-app language setting offers only the listed languages."
    }
    check(orphans.isEmpty()) {
        "These translated keys have no source key, so nothing can ever show them.\n" +
            "Either the source key was renamed or the translation is stale:\n" + orphans.joinToString("\n") { "  $it" }
    }
    if (System.getenv(STRICT_LOCALES_ENV).equals("true", ignoreCase = true)) {
        val incomplete = layout.translations.filter { (source - keysIn(it)).isNotEmpty() }
        check(incomplete.isEmpty()) { "$STRICT_LOCALES_ENV: incomplete locales: ${incomplete.joinToString()}" }
    }
}

/**
 * Placeholders must match across languages. Lyricist strips the `%1$` position markers and fills
 * the rest from left to right, so a translation that reorders its placeholders shows the wrong
 * value. Repeats fail for the same reason: `%1$s` twice becomes two separate arguments.
 */
private fun checkStringArguments(layout: Layout) {
    val marker = Regex("""%(\d+)\$""")
    fun argsIn(dir: String): Map<String, List<Int>> {
        val xml = layout.resources.resolve("$dir/strings.xml")
        if (!xml.isRegularFile()) return emptyMap()
        return StringResources.read(xml).filter { it.kind == ResourceKind.STRING }.associate { entry ->
            entry.name to marker.findAll(entry.text).map { it.groupValues[1].toInt() }.toList()
        }
    }
    val problems = mutableListOf<String>()
    val source = argsIn("values-en")
    source.forEach { (key, args) ->
        if (args.size != args.distinct().size) problems += "values-en: '$key' uses a placeholder more than once; give it its own number"
    }
    for (locale in layout.translations) {
        argsIn(locale).forEach { (key, args) ->
            val expected = source[key] ?: return@forEach
            if (args != expected) problems += "$locale: '$key' has $args, values-en has $expected"
        }
    }
    check(problems.isEmpty()) {
        "Placeholders do not match the source. Filled left to right, these would\n" +
            "print the wrong value or none at all:\n" + problems.joinToString("\n") { "  $it" }
    }
}

/**
 * Every English string with a placeholder has a comment directly above it that names each
 * placeholder, for example `<!-- %1$s is a user name. -->`. Weblate shows that comment to translators.
 */
private fun checkPlaceholderComments(layout: Layout) {
    val marker = Regex("""%(\d+)\$""")
    val missing = StringResources.read(layout.english).filter { it.kind == ResourceKind.STRING }.mapNotNull { entry ->
        val numbers = marker.findAll(entry.text).map { it.groupValues[1] }.toSet()
        val named = marker.findAll(entry.comment.orEmpty()).map { it.groupValues[1] }.toSet()
        val unnamed = numbers - named
        if (unnamed.isEmpty()) null else "${entry.name}: no comment names ${unnamed.sorted().joinToString { "%$it" }}"
    }
    check(missing.isEmpty()) {
        "Add a comment directly above each of these strings in values-en/strings.xml,\n" +
            "saying what each placeholder holds:\n" + missing.joinToString("\n") { "  $it" }
    }
}

/** The name Lyricist gives a string key: `connect_username` becomes `connectUsername`. */
internal fun camelCased(key: String): String {
    val parts = key.split("_")
    return parts.first().lowercase() + parts.drop(1).joinToString("") { part -> part.replaceFirstChar { it.uppercaseChar() } }
}

/** Reports string keys and drawables that nothing references. It warns only. */
private fun reportDeadResources(layout: Layout) {
    if (!layout.english.isRegularFile()) return
    val declared = StringResources.keys(layout.english).toSet()
    val code = (layout.sharedMain + layout.apps).files("kt", "xml").joinToString("\n") { it.readText() }
    // Strings are read through Lyricist, which renames every key, so either spelling counts.
    val unused = declared.filter { key ->
        listOf(key, camelCased(key)).none { Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(code) }
    }.sorted()
    // The desktop-package plugin builds the installer icons from these two by path (shared/module.yaml).
    val readByPath = setOf("synkplay_bg", "synkplay_fg")
    val drawables = layout.resources.resolve("drawable").listDirectoryEntries()
        .filter { it.isRegularFile() }
        .map { it.nameWithoutExtension }
        .filter { it !in readByPath && !Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(code) }
        .sorted()
    if (unused.isEmpty() && drawables.isEmpty()) {
        println("No dead resources.")
    } else {
        println("WARNING: dead resources (${unused.size} strings, ${drawables.size} drawables):")
        unused.forEach { println("  string $it") }
        drawables.forEach { println("  drawable $it") }
        println("Nothing references these. Delete them, or point something at them.")
    }
}

/**
 * A preference that declares a title, a summary and an icon is meant to be shown. If no code
 * outside its own declaration names it, no screen can show it. The scan covers the whole app,
 * because many preferences appear on their own panel instead of the settings screen.
 */
private fun checkSettingsReachable(layout: Layout) {
    val prefsFile = layout.shared.resolve("src/app/preferences/Preferences.kt")
    // `val NAME = Pref(...) {` with a config block is a preference meant to be displayed.
    val displayable = Regex("""\n    val ([A-Z][A-Z0-9_]*)\s*(?::[^=\n]+)?=\s*Pref[^\n]*\{""")
        .findAll(prefsFile.readText()).map { it.groupValues[1] }.toSet()
    // Engines attach their own settings rows, so engine files count as places to show one.
    val consumers = layout.sharedMain.map { it.resolve("app") }.files("kt").filter { it != prefsFile }.joinToString("\n") { it.readText() }
    val unreachable = displayable.filter { name -> !Regex("\\b" + Regex.escape(name) + "\\b").containsMatchIn(consumers) }.sorted()
    check(unreachable.isEmpty()) {
        "These preferences declare a title, summary and icon but no settings\n" +
            "category lists them, so nobody can reach them:\n" + unreachable.joinToString("\n") { "  Preferences.$it" }
    }
    println("All ${displayable.size} displayable preferences are reachable.")
}

/**
 * The engine destroy contract. An engine is one of the video players that the app can drive. Every
 * `destroy()` must set `isInitialized = false` first, then cancel `playerSupervisorJob`, and only
 * then release the native engine. A position tracker that outlives the teardown calls into a
 * released engine, which can abort the process.
 */
private fun checkDestroyContract(layout: Layout) {
    val problems = mutableListOf<String>()
    var checked = 0
    layout.sharedMain.map { it.resolve("app/player") }.files("kt").forEach { file ->
        val lines = file.readLines()
        lines.forEachIndexed { i, line ->
            if (!line.contains("override suspend fun destroy()")) return@forEachIndexed
            checked++
            val where = "${layout.where(file)}:${i + 1}"
            // Read to the end of the function: the first line with the declaration's indent that closes a brace.
            val indent = line.takeWhile { it == ' ' }
            val body = lines.drop(i + 1).takeWhile { it.trimEnd() != "$indent}" }.map { it.substringBefore("//") }
            val guard = body.indexOfFirst { it.contains("isInitialized = false") }
            val cancel = body.indexOfFirst { it.contains("playerSupervisorJob.cancel()") }
            when {
                guard < 0 -> problems += "$where: never sets isInitialized = false"
                cancel < 0 -> problems += "$where: never cancels playerSupervisorJob"
                guard > cancel -> problems += "$where: cancels playerSupervisorJob before dropping the isInitialized guard"
            }
        }
    }
    check(problems.isEmpty()) {
        "A player engine broke the destroy contract. It must flip the guard, then\n" +
            "cancel the supervisor, then release the engine:\n" + problems.joinToString("\n") { "  $it" }
    }
    println("All $checked engine destroy() bodies follow the contract.")
}

/**
 * A blocking read on the main thread freezes the interface. This gate fails on any `runBlocking`
 * or `readBlockingOrNull` call in app code outside the allowed places below.
 */
private fun checkBlockingReads(layout: Layout) {
    val allowed = mapOf(
        // The one helper: its actual blocks on purpose, for the startup read below.
        "shared/src@nonWeb/app/preferences/BlockingRead.nonWeb.kt" to "runBlocking",
        // The one startup read of the whole store, before the first frame.
        "shared/src/app/preferences/Datastore.kt" to "readBlockingOrNull",
        // The crash hook waits on purpose while it flushes the log, because the process is ending.
        "shared/src@ios/app/IOSApp.kt" to "runBlocking",
    )
    val call = Regex("""\b(runBlocking|readBlockingOrNull)\s*[({<]""")
    val offenders = (layout.sharedMain + layout.apps).files("kt").flatMap { file ->
        val where = layout.where(file)
        file.readLines().withIndex()
            .filter { (_, line) ->
                val code = line.substringBefore("//").trim()
                !code.startsWith("*") && !code.startsWith("import ") && !code.contains("fun <T> readBlockingOrNull")
            }
            .flatMap { (i, line) -> call.findAll(line.substringBefore("//")).map { i to it.groupValues[1] } }
            .filter { (_, name) -> allowed[where] != name }
            .map { (i, name) -> "$where:${i + 1}: $name" }
    }
    check(offenders.isEmpty()) {
        "A blocking call on the main thread freezes the interface. Read the preference\n" +
            "store without blocking, or add the call to the allowed list with its reason:\n" + offenders.joinToString("\n") { "  $it" }
    }
    println("No blocking reads outside the allowed places.")
}
