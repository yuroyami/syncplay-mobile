import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.JavaExec
import java.io.File
import java.net.URI

/**
 * Desktop native bundling for :desktopApp, kept out of its build script:
 * fetchVlcNatives / fetchMpvNatives download-and-bundle libVLC/libmpv for the CURRENT
 * build OS into desktopApp/resources/<os>-<arch>/ (Compose packages that dir into the
 * app image; vlcj/MpvNativeLoader discover it at runtime). Linux is skipped on both —
 * the app falls back to system installs there. Both tasks are no-ops once the target
 * dir exists and only WARN on failure. Details: CLAUDE.md "Desktop (PC) Target".
 */
object DesktopNativesConfig {

    const val VLC_VERSION = "3.0.21"

    /** resources/<os>-<arch> for the current build machine; null = no bundling (Linux/unknown). */
    private fun composeResourceTargetDir(): String? {
        val os = System.getProperty("os.name").lowercase()
        val archProp = System.getProperty("os.arch").lowercase()
        val arch = if (archProp == "aarch64" || archProp == "arm64") "arm64" else "x64"
        return when {
            os.contains("mac") -> "macos-$arch"
            os.contains("windows") -> "windows-$arch"
            else -> null
        }
    }

    /* Project.exec is gone from task actions in Gradle 9 — plain ProcessBuilder instead. */
    private fun runCommand(vararg cmd: String) {
        val exit = ProcessBuilder(*cmd).inheritIO().start().waitFor()
        if (exit != 0) error("Command failed ($exit): ${cmd.joinToString(" ")}")
    }

    private fun capture(vararg cmd: String): String {
        val proc = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readText()
        check(proc.waitFor() == 0) { "Command failed: ${cmd.joinToString(" ")}\n$out" }
        return out
    }

    fun Project.registerDesktopNativeTasks() {
        val targetOsDir = composeResourceTargetDir()
        val resourcesDir = layout.projectDirectory.dir("resources").asFile

        tasks.register("fetchVlcNatives") {
            group = "syncplay"
            description = "Download libVLC $VLC_VERSION for the current OS into desktopApp/resources/ for bundling."
            outputs.upToDateWhen { targetOsDir == null || File(resourcesDir, "$targetOsDir/vlc").isDirectory }
            doLast { fetchVlc(targetOsDir, resourcesDir) }
        }

        tasks.register("fetchMpvNatives") {
            group = "syncplay"
            description = "Bundle libmpv (+ dependency closure on macOS) into desktopApp/resources/ for zero-install mpv."
            outputs.upToDateWhen { targetOsDir == null || File(resourcesDir, "$targetOsDir/mpv").isDirectory }
            doLast { fetchMpv(targetOsDir, resourcesDir) }
        }

        registerSmokeTest("vlcSmokeTest", "app.desktop.VlcSmokeTestKt", "fetchVlcNatives", "smokeUrl",
            "Verify the bundled libVLC natives load (no system VLC install needed).")
        registerSmokeTest("mpvSmokeTest", "app.desktop.MpvSmokeTestKt", "fetchMpvNatives", "mpvSmokeUrl",
            "Verify libmpv loads (bundled first, system fallback); optional media probe.")

        /* Bundle/run always attempt the fetches first (cheap no-ops once cached). */
        tasks.matching {
            it.name == "run" || it.name == "runDistributable" || it.name == "createDistributable" ||
                it.name.startsWith("package")
        }.configureEach {
            dependsOn("fetchVlcNatives", "fetchMpvNatives")
        }

        /* prepareAppResources copies incrementally, so a plugins.dat deleted from resources/ can
         * survive in its output with stale mtimes — libVLC then slow-rescans on every dev run. */
        tasks.matching { it.name == "prepareAppResources" }.configureEach {
            doLast {
                File(layout.buildDirectory.get().asFile, "compose/tmp/prepareAppResources/vlc/plugins/plugins.dat").delete()
            }
        }
    }

    private fun Project.registerSmokeTest(
        name: String, mainClassName: String, fetchTask: String, urlProperty: String, desc: String,
    ) {
        // Resolved on the PROJECT here — inside the task-config lambda below, `extensions`
        // would resolve to the task's own (empty) extension container.
        val mainSourceSet = extensions.getByType(JavaPluginExtension::class.java).sourceSets.named("main")
        tasks.register(name, JavaExec::class.java) {
            group = "syncplay"
            description = desc
            dependsOn(fetchTask)
            classpath = mainSourceSet.get().runtimeClasspath
            mainClass.set(mainClassName)
            composeResourceTargetDir()?.let { osDir ->
                systemProperty(
                    "compose.application.resources.dir",
                    layout.projectDirectory.dir("resources/$osDir").asFile.absolutePath
                )
            }
            (findProperty(urlProperty) as String?)?.let { args(it) }
        }
    }

    private fun Project.fetchVlc(targetOsDir: String?, resourcesDir: File) {
        if (targetOsDir == null) {
            logger.warn("fetchVlcNatives: no portable libVLC for this OS — skipping (Linux uses the system VLC via NativeDiscovery).")
            return
        }
        val vlcDir = File(resourcesDir, "$targetOsDir/vlc")
        if (vlcDir.isDirectory) {
            logger.lifecycle("fetchVlcNatives: ${vlcDir.relativeTo(projectDir)} already present — skipping.")
            return
        }
        val tmp = File(layout.buildDirectory.get().asFile, "vlc-fetch").apply { mkdirs() }

        runCatching {
            if (targetOsDir.startsWith("macos")) {
                val dmg = File(tmp, "vlc-$VLC_VERSION-universal.dmg")
                if (!dmg.exists()) {
                    val url = "https://get.videolan.org/vlc/$VLC_VERSION/macosx/vlc-$VLC_VERSION-universal.dmg"
                    logger.lifecycle("fetchVlcNatives: downloading $url ...")
                    URI(url).toURL().openStream().use { input -> dmg.outputStream().use { input.copyTo(it) } }
                }
                val mount = File(tmp, "mount").apply { mkdirs() }
                runCommand("hdiutil", "attach", dmg.absolutePath, "-mountpoint", mount.absolutePath, "-nobrowse", "-quiet")
                try {
                    val macos = File(mount, "VLC.app/Contents/MacOS")
                    vlcDir.mkdirs()
                    copy { from(File(macos, "lib")); into(File(vlcDir, "lib")) }
                    copy { from(File(macos, "plugins")); into(File(vlcDir, "plugins")) }
                } finally {
                    runCatching { runCommand("hdiutil", "detach", mount.absolutePath, "-quiet") }
                }
                // Copied plugins.dat keeps the ORIGINAL files' mtimes → libVLC "stale plugins
                // cache" spam + rescan on every start. Drop it; the silent rescan is expected.
                File(vlcDir, "plugins/plugins.dat").delete()
            } else {
                val zip = File(tmp, "vlc-$VLC_VERSION-win64.zip")
                if (!zip.exists()) {
                    val url = "https://get.videolan.org/vlc/$VLC_VERSION/win64/vlc-$VLC_VERSION-win64.zip"
                    logger.lifecycle("fetchVlcNatives: downloading $url ...")
                    URI(url).toURL().openStream().use { input -> zip.outputStream().use { input.copyTo(it) } }
                }
                val unpacked = File(tmp, "unpacked")
                copy { from(zipTree(zip)); into(unpacked) }
                val root = File(unpacked, "vlc-$VLC_VERSION")
                vlcDir.mkdirs()
                copy { from(root) { include("libvlc.dll", "libvlccore.dll") }; into(vlcDir) }
                copy { from(File(root, "plugins")); into(File(vlcDir, "plugins")) }
                File(vlcDir, "plugins/plugins.dat").delete() // see the macOS branch comment
            }
            logger.lifecycle("fetchVlcNatives: libVLC unpacked into ${vlcDir.relativeTo(projectDir)}")
        }.onFailure {
            logger.warn("fetchVlcNatives: FAILED (${it.message}). The app will fall back to a system-installed VLC at runtime.")
            vlcDir.deleteRecursively()
        }
    }

    private fun Project.fetchMpv(targetOsDir: String?, resourcesDir: File) {
        if (targetOsDir == null) {
            logger.warn("fetchMpvNatives: Linux uses the system libmpv — skipping.")
            return
        }
        val mpvDir = File(resourcesDir, "$targetOsDir/mpv")
        if (mpvDir.isDirectory) {
            logger.lifecycle("fetchMpvNatives: ${mpvDir.relativeTo(projectDir)} already present — skipping.")
            return
        }

        runCatching {
            if (targetOsDir.startsWith("macos")) bundleMacosMpv(mpvDir)
            else bundleWindowsMpv(mpvDir)
        }.onFailure {
            logger.warn("fetchMpvNatives: SKIPPED (${it.message}). mpv falls back to a system install at runtime.")
            mpvDir.deleteRecursively()
        }
    }

    /**
     * macOS: closure-copy the Homebrew libmpv dylib + its full dependency tree (otool -L,
     * recursive over absolute, @loader_path and @rpath refs), rewrite every install name to
     * @loader_path siblings, ad-hoc re-sign (mandatory on arm64 after any Mach-O edit).
     * Needs `brew install mpv` on the BUILD machine only.
     */
    private fun Project.bundleMacosMpv(mpvDir: File) {
        val brewLib = listOf("/opt/homebrew/lib/libmpv.dylib", "/usr/local/lib/libmpv.dylib")
            .map(::File).firstOrNull { it.exists() }
            ?: error("Homebrew libmpv not found — run `brew install mpv` on this build machine to enable mpv bundling.")

        fun isSystemDep(dep: String) = dep.startsWith("/usr/lib/") || dep.startsWith("/System/")

        fun rawDeps(lib: File): List<String> =
            capture("otool", "-L", lib.absolutePath).lines().drop(1)
                .mapNotNull { it.trim().substringBefore(" (").takeIf { p -> p.isNotBlank() } }
                .filter { !isSystemDep(it) }
                .filterNot { it.substringAfterLast('/') == lib.name } // self id line

        fun resolveDep(dep: String, srcDir: File): File? {
            val f = when {
                dep.startsWith("@loader_path/") -> File(srcDir, dep.removePrefix("@loader_path/"))
                dep.startsWith("@rpath/") -> {
                    val tail = dep.removePrefix("@rpath/")
                    listOf(File(srcDir, tail), File("/opt/homebrew/lib", tail), File("/usr/local/lib", tail))
                        .firstOrNull { it.exists() }
                }
                dep.startsWith("/") -> File(dep)
                else -> null
            }
            return f?.takeIf { it.exists() }?.canonicalFile
        }

        mpvDir.mkdirs()
        // BFS over the dependency closure; copy each dylib under its install-name basename.
        val copied = LinkedHashMap<String, File>() // basename -> bundled file
        val realOf = LinkedHashMap<String, File>() // basename -> original real file
        val queue = ArrayDeque<Pair<String, File>>()
        queue.add("libmpv.dylib" to brewLib.canonicalFile)
        while (queue.isNotEmpty()) {
            val (name, real) = queue.removeFirst()
            if (copied.containsKey(name)) continue
            val dst = File(mpvDir, name)
            real.copyTo(dst, overwrite = true)
            dst.setWritable(true)
            copied[name] = dst
            realOf[name] = real
            for (dep in rawDeps(real)) {
                val depName = dep.substringAfterLast('/')
                if (copied.containsKey(depName)) continue
                val resolved = resolveDep(dep, real.parentFile)
                if (resolved != null) queue.add(depName to resolved)
                else logger.warn("fetchMpvNatives: unresolved dependency $dep (from ${real.name})")
            }
        }
        for ((name, file) in copied) {
            capture("install_name_tool", "-id", "@loader_path/$name", file.absolutePath)
            for (dep in rawDeps(realOf[name]!!)) {
                val depName = dep.substringAfterLast('/')
                if (copied.containsKey(depName)) {
                    capture("install_name_tool", "-change", dep, "@loader_path/$depName", file.absolutePath)
                }
            }
            capture("codesign", "--force", "-s", "-", file.absolutePath)
        }
        logger.lifecycle("fetchMpvNatives: bundled ${copied.size} dylibs into ${mpvDir.relativeTo(projectDir)}")
    }

    /** Windows: upstream libmpv dev packages are .7z-only, so extraction needs 7z on PATH. */
    private fun Project.bundleWindowsMpv(mpvDir: File) {
        val sevenZip = listOf("7z", "7za").firstOrNull {
            runCatching { ProcessBuilder(it).start().destroy() }.isSuccess
        } ?: error("7z not found on PATH — install 7-Zip to enable mpv bundling on Windows.")
        val url = "https://downloads.sourceforge.net/project/mpv-player-windows/libmpv/mpv-dev-x86_64-v3-20241229-git-1f5a237.7z"
        val archive = File(layout.buildDirectory.get().asFile, "mpv-fetch/libmpv-dev.7z")
        archive.parentFile.mkdirs()
        if (!archive.exists()) {
            logger.lifecycle("fetchMpvNatives: downloading $url ...")
            URI(url).toURL().openStream().use { input -> archive.outputStream().use { input.copyTo(it) } }
        }
        mpvDir.mkdirs()
        capture(sevenZip, "e", archive.absolutePath, "-o${mpvDir.absolutePath}", "libmpv-2.dll", "-y")
        logger.lifecycle("fetchMpvNatives: libmpv-2.dll bundled into ${mpvDir.relativeTo(projectDir)}")
    }
}
