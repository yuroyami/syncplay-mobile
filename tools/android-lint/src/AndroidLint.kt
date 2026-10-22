import com.android.tools.lint.Main
import java.io.File
import java.util.zip.ZipFile
import kotlin.system.exitProcess

/** The Android app module. */
private const val APP = "androidApp"

/** The source folders of the shared module that the Android app compiles. */
private val SHARED_ANDROID_SOURCES = listOf("shared/src", "shared/src@nonWeb", "shared/src@jvmShared", "shared/src@android")

/**
 * Runs Android Lint on the Android app.
 *
 * The toolchain has no lint task. This tool describes the app in a lint project file and runs lint
 * on it: the manifest and the resources of androidApp, the Kotlin and Java files of the shared code
 * that Android compiles, and the repository's lint.xml. The libraries come from the debug build of
 * the toolchain, so build the app first: `./kotlin build -m androidApp -p android`.
 *
 * Usage: `./kotlin run -m android-lint [-- lint options]`. Reports go to `build/android-lint/`.
 * Lint findings do not fail the run. Pass `--exitcode` to make an error finding fail it.
 */
fun main(args: Array<String>) {
    val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "project.yaml").isFile }
        ?: fail("Run this tool inside the Synkplay project: no project.yaml found.")
    val sdk = (System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT"))
        ?.let(::File)?.takeIf { it.isDirectory }
        ?: fail("Set ANDROID_HOME to the Android SDK.")
    val moduleDir = File(root, APP)
    val settings = File(moduleDir, "module.yaml").readText()

    // The toolchain builds Android through a generated Gradle project. Its settings file lists every
    // resolved library of the app as an absolute path.
    val build = "./kotlin build -m $APP -p android"
    val generated = File(root, "build/tasks/_${APP}_buildAndroidDebug/gradle-project/settings.gradle.kts")
    if (!generated.isFile) fail("No Android build of $APP found. Run `$build` first.")
    val libraries = Regex("\"(/[^\"]+\\.(?:jar|aar))\"")
        .findAll(generated.readText())
        .map { File(it.groupValues[1]) }
        // Skip the toolchain's own Gradle plugin jars and the archive of the app itself.
        .filter { "/extra/jars/" !in it.path && !it.path.contains("_${APP}_") && it.isFile }
        .distinctBy { it.absolutePath }
        .toList()

    val out = File(root, "build/android-lint").apply {
        deleteRecursively()
        mkdirs()
    }
    // Lint reads minSdk, targetSdk and the version only from the manifest, but the toolchain sets them
    // from the module settings. The copy adds them on the existing lines, so the line numbers stay the same.
    val manifest = File(moduleDir, "src/AndroidManifest.xml")
    val lintManifest = File(out, "AndroidManifest.xml")
    lintManifest.writeText(withVersionAndSdk(manifest.readText(), settings))

    val sources = (listOf("$APP/src") + SHARED_ANDROID_SOURCES).flatMap { sourceFiles(File(root, it)) }
    val resources = File(moduleDir, "res").walkTopDown().filter { it.isFile }.sortedBy { it.path }.toList()
    val project = buildString {
        appendLine("<project>")
        appendLine("""  <sdk dir="${sdk.xml()}"/>""")
        appendLine("""  <root dir="${root.xml()}"/>""")
        appendLine("""  <module name="$APP" android="true" library="false" compile-sdk-version="${setting(settings, "apiLevel")}">""")
        appendLine("""    <manifest file="${lintManifest.xml()}"/>""")
        // Lint skips a <src> or <resource> folder without a warning, so the project file lists each file.
        resources.forEach { appendLine("""    <resource file="${it.xml()}"/>""") }
        sources.forEach { appendLine("""    <src file="${it.xml()}"/>""") }
        libraries.forEachIndexed { index, library ->
            if (library.extension == "aar") {
                // Lint reads the classes and resources of a library from its unpacked AAR.
                val dir = File(out, "aars/$index-${library.nameWithoutExtension}").apply { mkdirs() }
                unzip(library, dir)
                appendLine("""    <aar file="${library.xml()}" extracted="${dir.xml()}"/>""")
            } else {
                appendLine("""    <classpath jar="${library.xml()}"/>""")
            }
        }
        appendLine("  </module>")
        appendLine("</project>")
    }
    val projectFile = File(out, "project.xml").apply { writeText(project) }
    println("Linting $APP: ${sources.size} source files, ${resources.size} resource files, ${libraries.size} libraries")

    System.setProperty("java.awt.headless", "true")
    val lintArgs = arrayOf(
        "--project", projectFile.path,
        "--sdk-home", sdk.path,
        "--config", File(root, "lint.xml").path,
        "--html", File(out, "report.html").path,
        "--xml", File(out, "report.xml").path,
        "--text", File(out, "report.txt").path,
    ) + args
    val code = Main().run(lintArgs)
    // Point the findings in the manifest copy back to the real manifest.
    for (report in listOf("report.txt", "report.xml", "report.html").map { File(out, it) }.filter { it.isFile }) {
        report.writeText(
            report.readText()
                .replace(lintManifest.path, manifest.path)
                .replace(lintManifest.toRelativeString(root), manifest.toRelativeString(root)),
        )
    }
    File(out, "report.txt").takeIf { it.isFile }?.let { print(it.readText()) }
    println("Lint reports: ${File(out, "report.html")}")
    exitProcess(code)
}

/** The Kotlin and Java files under [dir]. */
private fun sourceFiles(dir: File): List<File> =
    dir.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "java") }.sortedBy { it.path }.toList()

/** The first value of the setting [name] in [settings], for example `minSdk: 26`. */
private fun setting(settings: String, name: String): String =
    Regex("""(?m)^\s+$name:\s*"?([^"\s#]+)""").find(settings)?.groupValues?.get(1) ?: fail("No $name in $APP/module.yaml.")

/** Adds `versionCode` and `versionName` to the `<manifest>` tag, and `<uses-sdk>` right after it. */
private fun withVersionAndSdk(manifest: String, settings: String): String {
    val tag = Regex("""<manifest\b[^>]*>""").find(manifest) ?: fail("No <manifest> tag in the app manifest.")
    val versioned = tag.value.removeSuffix(">") +
        """ android:versionCode="${setting(settings, "versionCode")}"""" +
        """ android:versionName="${setting(settings, "versionName")}">""" +
        """<uses-sdk android:minSdkVersion="${setting(settings, "minSdk")}"""" +
        """ android:targetSdkVersion="${setting(settings, "targetSdk")}"/>"""
    return manifest.replaceRange(tag.range, versioned)
}

private fun unzip(archive: File, into: File) = ZipFile(archive).use { zip ->
    for (entry in zip.entries()) {
        val target = File(into, entry.name).canonicalFile
        // Never write outside the target folder, whatever the entry names say.
        if (!target.path.startsWith(into.canonicalPath + File.separator)) continue
        if (entry.isDirectory) {
            target.mkdirs()
        } else {
            target.parentFile.mkdirs()
            zip.getInputStream(entry).use { input -> target.outputStream().use(input::copyTo) }
        }
    }
}

private fun File.xml() = path.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")

private fun fail(message: String): Nothing {
    System.err.println(message)
    exitProcess(2)
}
