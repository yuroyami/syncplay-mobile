package io.github.yuroyami.synkplay.buildplugins.appconfig

import org.jetbrains.amper.plugins.ExecutionAvoidance
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** One place in one file that holds a version. [pattern] has the value in group 1. */
private class VersionSlot(val file: Path, val label: String, val pattern: Regex, val expected: String, val count: IntRange)

/**
 * The buildSettings blocks of the app target in project.pbxproj: the ones that set
 * PRODUCT_BUNDLE_IDENTIFIER. The project-level blocks do not hold the app version.
 */
private val buildSettingsBlock = Regex("""(?s)buildSettings = \{(.*?)\n\s*};""")

private fun slots(version: String, rebuild: Int, androidModule: Path, desktopModule: Path, xcodeProject: Path): List<VersionSlot> {
    val build = buildNumber(version, rebuild).toString()
    return listOf(
        VersionSlot(androidModule, "versionName", Regex("""(?m)^\s+versionName:\s*"?([^"\s#]+)"?"""), version, 1..1),
        VersionSlot(androidModule, "versionCode", Regex("""(?m)^\s+versionCode:\s*(\d+)"""), build, 1..1),
        VersionSlot(desktopModule, "packageVersion", Regex("""(?m)^\s+packageVersion:\s*"?([^"\s#]+)"?"""), version, 1..1),
        // The app target has a Debug and a Release configuration.
        VersionSlot(xcodeProject, "MARKETING_VERSION", Regex("""MARKETING_VERSION = "?([^";]+)"?;"""), version, 2..2),
        VersionSlot(xcodeProject, "CURRENT_PROJECT_VERSION", Regex("""CURRENT_PROJECT_VERSION = "?([^";]+)"?;"""), build, 2..2),
    )
}

/** The ranges of the app target's buildSettings blocks, or the whole text for a YAML file. */
private fun scopes(slot: VersionSlot, text: String): List<IntRange> =
    if (slot.file.fileName.toString() != "project.pbxproj") listOf(text.indices)
    else buildSettingsBlock.findAll(text).filter { "PRODUCT_BUNDLE_IDENTIFIER" in it.value }.map { it.range }.toList()

/** Every value of this slot in [text], with its range in [text]. */
private fun VersionSlot.values(text: String): List<Pair<String, IntRange>> {
    val found = scopes(this, text).flatMap { scope ->
        pattern.findAll(text, scope.first).takeWhile { it.range.last <= scope.last }.map { match ->
            val group = match.groups[1]!!
            group.value to group.range
        }
    }
    check(found.size in count) { "Expected $count `$label` entries in $file, found ${found.size}." }
    return found
}

/** Fails when a platform file holds another version or build number than shared/module.yaml. */
@TaskAction
fun checkAppVersion(
    version: String,
    rebuild: Int,
    // The files are a baseline: reading them must not make the sync command run first.
    @Input(inferTaskDependency = false) androidModule: Path,
    @Input(inferTaskDependency = false) desktopModule: Path,
    @Input(inferTaskDependency = false) xcodeProject: Path,
) {
    val wrong = slots(version, rebuild, androidModule, desktopModule, xcodeProject).flatMap { slot ->
        slot.values(slot.file.readText()).map { it.first }.filter { it != slot.expected }
            .map { "${slot.file.fileName} ${slot.label} is \"$it\", expected \"${slot.expected}\"" }
    }
    check(wrong.isEmpty()) {
        "The app version $version is not in every platform file:\n  " + wrong.joinToString("\n  ") +
            "\nRun `./kotlin do syncAppVersion` and commit the files."
    }
    println("App version $version (build ${buildNumber(version, rebuild)}) is the same in every platform file.")
}

/** Writes the app version and the build number into the Android, desktop and Xcode files. */
@TaskAction(ExecutionAvoidance.Disabled)
fun syncAppVersion(
    version: String,
    rebuild: Int,
    // The files are written, but they are not declared as outputs: a build task that reads one
    // would then run this command first.
    @Input(inferTaskDependency = false) androidModule: Path,
    @Input(inferTaskDependency = false) desktopModule: Path,
    @Input(inferTaskDependency = false) xcodeProject: Path,
) {
    slots(version, rebuild, androidModule, desktopModule, xcodeProject).groupBy { it.file }.forEach { (file, fileSlots) ->
        val original = file.readText()
        var text = original
        for (slot in fileSlots) {
            // Replace from the end, so the earlier ranges stay valid.
            for ((_, range) in slot.values(text).reversed()) {
                text = text.replaceRange(range, slot.expected)
            }
        }
        if (text != original) {
            file.writeText(text)
            println("Updated $file")
        }
    }
    println("App version $version (build ${buildNumber(version, rebuild)}) is now in every platform file.")
}

/**
 * Prints the release identity as KEY=VALUE lines for the release workflow. Read the version and
 * the build number from here instead of parsing files, so the version scheme exists in one place.
 */
@TaskAction(ExecutionAvoidance.Disabled)
fun printReleaseIdentity(
    version: String,
    rebuild: Int,
    @Input(inferTaskDependency = false) androidModule: Path,
    @Input(inferTaskDependency = false) xcodeProject: Path,
) {
    val build = buildNumber(version, rebuild)
    val applicationId = Regex("""(?m)^\s+applicationId:\s*"?([^"\s#]+)"?""").find(androidModule.readText())?.groupValues?.get(1)
        ?: error("No applicationId in $androidModule.")
    // The Xcode project owns the deployment target.
    val iosMinimum = Regex("""IPHONEOS_DEPLOYMENT_TARGET = ([0-9.]+);""").find(xcodeProject.readText())?.groupValues?.get(1)
        ?: error("No IPHONEOS_DEPLOYMENT_TARGET in $xcodeProject.")
    println("VERSION=$version")
    println("VERSION_CODE=$build")
    println("IOS_VERSION=$version")
    println("IOS_BUILD_NUMBER=$build")
    println("APPLICATION_ID=$applicationId")
    println("IOS_MIN_VERSION=$iosMinimum")
}
