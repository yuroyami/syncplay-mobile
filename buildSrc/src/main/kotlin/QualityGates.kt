import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register
import java.io.File

/**
 * The build-time gates: checks for rules that this codebase relies on.
 *
 * Each gate covers a rule that broke once, or a standing decision that no tool checked. The
 * gates are plain file scans, not lint or detekt rules, because each one reads files that a
 * Kotlin analyser does not read: the locale XMLs, a resource tree, the settings registry.
 *
 * [registerQualityGates] registers them, and `:shared:check` depends on them.
 */

private const val GATE_GROUP = "verification"

/**
 * Gates always run. Each scan takes milliseconds, and declaring the source and resource trees as
 * inputs collides with the tasks that generate files into them.
 */
private fun org.gradle.api.Task.alwaysRun() {
    outputs.upToDateWhen { false }
}

/**
 * Registers every gate and a `qualityGates` task that runs them all, and makes `:shared:check`
 * depend on the gates. The root build script calls it, so the tasks belong to the root project.
 */
fun Project.registerQualityGates(androidVersionCode: String, versionName: String) {
    val gates = listOf(
        registerProtocolThrowsGate(),
        registerStringResourceGate(),
        registerLocaleParityGate(),
        registerStringArgumentGate(),
        registerDeadResourceGate(),
        registerSettingsReachabilityGate(),
        registerDestroyContractGate(),
        registerStoreMetadataGate(androidVersionCode, versionName),
    )
    tasks.register("qualityGates") {
        group = GATE_GROUP
        description = "Runs all build-time gates: protocol throws, string resources, locale parity, string arguments, dead resources, settings reachability, destroy contract, store metadata."
        dependsOn(gates)
    }
    gradle.projectsEvaluated {
        project(":shared").tasks.findByName("check")?.dependsOn(gates)
    }
}

// ---------------------------------------------------------------------------------------------

/**
 * Inbound protocol code must throw SerializationException and nothing else. That is the only
 * type that the catch which skips a malformed line covers. An `error()`, `check()` or
 * `require()` there turns one malformed line into a dropped connection.
 *
 * This is a file scan, not detekt's ForbiddenMethodCall. That rule needs type resolution, which
 * the plain detekt task does not have, so as a detekt rule it never fires.
 */
private fun Project.registerProtocolThrowsGate(): TaskProvider<*> {
    // The protocol folder of every main source set, read from disk, so a transport in a new source
    // set is checked without an edit here (Netty lives in jvmShared, Ktor sockets in nonWebMain).
    val sources = file("shared/src").listFiles().orEmpty()
        .filter { it.name.endsWith("Main") || it.name == "jvmShared" }
        .sortedBy { it.name }
        .map { File(it, "kotlin/app/protocol") }
        .plus(file("shared/src/commonMain/kotlin/app/server/ClientConnection.kt"))
        .filter { it.exists() }
    val root = rootDir
    return tasks.register("checkProtocolThrows") {
        group = GATE_GROUP
        description = "Fails if inbound protocol code throws anything but SerializationException."
        alwaysRun()
        doLast {
            val banned = Regex("""(^|[^\w."])(error|check|checkNotNull|require|requireNotNull)\s*\(""")
            val offenders = sources.kotlinFiles().flatMap { f ->
                f.readLines().withIndex()
                    .filter { (_, line) ->
                        val code = line.substringBefore("//").trim()
                        // A declaration named error() is the WireMessage builder, not a throw.
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
 * String resources must load, and each key must have one value. The string files are read with
 * an XML parser ([StringResources]), so a key written as `name ="key"` is seen like any other.
 */
private fun Project.registerStringResourceGate(): TaskProvider<*> {
    val resourceRoot = file("shared/src/commonMain/composeResources")
    val root = rootDir
    return tasks.register("checkStringResources") {
        group = GATE_GROUP
        description = "Fails on a duplicate string or plural key in any locale."
        alwaysRun()
        doLast {
            val problems = mutableListOf<String>()
            resourceRoot.listFiles().orEmpty().sortedBy { it.name }.forEach { dir ->
                val xml = File(dir, "strings.xml").takeIf { it.isFile } ?: return@forEach
                val entries = StringResources.read(xml)
                val where = xml.relativeTo(root).path

                val names = entries.filter { it.kind == ResourceKind.STRING }.map { it.name }
                names.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted().forEach {
                    problems += "$where: duplicate key '$it'"
                }

                val plurals = entries.filter { it.kind == ResourceKind.PLURALS }.map { it.name }
                plurals.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted().forEach {
                    problems += "$where: duplicate plural '$it'"
                }
                (names.toSet() intersect plurals.toSet()).sorted().forEach {
                    problems += "$where: '$it' is both a string and a plural"
                }

                // No apostrophe rule, on purpose: these are Compose Resources, not Android res/,
                // so an apostrophe needs no escape.
            }
            if (problems.isNotEmpty()) {
                throw GradleException("String resources will not load:\n" + problems.joinToString("\n") { "  $it" })
            }
        }
    }
}

/**
 * Locale parity: by default the gate reports missing translations and does not fail on them.
 *
 * Translations arrive through Weblate on their own schedule, so a missing key must not stop a
 * build. The other direction must stop a build: a key that exists in a translation but not in
 * the source. Then the source key was renamed or deleted, and the translation is unused. Run with
 * `-PstrictLocales=true` to fail on missing keys as well.
 */
private fun Project.registerLocaleParityGate(): TaskProvider<*> {
    val resourceRoot = file("shared/src/commonMain/composeResources")
    val strict = providers.gradleProperty("strictLocales").orNull?.toBoolean() ?: false
    return tasks.register("checkLocaleParity") {
        group = GATE_GROUP
        description = "Reports untranslated keys and fails on keys that exist only in a translation."
        alwaysRun()
        doLast {
            fun keysIn(dir: String): Set<String> {
                val xml = File(File(resourceRoot, dir), "strings.xml")
                if (!xml.isFile) return emptySet()
                return StringResources.keys(xml).toSet()
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
 * Placeholders must match across languages.
 *
 * The string generator (Lyricist) strips the `%1$` position markers and fills the rest from left
 * to right. So a translation that reorders its placeholders puts the room name where the password
 * goes, and nothing reports it at runtime. Repeats fail for the same reason: `%1$s` twice becomes
 * two separate arguments.
 */
private fun Project.registerStringArgumentGate(): TaskProvider<*> {
    val resourceRoot = file("shared/src/commonMain/composeResources")
    return tasks.register("checkStringArguments") {
        group = GATE_GROUP
        description = "Fails when a translation's placeholders differ in order or number from English."
        alwaysRun()
        doLast {
            val marker = Regex("""%(\d+)\$""")

            fun argsIn(dir: String): Map<String, List<Int>> {
                val xml = File(File(resourceRoot, dir), "strings.xml")
                if (!xml.isFile) return emptyMap()
                return StringResources.read(xml).filter { it.kind == ResourceKind.STRING }.associate { e ->
                    e.name to marker.findAll(e.text).map { it.groupValues[1].toInt() }.toList()
                }
            }

            val problems = mutableListOf<String>()
            val source = argsIn("values-en")
            source.forEach { (key, args) ->
                if (args.size != args.distinct().size) {
                    problems += "values-en: '$key' uses a placeholder more than once; give it its own number"
                }
            }

            resourceRoot.listFiles().orEmpty()
                .filter { it.isDirectory && it.name.startsWith("values-") && it.name != "values-en" }
                .map { it.name }.sorted()
                .forEach { locale ->
                    argsIn(locale).forEach { (key, args) ->
                        val expected = source[key] ?: return@forEach
                        if (args != expected) {
                            problems += "$locale: '$key' has $args, values-en has $expected"
                        }
                    }
                }

            if (problems.isNotEmpty()) {
                throw GradleException(
                    "Placeholders do not match the source. Filled left to right, these would\n" +
                        "print the wrong value or none at all:\n" +
                        problems.joinToString("\n") { "  $it" }
                )
            }
        }
    }
}

/**
 * Reports string keys and drawables that nothing references. It warns only; it does not fail
 * the build.
 */
private fun Project.registerDeadResourceGate(): TaskProvider<*> {
    val resourceRoot = file("shared/src/commonMain/composeResources")
    val codeRoots = listOf(
        file("shared/src/commonMain/kotlin"),
        file("shared/src/nonWebMain/kotlin"),
        file("shared/src/androidMain/kotlin"),
        file("shared/src/desktopMain/kotlin"),
        file("shared/src/iosMain/kotlin"),
        file("shared/src/wasmJsMain/kotlin"),
        file("androidApp/src/main"),
        file("desktopApp/src/main"),
        file("webApp/src/wasmJsMain"),
    ).filter { it.exists() }
    return tasks.register("checkDeadResources") {
        group = GATE_GROUP
        description = "Reports string and drawable resources nothing references."
        alwaysRun()
        doLast {
            val sourceXml = File(File(resourceRoot, "values-en"), "strings.xml")
            if (!sourceXml.isFile) return@doLast
            val declared = StringResources.keys(sourceXml).toSet()

            val code = buildString {
                codeRoots.filter { it.exists() }.forEach { r ->
                    r.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "xml") }
                        .forEach { append(it.readText()).append('\n') }
                }
            }

            /* Strings are read through Lyricist, which renames every key: connect_username
             * becomes connectUsername. A key counts as used under either spelling. */
            val unused = declared.filter { key ->
                listOf(key, camelCased(key))
                    .none { Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(code) }
            }.sorted()
            /* KiteConfig reads these by path when it regenerates launcher assets; no Kotlin
             * ever names them, and deleting them would break kiteApply. */
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

/** The name Lyricist gives a string key: `connect_username` becomes `connectUsername`. */
internal fun camelCased(key: String): String {
    val parts = key.split("_")
    return parts.first().lowercase() +
        parts.drop(1).joinToString("") { part -> part.replaceFirstChar { it.uppercaseChar() } }
}

/**
 * A preference that declares a title, a summary and an icon is meant to be shown. If no code
 * outside its own declaration names it, no screen can show it, and nobody notices until someone
 * asks where a setting went.
 *
 * The scan covers the whole app, not only the settings screen, on purpose. Many preferences
 * appear on their own panel instead (gestures on their card, chat colours in the palette, the
 * hosted server on its own panel). The gate catches a preference that reaches no screen at all.
 *
 * This is a file scan, not a test: nothing lists the members of the Preferences object at
 * runtime, and Kotlin/Native has no reflection for it.
 */
private fun Project.registerSettingsReachabilityGate(): TaskProvider<*> {
    val prefsFile = file("shared/src/commonMain/kotlin/app/preferences/Preferences.kt")
    val settingsDir = file("shared/src/commonMain/kotlin/app/preferences/settings")
    val engineDirs = listOf(
        file("shared/src/commonMain/kotlin/app"),
        file("shared/src/nonWebMain/kotlin/app"),
        file("shared/src/androidMain/kotlin/app"),
        file("shared/src/iosMain/kotlin/app"),
        file("shared/src/desktopMain/kotlin/app"),
        file("shared/src/wasmJsMain/kotlin/app"),
    ).filter { it.exists() }
    return tasks.register("checkSettingsReachable") {
        group = GATE_GROUP
        description = "Fails if a preference declares a title and no settings category shows it."
        alwaysRun()
        doLast {
            // `val NAME = Pref(...) {` with a config block is a preference meant to be displayed.
            val displayable = Regex("""\n    val ([A-Z][A-Z0-9_]*)\s*(?::[^=\n]+)?=\s*Pref[^\n]*\{""")
                .findAll(prefsFile.readText())
                .map { it.groupValues[1] }
                .toSet()

            // Engines attach their own settings rows, so engine files count as places to show one.
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


/**
 * Checks the engine destroy contract. An engine is one of the video players the app can drive.
 *
 * Every `destroy()` must set `isInitialized = false` first, then cancel `playerSupervisorJob`,
 * and only then release the native engine. The gate checks the first two steps and their order.
 * The order matters: a position tracker that outlives teardown gets past its own guard and calls
 * into a released engine. On every engine that leaks the whole RoomViewmodel graph, and a call
 * into a released native handle can abort the process.
 *
 * This is a file scan, because most engines are iOS or Android actuals that no JVM test can
 * construct, and an edit can easily reverse this order.
 */
private fun Project.registerDestroyContractGate(): TaskProvider<*> {
    // Every source set that can hold an engine. Keep nonWebMain (KitePlayer) and wasmJsMain (the
    // browser engine) in the list, or the gate silently stops checking those engines.
    val engineRoots = listOf(
        file("shared/src/commonMain/kotlin/app/player"),
        file("shared/src/nonWebMain/kotlin/app/player"),
        file("shared/src/androidMain/kotlin/app/player"),
        file("shared/src/iosMain/kotlin/app/player"),
        file("shared/src/desktopMain/kotlin/app/player"),
        file("shared/src/wasmJsMain/kotlin/app/player"),
    ).filter { it.exists() }
    val root = rootDir
    return tasks.register("checkDestroyContract") {
        group = GATE_GROUP
        description = "Fails if a player engine's destroy() breaks the guard-then-cancel-then-release order."
        alwaysRun()
        doLast {
            val problems = mutableListOf<String>()
            var checked = 0
            engineRoots.forEach { dir ->
                dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
                    val lines = f.readLines()
                    lines.forEachIndexed { i, line ->
                        if (!line.contains("override suspend fun destroy()")) return@forEachIndexed
                        checked++
                        val where = f.relativeTo(root).path + ":" + (i + 1)
                        // Read to the end of the function: the first line that has the same
                        // indent as the declaration and closes a brace.
                        val indent = line.takeWhile { it == ' ' }
                        val body = lines.drop(i + 1)
                            .takeWhile { it.trimEnd() != "$indent}" }
                            .map { it.substringBefore("//") }
                        val guard = body.indexOfFirst { it.contains("isInitialized = false") }
                        val cancel = body.indexOfFirst { it.contains("playerSupervisorJob.cancel()") }
                        when {
                            guard < 0 -> problems += "$where: never sets isInitialized = false"
                            cancel < 0 -> problems += "$where: never cancels playerSupervisorJob"
                            guard > cancel -> problems +=
                                "$where: cancels playerSupervisorJob before dropping the isInitialized guard"
                        }
                    }
                }
            }
            if (problems.isNotEmpty()) {
                throw GradleException(
                    "A player engine broke the destroy contract. It must flip the guard, then\n" +
                        "cancel the supervisor, then release the engine:\n" +
                        problems.joinToString("\n") { "  " + it }
                )
            }
            logger.lifecycle("All " + checked + " engine destroy() bodies follow the contract.")
        }
    }
}

/**
 * The stores enforce hard length limits on store text at upload, not at build time. So a text
 * that is too long fails only when a release is already half-published.
 *
 * The gate also checks the release notes of the version being built. The Play note is a summary
 * written by hand, so it must exist, fit the Play limit and not be a placeholder such as
 * "Maintenance update.". CHANGELOG.md must have a section for the version. The release workflow
 * runs the gates before any build, so a version without release notes does not get released.
 */
private fun Project.registerStoreMetadataGate(versionCode: String, versionName: String): TaskProvider<*> {
    val metadata = file("fastlane/metadata/android/en-US")
    val changelogMd = file("CHANGELOG.md")
    return tasks.register("checkStoreMetadata") {
        group = GATE_GROUP
        description = "Fails if Play store copy is over the limit or the version has no changelog."
        alwaysRun()
        doLast {
            val problems = mutableListOf<String>()
            fun limit(path: String, max: Int, what: String) {
                val f = File(metadata, path)
                if (!f.isFile) {
                    problems += "$path is missing ($what)"
                    return
                }
                val n = f.readText().trim().length
                if (n > max) problems += "$path is $n characters; Play allows $max ($what)"
            }
            limit("short_description.txt", 80, "the one-line pitch")
            limit("full_description.txt", 4000, "the store listing body")

            val changelog = File(metadata, "changelogs/$versionCode.txt")
            if (!changelog.isFile) {
                problems += "changelogs/$versionCode.txt is missing; this version has no release notes"
            } else {
                val text = changelog.readText().trim()
                if (text.length > 500) problems += "changelogs/$versionCode.txt is ${text.length} characters; Play allows 500"
                if (text.length < MIN_PLAY_NOTE || PLACEHOLDER_NOTE.matches(text)) {
                    problems += "changelogs/$versionCode.txt is a placeholder (\"$text\"); summarize this version's CHANGELOG.md section"
                }
            }
            if (!changelogMd.isFile || changelogMd.readLines().none { it.trim() == "## $versionName" }) {
                problems += "CHANGELOG.md has no \"## $versionName\" section"
            }

            if (problems.isNotEmpty()) {
                throw GradleException(
                    "Store metadata will be rejected on upload:\n" +
                        problems.joinToString("\n") { "  " + it }
                )
            }
            logger.lifecycle("Store metadata fits, and " + versionCode + " has release notes.")
        }
    }
}

/** A Play note shorter than this says nothing about the release. */
private const val MIN_PLAY_NOTE = 40

/** Generic lines that stand in for release notes. */
private val PLACEHOLDER_NOTE = Regex(
    """(?i)(maintenance update|bug fixes( and (performance )?improvements)?|minor (fixes|improvements)|various fixes|improvements|todo|tbd|placeholder)\.?""",
)

private fun List<File>.kotlinFiles(): List<File> =
    flatMap { if (it.isDirectory) it.walkTopDown().toList() else listOf(it) }
        .filter { it.isFile && it.extension == "kt" }
