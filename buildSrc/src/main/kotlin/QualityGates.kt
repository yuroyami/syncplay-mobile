import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register
import java.io.File

/**
 * The build-time gates: rules this codebase relies on that nothing was checking.
 *
 * Each one exists because the thing it checks has already gone wrong once, or because a
 * standing decision was being kept by memory alone. They are plain scans rather than lint or
 * detekt rules, because every one of them needs to see files a Kotlin analyser does not read:
 * eight locale XMLs, a resource tree, the settings registry.
 *
 * Registered by [registerQualityGates] and wired into `:shared:check`.
 */

private const val GATE_GROUP = "verification"

/** Every gate, in one place, hung off the module that owns the code. */
fun Project.registerQualityGates() {
    val gates = listOf(
        registerProtocolThrowsGate(),
        registerStringResourceGate(),
        registerLocaleParityGate(),
        registerDeadResourceGate(),
        registerSettingsReachabilityGate(),
    )
    tasks.register("qualityGates") {
        group = GATE_GROUP
        description = "Runs every build-time gate: protocol throws, string resources, locale parity, dead resources."
        dependsOn(gates)
    }
    gradle.projectsEvaluated {
        project(":shared").tasks.findByName("check")?.dependsOn(gates)
    }
}

// ---------------------------------------------------------------------------------------------

/**
 * Inbound protocol code must throw SerializationException and nothing else, because that is the
 * only type the skip-a-poisoned-line catch covers. An `error()`, `check()` or `require()` there
 * turns one malformed line into a dropped connection.
 *
 * A scan rather than detekt's ForbiddenMethodCall, which needs type resolution the plain detekt
 * task does not have: written as a rule, it looked right and never fired once.
 */
private fun Project.registerProtocolThrowsGate(): TaskProvider<*> {
    val sources = listOf(
        file("shared/src/commonMain/kotlin/app/protocol"),
        file("shared/src/commonMain/kotlin/app/server/ClientConnection.kt"),
    )
    val root = rootDir
    return tasks.register("checkProtocolThrows") {
        group = GATE_GROUP
        description = "Fails if inbound protocol code throws anything but SerializationException."
        inputs.files(sources)
        doLast {
            val banned = Regex("""(^|[^\w."])(error|check|checkNotNull|require|requireNotNull)\s*\(""")
            val offenders = sources.kotlinFiles().flatMap { f ->
                f.readLines().withIndex()
                    .filter { (_, line) ->
                        val code = line.substringBefore("//").trim()
                        // A declaration named error() is our own WireMessage builder.
                        !code.startsWith("*") && !code.contains("fun error(") && banned.containsMatchIn(code)
                    }
                    .map { (i, line) -> "${f.relativeTo(root).path}:${i + 1}: ${line.trim()}" }
            }
            if (offenders.isNotEmpty()) {
                throw GradleException(
                    "Inbound protocol code must throw SerializationException, nothing else.\n" +
                        "These throw IllegalStateException or IllegalArgumentException instead,\n" +
                        "which the skip-a-poisoned-line catch does not cover:\n" +
                        offenders.joinToString("\n") { "  $it" }
                )
            }
        }
    }
}

/**
 * String resources must be loadable. A duplicate key is a crash at first use rather than a build
 * error, and this app has shipped one.
 */
private fun Project.registerStringResourceGate(): TaskProvider<*> {
    val resourceRoot = file("shared/src/commonMain/composeResources")
    val root = rootDir
    return tasks.register("checkStringResources") {
        group = GATE_GROUP
        description = "Fails on a duplicate string or plural key in any locale."
        inputs.dir(resourceRoot)
        doLast {
            val problems = mutableListOf<String>()
            resourceRoot.listFiles().orEmpty().sortedBy { it.name }.forEach { dir ->
                val xml = File(dir, "strings.xml").takeIf { it.isFile } ?: return@forEach
                val text = xml.readText()
                val where = xml.relativeTo(root).path

                val names = Regex("""<string name="([^"]+)"""").findAll(text).map { it.groupValues[1] }.toList()
                names.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted().forEach {
                    problems += "$where: duplicate key '$it'"
                }

                val plurals = Regex("""<plurals name="([^"]+)"""").findAll(text).map { it.groupValues[1] }.toList()
                plurals.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted().forEach {
                    problems += "$where: duplicate plural '$it'"
                }
                (names.toSet() intersect plurals.toSet()).sorted().forEach {
                    problems += "$where: '$it' is both a string and a plural"
                }

                // Deliberately no apostrophe rule: these are Compose Resources, not Android
                // res/, and the copy pass that removed those escapes was correct.
            }
            if (problems.isNotEmpty()) {
                throw GradleException("String resources will not load:\n" + problems.joinToString("\n") { "  $it" })
            }
        }
    }
}

/**
 * Locale parity, reported rather than enforced by default.
 *
 * Translations arrive through Weblate on their own schedule, so a missing key must not stop a
 * build. What must stop a build is the other direction: a key that exists in a translation and
 * not in the source, which means the source key was renamed or deleted and the translation is
 * now dead weight. Run with `-PstrictLocales=true` to fail on missing keys as well.
 */
private fun Project.registerLocaleParityGate(): TaskProvider<*> {
    val resourceRoot = file("shared/src/commonMain/composeResources")
    val strict = providers.gradleProperty("strictLocales").orNull?.toBoolean() ?: false
    return tasks.register("checkLocaleParity") {
        group = GATE_GROUP
        description = "Reports untranslated keys and fails on keys that exist only in a translation."
        inputs.dir(resourceRoot)
        doLast {
            fun keysIn(dir: String): Set<String> {
                val xml = File(File(resourceRoot, dir), "strings.xml")
                if (!xml.isFile) return emptySet()
                val text = xml.readText()
                return (Regex("""<string name="([^"]+)"""").findAll(text) +
                    Regex("""<plurals name="([^"]+)"""").findAll(text))
                    .map { it.groupValues[1] }.toSet()
            }

            val source = keysIn("values-en")
            if (source.isEmpty()) throw GradleException("values-en/strings.xml has no keys; the source of truth is missing")

            val locales = resourceRoot.listFiles().orEmpty()
                .filter { it.isDirectory && it.name.startsWith("values-") && it.name != "values-en" }
                .map { it.name }.sorted()

            val orphans = mutableListOf<String>()
            logger.lifecycle("Locale parity against values-en (${source.size} keys):")
            for (locale in locales) {
                val keys = keysIn(locale)
                val missing = source - keys
                val extra = keys - source
                val percent = if (source.isEmpty()) 0 else (keys.count { it in source } * 100) / source.size
                logger.lifecycle("  $locale: $percent%, ${missing.size} missing, ${extra.size} not in the source")
                extra.sorted().forEach { orphans += "$locale: '$it' does not exist in values-en" }
            }

            if (orphans.isNotEmpty()) {
                throw GradleException(
                    "These translated keys have no source key, so nothing can ever show them.\n" +
                        "Either the source key was renamed or the translation is stale:\n" +
                        orphans.joinToString("\n") { "  $it" }
                )
            }
            if (strict) {
                val incomplete = locales.filter { (source - keysIn(it)).isNotEmpty() }
                if (incomplete.isNotEmpty()) {
                    throw GradleException("-PstrictLocales: incomplete locales: ${incomplete.joinToString()}")
                }
            }
        }
    }
}

/**
 * Resources nobody references. The last dead-resource sweep was done by hand and found 117 dead
 * string keys, a shadowed launcher icon set and three unused drawables. This makes it mechanical.
 */
private fun Project.registerDeadResourceGate(): TaskProvider<*> {
    val resourceRoot = file("shared/src/commonMain/composeResources")
    val codeRoots = listOf(
        file("shared/src/commonMain/kotlin"),
        file("shared/src/androidMain/kotlin"),
        file("shared/src/desktopMain/kotlin"),
        file("shared/src/iosMain/kotlin"),
        file("androidApp/src/main"),
        file("desktopApp/src/main"),
    )
    return tasks.register("checkDeadResources") {
        group = GATE_GROUP
        description = "Reports string and drawable resources nothing references."
        inputs.dir(resourceRoot)
        inputs.files(codeRoots.filter { it.exists() })
        doLast {
            val sourceXml = File(File(resourceRoot, "values-en"), "strings.xml")
            if (!sourceXml.isFile) return@doLast
            val declared = (Regex("""<string name="([^"]+)"""").findAll(sourceXml.readText()) +
                Regex("""<plurals name="([^"]+)"""").findAll(sourceXml.readText()))
                .map { it.groupValues[1] }.toSet()

            val code = buildString {
                codeRoots.filter { it.exists() }.forEach { r ->
                    r.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "xml") }
                        .forEach { append(it.readText()).append('\n') }
                }
            }

            val unused = declared.filter { !Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(code) }.sorted()
            /* KiteConfig reads these by path when it regenerates launcher assets; no Kotlin
             * ever names them, and deleting them would break kiteRewriteLogo. */
            val ownedByKiteConfig = setOf("synkplay_bg", "synkplay_fg")
            val drawables = File(resourceRoot, "drawable").listFiles().orEmpty()
                .filter { it.isFile }
                .map { it.nameWithoutExtension }
                .filter { it !in ownedByKiteConfig }
                .filter { !Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(code) }
                .sorted()

            if (unused.isEmpty() && drawables.isEmpty()) {
                logger.lifecycle("No dead resources.")
            } else {
                logger.warn("Dead resources (${unused.size} strings, ${drawables.size} drawables):")
                unused.forEach { logger.warn("  string $it") }
                drawables.forEach { logger.warn("  drawable $it") }
                logger.warn("Nothing references these. Delete them, or point something at them.")
            }
        }
    }
}

/**
 * A preference that declares a title, a summary and an icon is meant to be seen. If nothing
 * outside its own declaration ever names it, nobody can reach it, and nobody finds out until
 * someone asks where a setting went.
 *
 * Deliberately wider than the settings console: plenty of preferences surface through a
 * purpose-built panel instead (gestures on their card, chat colours in the palette, the hosted
 * server on its own panel). What this catches is a pref that reaches no screen at all.
 *
 * A scan, not a test: nothing enumerates the members of the Preferences object at runtime, and
 * Kotlin/Native has no reflection to do it with.
 */
private fun Project.registerSettingsReachabilityGate(): TaskProvider<*> {
    val prefsFile = file("shared/src/commonMain/kotlin/app/preferences/Preferences.kt")
    val settingsDir = file("shared/src/commonMain/kotlin/app/preferences/settings")
    val engineDirs = listOf(
        file("shared/src/commonMain/kotlin/app"),
        file("shared/src/androidMain/kotlin/app"),
        file("shared/src/iosMain/kotlin/app"),
        file("shared/src/desktopMain/kotlin/app"),
    ).filter { it.exists() }
    return tasks.register("checkSettingsReachable") {
        group = GATE_GROUP
        description = "Fails if a preference declares a title and no settings category shows it."
        inputs.file(prefsFile)
        inputs.files(settingsDir)
        inputs.files(engineDirs)
        doLast {
            // `val NAME = Pref(...) {` with a config block is a preference meant to be displayed.
            val displayable = Regex("""\n    val ([A-Z][A-Z0-9_]*)\s*(?::[^=\n]+)?=\s*Pref[^\n]*\{""")
                .findAll(prefsFile.readText())
                .map { it.groupValues[1] }
                .toSet()

            // Engines attach their own rows, so their files count as places a pref can surface.
            val consumers = buildString {
                (listOf(settingsDir) + engineDirs).forEach { dir ->
                    dir.walkTopDown()
                        .filter { it.isFile && it.extension == "kt" && it != prefsFile }
                        .forEach { append(it.readText()).append('\n') }
                }
            }

            val unreachable = displayable
                .filter { name -> !Regex("\\b" + Regex.escape(name) + "\\b").containsMatchIn(consumers) }
                .sorted()
            if (unreachable.isNotEmpty()) {
                throw GradleException(
                    "These preferences declare a title, summary and icon but no settings\n" +
                        "category lists them, so nobody can reach them:\n" +
                        unreachable.joinToString("\n") { "  Preferences." + it }
                )
            }
            logger.lifecycle("All " + displayable.size + " displayable preferences are reachable.")
        }
    }
}


private fun List<File>.kotlinFiles(): List<File> =
    flatMap { if (it.isDirectory) it.walkTopDown().toList() else listOf(it) }
        .filter { it.isFile && it.extension == "kt" }
