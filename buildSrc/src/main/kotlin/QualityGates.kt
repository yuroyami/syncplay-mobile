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

/**
 * Gates always run. They are millisecond scans, and declaring the source and resource trees as
 * inputs collides with the tasks that generate into them.
 */
private fun org.gradle.api.Task.alwaysRun() {
    outputs.upToDateWhen { false }
}

/** Every gate, in one place, hung off the module that owns the code. */
fun Project.registerQualityGates() {
    val gates = listOf(
        registerProtocolThrowsGate(),
        registerStringResourceGate(),
        registerLocaleParityGate(),
        registerDeadResourceGate(),
        registerSettingsReachabilityGate(),
        registerDestroyContractGate(),
        registerStoreMetadataGate(),
    ) + registerDocVersionGates().take(1)
    tasks.register("qualityGates") {
        group = GATE_GROUP
        description = "Runs all eight build-time gates: protocol throws, string resources, locale parity, dead resources, settings reachability, destroy contract, store metadata, doc versions."
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
        alwaysRun()
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
        alwaysRun()
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
        alwaysRun()
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
        alwaysRun()
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
        alwaysRun()
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


/**
 * The engine destroy contract, enforced.
 *
 * Every `destroy()` must flip `isInitialized = false` first, then cancel `playerSupervisorJob`,
 * and only then release the native engine. The order is not cosmetic: mpv's handle is
 * process-global, so a position tracker that outlives teardown sails past its own guard and
 * trips a native CHECK that aborts the process. On the other engines the same mistake is
 * quieter, and only leaks the whole RoomViewmodel graph.
 *
 * A scan, because three of the five engines are iOS or Android actuals that no JVM test can
 * construct, and this is exactly the kind of ordering a well-meaning edit reverses.
 */
private fun Project.registerDestroyContractGate(): TaskProvider<*> {
    val engineRoots = listOf(
        file("shared/src/commonMain/kotlin/app/player"),
        file("shared/src/androidMain/kotlin/app/player"),
        file("shared/src/iosMain/kotlin/app/player"),
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
                        // Read to the end of the function: the first line indented exactly as
                        // the declaration and closing a brace.
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
 * Store copy has hard limits the stores enforce on upload, not at build time, so an overlong
 * file only fails at the point where a release is already half-published. The 0.24.0 What's New
 * was 1635 characters against a 500 limit and nothing had noticed.
 *
 * Also checks that a changelog exists for the version being built, since a release with no notes
 * is one of the two things standing between this app and a tagged version.
 */
private fun Project.registerStoreMetadataGate(): TaskProvider<*> {
    val metadata = file("fastlane/metadata/android/en-US")
    val versionCode = releaseVersionCode()
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
                val n = changelog.readText().trim().length
                if (n > 500) problems += "changelogs/$versionCode.txt is $n characters; Play allows 500"
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

/** KiteConfig's version code scheme: 1 | major(3) | minor(3) | patch(2) | rebuild(1). */
private fun Project.releaseVersionCode(): String {
    val version = Regex("""\n\s+version\s*=\s*"([^"]+)"""")
        .find(file("build.gradle.kts").readText())?.groupValues?.get(1)
        ?: error("no version in the kiteConfig block")
    val parts = version.split(".")
    return "1" + parts[0].padStart(3, '0') + parts[1].padStart(3, '0') +
        parts[2].padStart(2, '0') + "0"
}

/**
 * Keeps the version numbers written in the docs honest.
 *
 * The dependency table in CLAUDE.md and the toolchain line beside it were typed by hand, which
 * means they were right on the day they were written and drifting ever since. This reads
 * `gradle/libs.versions.toml` and rewrites the numbers between the markers.
 *
 * `checkDocVersions` fails when they have drifted; `updateDocVersions` fixes them.
 */
private fun Project.registerDocVersionGates(): List<TaskProvider<*>> {
    val catalog = file("gradle/libs.versions.toml")
    val properties = file("gradle.properties")
    val wrapper = file("gradle/wrapper/gradle-wrapper.properties")
    val doc = file("CLAUDE.md")

    fun rendered(): String {
        val versions = Regex("""^([A-Za-z0-9_-]+)\s*=\s*"([^"]+)"""", RegexOption.MULTILINE)
            .findAll(catalog.readText().substringAfter("[versions]").substringBefore("["))
            .associate { it.groupValues[1] to it.groupValues[2] }
        val props = Regex("""^([A-Za-z0-9_.-]+)\s*=\s*(.+)$""", RegexOption.MULTILINE)
            .findAll(properties.readText())
            .associate { it.groupValues[1].trim() to it.groupValues[2].trim() }

        fun v(name: String) = versions[name] ?: "?"
        fun p(name: String) = props[name] ?: "?"
        // Gradle's own version lives in the wrapper, not the catalog.
        val gradleVersion = Regex("""gradle-([0-9.]+)-bin\.zip""")
            .find(wrapper.readText())?.groupValues?.get(1) ?: "?"

        return buildString {
            appendLine("Kotlin " + v("kotlin") + ", AGP " + v("agp") + ", Compose Multiplatform " +
                v("compose-multiplatform") + ", Gradle " + gradleVersion + ", NDK " + p("android.ndkVersion") + ".")
            appendLine()
            appendLine("| Library | Version |")
            appendLine("|---|---|")
            listOf(
                "Ktor" to "ktor",
                "Netty" to "netty",
                "Conscrypt" to "conscrypt",
                "kotlinx-serialization-json" to "kSerialization",
                "Ktorfit" to "ktorfit",
                "DataStore" to "datastore",
                "kotlinx-coroutines" to "koroutines",
                "kotlinx-datetime" to "datetime",
                "Media3 / ExoPlayer" to "media3",
                "VLCKit (iOS)" to "libvlc-ios",
                "KitePlayer" to "kiteplayer",
                "Coil3" to "coil",
                "Haze" to "haze",
                "MaterialKolor" to "materialkolor",
                "Kermit" to "kermit",
                "detekt" to "detekt",
                "kover" to "kover",
                "skiko (force-pinned)" to "skiko",
            ).forEach { (label, key) ->
                if (versions.containsKey(key)) appendLine("| " + label + " | " + versions.getValue(key) + " |")
            }
        }.trim()
    }

    val begin = "<!-- versions:begin -->"
    val end = "<!-- versions:end -->"

    fun withBlock(text: String, body: String): String {
        val head = text.substringBefore(begin)
        val tail = text.substringAfter(end)
        return head + begin + "\n" + body + "\n" + end + tail
    }

    val update = tasks.register("updateDocVersions") {
        group = "documentation"
        description = "Rewrites the generated version table in CLAUDE.md from the version catalog."
        alwaysRun()
        doLast {
            val text = doc.readText()
            if (begin !in text) throw GradleException("CLAUDE.md has no $begin marker")
            doc.writeText(withBlock(text, rendered()))
            logger.lifecycle("CLAUDE.md version table rewritten.")
        }
    }

    val check = tasks.register("checkDocVersions") {
        group = GATE_GROUP
        description = "Fails when the version table in CLAUDE.md has drifted from the catalog."
        alwaysRun()
        doLast {
            val text = doc.readText()
            if (begin !in text) throw GradleException("CLAUDE.md has no $begin marker")
            if (text != withBlock(text, rendered())) {
                throw GradleException(
                    "The version table in CLAUDE.md no longer matches gradle/libs.versions.toml.\n" +
                        "Run ./gradlew updateDocVersions"
                )
            }
        }
    }
    return listOf(check, update)
}

private fun List<File>.kotlinFiles(): List<File> =
    flatMap { if (it.isDirectory) it.walkTopDown().toList() else listOf(it) }
        .filter { it.isFile && it.extension == "kt" }
