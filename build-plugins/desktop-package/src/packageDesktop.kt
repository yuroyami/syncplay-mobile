package io.github.yuroyami.synkplay.buildplugins.desktoppackage

import org.jetbrains.amper.plugins.Classpath
import org.jetbrains.amper.plugins.CompilationArtifact
import org.jetbrains.amper.plugins.ExecutionAvoidance
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.io.path.readLines

private enum class Host(val format: String, val id: String) { Mac("dmg", "macos"), Windows("msi", "windows"), Linux("deb", "linux") }

private val host: Host = System.getProperty("os.name").lowercase().let {
    when {
        "mac" in it -> Host.Mac
        "win" in it -> Host.Windows
        else -> Host.Linux
    }
}

/** The architecture name in Skiko's native file names: `arm64` or `x64`. */
private val arch: String = when (System.getProperty("os.arch").lowercase()) {
    "aarch64", "arm64" -> "arm64"
    else -> "x64"
}

/** The Compose Gradle plugin passes this option to every desktop app. It sets the Swing look and the macOS menu bar. */
private const val SWING_GLOBALS = "-Dcompose.application.configure.swing.globals=true"

/**
 * Builds the installer of this host into [outputDir]. jpackage comes from the JDK that the
 * toolchain provisioned for [jdkVersion], so the bundled runtime has the same major version as the
 * app's bytecode. The runtime holds every JDK module, like `includeAllModules` of the Compose Gradle
 * plugin: Netty, JNA, OkHttp and DataStore each use other jdk.* modules.
 *
 * Like the Compose Gradle plugin, the task keeps only this host's Skiko library and points
 * `skiko.library.path` at it, so the app loads Skiko from the package instead of unpacking it.
 */
@OptIn(ExperimentalPathApi::class)
@TaskAction(ExecutionAvoidance.Disabled)
fun packageDesktop(
    @Input settings: DesktopPackageSettings,
    mainClass: String?,
    jdkVersion: Int,
    @Input jar: CompilationArtifact,
    @Input runtimeClasspath: Classpath,
    @Output workDir: Path,
    @Output outputDir: Path,
) {
    val jdk = findJdk(jdkVersion)
    val jpackage = jdk.resolve(if (host == Host.Windows) "bin/jpackage.exe" else "bin/jpackage")
    workDir.deleteRecursively()
    outputDir.deleteRecursively()
    outputDir.createDirectories()

    // jpackage wants one folder with every jar of the app.
    val input = workDir.resolve("input").createDirectories()
    val jars = (listOf(jar.artifact) + runtimeClasspath.resolvedFiles).distinct().filter { it.name.endsWith(".jar") }
    val names = mutableSetOf<String>()
    var skikoLibraries = 0
    for (file in jars) {
        var name = file.name
        var index = 1
        // Two artifacts can have the same file name (for example shared-jvm.jar from two groups).
        while (!names.add(name)) name = "${index++}-${file.name}"
        if (file.name.startsWith("skiko-") && "-awt-runtime" in file.name) {
            skikoLibraries += splitSkikoRuntime(file, input.resolve(name), input)
        } else if (carriesForeignNatives(file)) {
            copyWithHostNatives(file, input.resolve(name))
        } else {
            file.copyTo(input.resolve(name))
        }
    }
    check(skikoLibraries > 0 || jars.none { it.name.startsWith("skiko-") && "-awt-runtime" in it.name }) {
        "The Skiko runtime jar has no library for ${host.id}-$arch."
    }

    val icons = writeIcons(settings.logoForeground, settings.logoBackground, settings.logoPercent, workDir.resolve("icons").createDirectories())
    val modules = capture(listOf(jdk.resolve(if (host == Host.Windows) "bin/java.exe" else "bin/java").absolutePathString(), "--list-modules"))
        .map { it.substringBefore('@').trim() }.filter { it.isNotEmpty() }
    val version = if (host == Host.Mac && settings.packageVersion.startsWith("0.")) {
        "1." + settings.packageVersion.removePrefix("0.")
    } else {
        settings.packageVersion
    }

    val common = buildList {
        add("--name"); add(settings.packageName)
        add("--app-version"); add(version)
        settings.vendor?.let { add("--vendor"); add(it) }
        settings.description?.let { add("--description"); add(it) }
    }
    val javaOptions = buildList {
        add(SWING_GLOBALS)
        addAll(settings.jvmArgs)
        // jpackage's launcher replaces $APPDIR with the folder that holds the jars.
        if (skikoLibraries > 0) add("-Dskiko.library.path=\$APPDIR")
        if (host == Host.Mac) add("-Xdock:name=${settings.packageName}")
    }
    val image = buildList {
        add(jpackage.absolutePathString())
        addAll(common)
        add("--input"); add(input.absolutePathString())
        add("--main-jar"); add(jar.artifact.name)
        add("--main-class"); add(checkNotNull(mainClass) { "Set settings.jvm.mainClass in the module.yaml of the desktop app." })
        add("--add-modules"); add(modules.joinToString(","))
        javaOptions.forEach { add("--java-options"); add(it) }
        val icon = when (host) {
            Host.Mac -> icons.mac
            Host.Windows -> icons.windows
            Host.Linux -> icons.linux
        }
        add("--icon"); add(icon.absolutePathString())
        if (host == Host.Mac) settings.macBundleId?.let { add("--mac-package-identifier"); add(it) }
    }
    val installer = buildList {
        add("--type"); add(host.format)
        add("--dest"); add(outputDir.absolutePathString())
        if (host == Host.Windows) {
            // The Compose Gradle plugin lets the user choose the installation folder.
            add("--win-dir-chooser")
            if (settings.windowsPerUserInstall) add("--win-per-user-install")
            settings.windowsMenuGroup?.let { add("--win-menu-group"); add(it) }
            settings.windowsUpgradeUuid?.let { add("--win-upgrade-uuid"); add(it) }
        }
    }

    if (host != Host.Mac) {
        run(image + installer)
        println("Installer: ${outputDir.listDirectoryEntries().joinToString()}")
        return
    }

    val images = workDir.resolve("image")
    run(image + listOf("--type", "app-image", "--dest", images.absolutePathString()))
    val app = images.resolve("${settings.packageName}.app")
    val plist = app.resolve("Contents/Info.plist").absolutePathString()
    // The Compose Gradle plugin writes this key, so the app may run on the integrated GPU.
    run(listOf("/usr/bin/plutil", "-replace", "NSSupportsAutomaticGraphicsSwitching", "-string", "true", plist))
    // jpackage writes macOS 10.11 and a microphone text. The Compose Gradle plugin wrote 10.13 and no
    // microphone text, and the app does not use the microphone.
    // FFmpeg in KiteFFmpeg is built for macOS 12.0, so an older macOS cannot load the decoder.
    run(listOf("/usr/bin/plutil", "-replace", "LSMinimumSystemVersion", "-string", "12.0", plist))
    // jpackage refuses a version that starts with 0, so the build passes 1.x. The version that the
    // user sees in Finder and in the About window is the real one.
    run(listOf("/usr/bin/plutil", "-replace", "CFBundleShortVersionString", "-string", settings.packageVersion, plist))
    run(listOf("/usr/bin/plutil", "-remove", "NSMicrophoneUsageDescription", plist))
    val identity = System.getenv("SYNKPLAY_MAC_SIGN_IDENTITY")?.takeIf { it.isNotBlank() }
    if (identity == null) {
        // An ad-hoc signature. macOS runs it, but Gatekeeper asks the user to allow it once.
        run(listOf("/usr/bin/codesign", "--force", "--deep", "--sign", "-", app.absolutePathString()))
    } else {
        signForNotarization(app, identity, workDir.resolve("entitlements.plist"))
    }
    // hdiutil builds the disk image, not jpackage: jpackage signs the app again ad hoc while it copies
    // it, which drops the signature above. The image holds the app and a link to Applications.
    val stage = workDir.resolve("dmg").createDirectories()
    run(listOf("/bin/cp", "-R", app.absolutePathString(), stage.absolutePathString()))
    java.nio.file.Files.createSymbolicLink(stage.resolve("Applications"), Path("/Applications"))
    val dmg = outputDir.resolve("${settings.packageName}-${settings.packageVersion}.dmg")
    run(
        listOf(
            "/usr/bin/hdiutil", "create", "-volname", settings.packageName, "-srcfolder", stage.absolutePathString(),
            "-ov", "-format", "UDZO", dmg.absolutePathString(),
        ),
    )
    if (identity != null) run(listOf("/usr/bin/codesign", "--force", "--timestamp", "--sign", identity, dmg.absolutePathString()))
    println("App image: $app")
    println("Installer: ${outputDir.listDirectoryEntries().joinToString()}")
}

/**
 * What the bundled JVM needs under the hardened runtime that notarization asks for: the JIT, and
 * native libraries that KiteFFmpeg and libass unpack at run time, which carry no Apple signature.
 */
private val ENTITLEMENTS = """
    <?xml version="1.0" encoding="UTF-8"?>
    <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
    <plist version="1.0">
    <dict>
        <key>com.apple.security.cs.allow-jit</key><true/>
        <key>com.apple.security.cs.allow-unsigned-executable-memory</key><true/>
        <key>com.apple.security.cs.disable-library-validation</key><true/>
    </dict>
    </plist>
""".trimIndent()

/**
 * Signs [app] with [identity] for notarization: every Mach-O file inside first, then the app, all
 * with the hardened runtime and a secure timestamp. Set SYNKPLAY_MAC_SIGN_IDENTITY to a
 * "Developer ID Application" identity of the login or CI keychain to use it.
 */
private fun signForNotarization(app: Path, identity: String, entitlements: Path) {
    entitlements.toFile().writeText(ENTITLEMENTS)
    fun sign(target: Path) = run(
        listOf(
            "/usr/bin/codesign", "--force", "--timestamp", "--options", "runtime",
            "--entitlements", entitlements.absolutePathString(), "--sign", identity, target.absolutePathString(),
        ),
    )
    val contents = app.resolve("Contents")
    java.nio.file.Files.walk(contents).use { files ->
        files.filter { java.nio.file.Files.isRegularFile(it) && isMachO(it) }.toList()
    }.sortedByDescending { it.nameCount }.forEach(::sign)
    // The bundled runtime is a bundle of its own. Sign it after its files, and the app last.
    contents.resolve("runtime").takeIf { it.exists() }?.let(::sign)
    sign(app)
}

/** Whether [file] starts with a Mach-O magic number, thin or universal. */
private fun isMachO(file: Path): Boolean {
    val head = ByteArray(4)
    val read = java.nio.file.Files.newInputStream(file).use { it.read(head) }
    if (read < 4) return false
    val magic = ((head[0].toInt() and 0xFF) shl 24) or ((head[1].toInt() and 0xFF) shl 16) or
        ((head[2].toInt() and 0xFF) shl 8) or (head[3].toInt() and 0xFF)
    return magic == 0xFEEDFACF.toInt() || magic == 0xCFFAEDFE.toInt() || magic == 0xCAFEBABE.toInt() || magic == 0xBEBAFECA.toInt()
}

/** A folder of native libraries for one system, as KiteFFmpeg and KitePlayer name them: `kiteffmpeg-native/macos-arm64/`. */
private val nativeFolder = Regex("^[^/]+-native/(linux|macos|windows)-(x64|arm64)/")

/** Whether [jar] carries native libraries for other systems than this host. */
private fun carriesForeignNatives(jar: Path): Boolean = ZipFile(jar.toFile()).use { zip ->
    zip.entries().asSequence().any { entry ->
        nativeFolder.find(entry.name)?.let { it.groupValues[1] != host.id || it.groupValues[2] != arch } == true
    }
}

/**
 * Copies [jar] to [target] without the native libraries of other systems. Each library loads only
 * the folder of the system it runs on, so the other folders are dead weight: about 70 MB per
 * installer for KiteFFmpeg and libass.
 */
private fun copyWithHostNatives(jar: Path, target: Path) {
    ZipFile(jar.toFile()).use { zip ->
        ZipOutputStream(target.outputStream()).use { out ->
            for (entry in zip.entries()) {
                val folder = nativeFolder.find(entry.name)
                if (folder != null && (folder.groupValues[1] != host.id || folder.groupValues[2] != arch)) continue
                out.putNextEntry(ZipEntry(entry.name))
                if (!entry.isDirectory) zip.getInputStream(entry).use { it.copyTo(out) }
                out.closeEntry()
            }
        }
    }
}

/**
 * Copies the Skiko runtime [jar] to [target] without its native libraries, and writes this host's
 * library and its `.sha256` file into [libraryDir]. Returns the number of libraries written. The
 * file name rules are the ones of the Compose Gradle plugin.
 */
private fun splitSkikoRuntime(jar: Path, target: Path, libraryDir: Path): Int {
    var written = 0
    ZipFile(jar.toFile()).use { zip ->
        ZipOutputStream(target.outputStream()).use { out ->
            for (entry in zip.entries()) {
                val fileName = entry.name.removeSuffix(".sha256").substringAfterLast('/')
                val native = fileName == "icudtl.dat" ||
                    (fileName.startsWith("libskiko") && (fileName.endsWith(".so") || fileName.endsWith(".dylib"))) ||
                    (fileName.startsWith("skiko") && fileName.endsWith(".dll"))
                if (!native) {
                    out.putNextEntry(ZipEntry(entry.name))
                    if (!entry.isDirectory) zip.getInputStream(entry).use { it.copyTo(out) }
                    out.closeEntry()
                    continue
                }
                val keep = if (fileName == "icudtl.dat") host == Host.Windows else "-${host.id}-$arch" in fileName
                if (!keep) continue
                libraryDir.resolve(entry.name.substringAfterLast('/')).outputStream().use { output ->
                    zip.getInputStream(entry).use { it.copyTo(output) }
                }
                if (!entry.name.endsWith(".sha256") && fileName != "icudtl.dat") written++
            }
        }
    }
    return written
}

/**
 * Finds a full JDK with jpackage for [major]: SYNKPLAY_PACKAGE_JAVA_HOME, then the JDKs that the
 * toolchain extracted into its cache, then JAVA_HOME.
 */
private fun findJdk(major: Int): Path {
    System.getenv("SYNKPLAY_PACKAGE_JAVA_HOME")?.let { return Path(it) }
    val home = System.getProperty("user.home")
    val caches = listOfNotNull(
        "$home/Library/Caches/JetBrains/Kotlin/extract.cache",
        "$home/.cache/JetBrains/Kotlin/extract.cache",
        System.getenv("LOCALAPPDATA")?.let { "$it/JetBrains/Kotlin/extract.cache" },
    ).map { Path(it) }.filter { it.isDirectory() }
    // Each archive is extracted to <cache>/<archive>.d/<jdk folder>, with the JDK home there or in Contents/Home.
    // Temurin ("OpenJDK" archives), the toolchain's default JDK, comes first, so the pick is the same on every run.
    val fromCache = caches.asSequence()
        .flatMap { cache ->
            cache.listDirectoryEntries().filter { it.isDirectory() }
                .sortedWith(compareBy<Path>({ "OpenJDK" !in it.name }, { it.name })).asSequence()
        }
        .flatMap { archive -> archive.listDirectoryEntries().filter { it.isDirectory() }.asSequence() }
        .flatMap { folder -> sequenceOf(folder, folder.resolve("Contents/Home")) }
        .firstOrNull { it.isJdk(major) }
    val fromJavaHome = System.getenv("JAVA_HOME")?.let { Path(it) }?.takeIf { it.isJdk(major) }
    return fromCache ?: fromJavaHome
        ?: error("No JDK $major with jpackage found. Build the app once with ./kotlin build, or set SYNKPLAY_PACKAGE_JAVA_HOME.")
}

private fun Path.isJdk(major: Int): Boolean {
    val release = resolve("release")
    if (!release.exists()) return false
    val version = release.readLines().firstOrNull { it.startsWith("JAVA_VERSION=") }?.substringAfter('=')?.trim('"') ?: return false
    val jpackage = resolve("bin/jpackage").takeIf { it.exists() } ?: resolve("bin/jpackage.exe")
    return version.substringBefore('.') == major.toString() && (jpackage.isExecutable() || jpackage.exists())
}

private fun run(command: List<String>) {
    println("> " + command.joinToString(" "))
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    process.inputStream.bufferedReader().forEachLine(::println)
    val exit = process.waitFor()
    check(exit == 0) { "Exit code $exit: ${command.first()}" }
}

private fun capture(command: List<String>): List<String> {
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    val lines = process.inputStream.bufferedReader().readLines()
    check(process.waitFor() == 0) { "Exit code ${process.exitValue()}: ${command.joinToString(" ")}\n${lines.joinToString("\n")}" }
    return lines
}
