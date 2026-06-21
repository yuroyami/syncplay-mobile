plugins {
    id("io.github.yuroyami.kmpssot") version "1.4.0"

    alias(libs.plugins.kotlin.multiplatform).apply(false)
    alias(libs.plugins.kotlin.android).apply(false)
    alias(libs.plugins.kotlin.cocoapods).apply(false)

    alias(libs.plugins.compose.compiler).apply(false)
    alias(libs.plugins.compose.plugin).apply(false)

    alias(libs.plugins.android.application).apply(false)
    alias(libs.plugins.android.kmp.library).apply(false)

    alias(libs.plugins.kSerialization).apply(false)
    alias(libs.plugins.ksp).apply(false)

    alias (libs.plugins.ktorfit).apply(false)

    alias(libs.plugins.buildConfig).apply(false)
}

kmpSsot {
    appName         = "Synkplay"
    versionName     = "0.23.1"
    bundleIdBase    = "com.yuroyami.syncplay"
    iosBundleSuffix = ".iosApp"

    sharedModule     = "shared"
    androidAppModule = "androidApp"

    javaVersion = 21

    appLogoPngForeground = File("${rootDir}/shared/src/commonMain/composeResources/drawable/logo_fg.png")
    appLogoPngBackground = File("${rootDir}/shared/src/commonMain/composeResources/drawable/logo_bg.png")
    appLogoAndroidSafeZoneRatio = 0.5

    // exoOnly flavor switches the Android base ID to "com.reddnek.syncplay" — let the
    // user-side flavor branch override the plugin's default (since user's defaultConfig
    // block runs after our plugins.withId callback, the override wins). Plugin still
    // propagates iOS PRODUCT_BUNDLE_IDENTIFIER as com.yuroyami.syncplay.iosApp.

    // Custom Trinity color, logo→imageset, and default-strings fallback propagation
    // stay in buildSrc/AppConfig.kt — outside plugin scope.
}

/*
 * androidReleaseAll: build every shippable Android artifact and collect them under
 * AndroidAppOutput/ at the repo root. Produces 7 files:
 *   full     5x release APKs   (per-ABI + universal, ABI-split)
 *   exoOnly  1x release APK    (no native libs, no split, one universal apk)
 *   full     1x release AAB    (Play Store upload)
 *
 * Shells out to THREE separate `./gradlew` runs (not in-process dependsOn) because:
 *   1. Only ONE product flavor exists per Gradle invocation. -PexoOnly (see androidApp's
 *      productFlavors) is read at configuration time and also flips applicationId,
 *      native-lib packaging, ABI splits, and the preBuild native step, so "full" and
 *      "exoOnly" can't coexist in one build model.
 *   2. ABI splits (full APKs) and the AAB are mutually exclusive in one task graph
 *      (AGP issuetracker 402800800, see androidApp's splits comment).
 * Each run is a clean OS process, the isolation the per-invocation -PexoOnly switch needs.
 * Per-file renaming happens in androidApp's onVariants block; this only gathers artifacts.
 *
 * Run with:  ./gradlew androidReleaseAll
 */
abstract class AndroidReleaseAllTask @Inject constructor(
    private val execOps: ExecOperations,
    private val fsOps: FileSystemOperations,
) : DefaultTask() {

    @get:Input
    abstract val versionName: Property<String>

    /* The Gradle wrapper used to launch each isolated sub-build. */
    @get:Internal
    abstract val gradlewScript: RegularFileProperty

    /* Repo root: working dir for the wrapper and parent of AndroidAppOutput/. */
    @get:Internal
    abstract val repoRoot: DirectoryProperty

    @get:Internal
    abstract val fullApkDir: DirectoryProperty

    @get:Internal
    abstract val exoApkDir: DirectoryProperty

    @get:Internal
    abstract val fullAabDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    private fun gradle(vararg args: String) {
        val script = gradlewScript.get().asFile
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val launcher = if (isWindows) listOf("cmd", "/c", script.absolutePath) else listOf(script.absolutePath)
        execOps.exec {
            workingDir = repoRoot.get().asFile
            commandLine(launcher + args)
            // Default isIgnoreExitValue = false, so a failed sub-build aborts the whole task.
        }
    }

    @TaskAction
    fun run() {
        val v = versionName.get()

        /* Three isolated sub-builds. -PexoOnly flips the entire project model; the
         * bundle run drops ABI splits by itself (it sees "bundle" in the task name). */
        logger.lifecycle("androidReleaseAll: [1/3] full release APKs (ABI-split)...")
        gradle(":androidApp:assembleFullRelease", "-PexoOnly=false")

        logger.lifecycle("androidReleaseAll: [2/3] exoOnly release APK...")
        gradle(":androidApp:assembleExoOnlyRelease", "-PexoOnly=true")

        logger.lifecycle("androidReleaseAll: [3/3] full release AAB...")
        gradle(":androidApp:bundleFullRelease", "-PexoOnly=false")

        /* Fresh output dir so a version bump never leaves stale artifacts behind. */
        val out = outputDir.get().asFile
        fsOps.delete { delete(out) }
        out.mkdirs()

        /* Copy only this version's APKs: the build dirs keep older-version files
         * around (the name changes per release), so an unfiltered *.apk would drag
         * stale builds along. The single AAB is renamed to match the APK naming. */
        fsOps.copy {
            from(fullApkDir) { include("*-$v-*.apk") }
            from(exoApkDir) { include("*-$v-*.apk") }
            from(fullAabDir) {
                include("*.aab")
                rename { "synkplay-$v-full.aab" }
            }
            into(out)
        }

        val produced = out.listFiles()?.filter { it.isFile }?.sortedBy { it.name }.orEmpty()
        logger.lifecycle("androidReleaseAll: done. ${produced.size} artifact(s) in AndroidAppOutput/:")
        produced.forEach { logger.lifecycle("    ${it.name}  (${it.length() / 1_000_000} MB)") }
    }
}

tasks.register<AndroidReleaseAllTask>("androidReleaseAll") {
    group = "syncplay"
    description = "Build all release APKs (full ABI-split + exoOnly) plus the full-flavor AAB into AndroidAppOutput/."

    /* Always run: the real work happens in nested builds whose outputs Gradle can't
     * track from here, so normal up-to-date checks would wrongly skip the task. */
    outputs.upToDateWhen { false }

    val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    versionName.set(kmpSsot.versionName.get())
    gradlewScript.set(layout.projectDirectory.file(if (isWindows) "gradlew.bat" else "gradlew"))
    repoRoot.set(layout.projectDirectory)
    // androidApp uses the default build dir (androidApp/build); reference it via the
    // repo root so we don't force cross-project configuration from here.
    fullApkDir.set(layout.projectDirectory.dir("androidApp/build/outputs/apk/full/release"))
    exoApkDir.set(layout.projectDirectory.dir("androidApp/build/outputs/apk/exoOnly/release"))
    fullAabDir.set(layout.projectDirectory.dir("androidApp/build/outputs/bundle/fullRelease"))
    outputDir.set(layout.projectDirectory.dir("AndroidAppOutput"))
}