package app.home.components

import app.utils.Platform
import com.mikepenz.aboutlibraries.Libs

/** One row of the licences screen. [text] is the full licence text, or null when there is none. */
data class LicenceRow(
    val name: String,
    val version: String?,
    val licence: String,
    val url: String?,
    val text: String?,
)

/**
 * The target names that the licence list uses for [this] platform. The plugin reports no iOS
 * target, so iOS takes the libraries of the common code, which is what it links.
 */
private fun Platform.licenceTargets(): Set<String> = when (this) {
    Platform.Android -> setOf("android")
    Platform.Desktop -> setOf("desktop")
    Platform.Web -> setOf("wasmJs")
    Platform.IOS -> setOf("metadata")
}

/**
 * The artifacts whose native code the ExoPlayer-only build strips (see the jniLibs excludes in
 * androidApp/build.gradle.kts), so that build does not list them.
 */
private val fullBuildOnly = setOf(
    "io.github.yuroyami:libmpvkt",
    "io.github.yuroyami:libmpvkt-native",
    "io.github.yuroyami:libmpvkt-view",
    "io.github.yuroyami:kiteffmpeg",
    "io.github.yuroyami:kiteplayer-libass",
)

/**
 * The rows for the running build: the hand-written entries first, then every library of the
 * running target, by name. [json] is the file that the AboutLibraries plugin writes.
 */
fun licenceRows(json: String, platform: Platform, exoOnly: Boolean): List<LicenceRow> {
    val libs = Libs.Builder().withJson(json).build()
    val texts = libs.licenses.associate { it.hash to it.licenseContent }
    val handWritten = handWrittenAttributions(platform, exoOnly).map {
        LicenceRow(it.name, null, it.licence, it.url, it.licenceId?.let(texts::get))
    }
    val targets = platform.licenceTargets()
    val generated = libs.libraries
        .filter { lib -> lib.targets.any { it in targets } }
        .filter { lib -> !(exoOnly && lib.uniqueId in fullBuildOnly) }
        .sortedBy { it.name.lowercase() }
        .map { lib ->
            LicenceRow(
                name = lib.name,
                version = lib.artifactVersion,
                licence = lib.licenses.joinToString { it.name }.ifEmpty { "Not stated" },
                url = lib.website,
                text = lib.licenses.mapNotNull { it.licenseContent }.distinct().joinToString("\n\n").ifBlank { null },
            )
        }
    return handWritten + generated
}
