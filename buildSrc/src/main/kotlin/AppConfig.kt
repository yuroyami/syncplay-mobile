import org.gradle.api.Project
import org.gradle.api.provider.ProviderFactory
import java.io.File
import java.util.Properties

/**
 * Build helpers that are not app identity. App identity (name, version, bundle ids) is
 * declared once in the root kiteConfig { } block and read back through the kiteConfig
 * accessor; nothing here duplicates it. What lives here: the flavor flag, signing-secret
 * loading, native build data, brand colors, and the custom propagators.
 */
object AppConfig {
    /* ── Shared framework name (not app identity; kiteConfig does not expose it) ─────────────────── */
    const val SHARED_MODULE_NAME = "shared"

    /**
     * Reads `<rootDir>/local.properties` (signing secrets) and returns the parsed [Properties].
     * The caller's `rootDir` must be passed explicitly: resolving via the JVM working directory
     * returns empty Properties when the Gradle daemon's CWD isn't the project root, which surfaces
     * as a misleading "SigningConfig missing storePassword" error at sign time.
     */
    fun localProperties(rootDir: File): Properties = Properties().apply {
        val file = File(rootDir, "local.properties")
        if (file.exists()) load(file.inputStream())
    }

    /** Compile-time default for the [exoOnly] flavor. Override at build time with
     *  `-PexoOnly=true` (or a line in gradle.properties) — see [resolveExoOnly]. */
    const val exoOnly = false

    /**
     * Resolves the [exoOnly] flavor flag, letting it be overridden from the command line
     * or gradle.properties (`-PexoOnly=true`) without editing source. This is what lets a
     * reproducible-build setup (e.g. IzzyOnDroid) select the exo-only variant — which skips
     * the mpv native build scripts entirely — via a plain Gradle invocation. Both the
     * build logic (androidApp) and the EXOPLAYER_ONLY BuildConfig field (shared) must read
     * through here so the build and the app code never disagree. Falls back to [exoOnly].
     */
    fun resolveExoOnly(providers: ProviderFactory): Boolean =
        providers.gradleProperty("exoOnly").orNull?.toBooleanStrictOrNull() ?: exoOnly

    /* ── Trinity brand colors (SSOT for the logo gradient) ──────────────────────────────────────── */
    // The three stops that cover most of the logo's visible sail area, so the wordmark, the
    // launcher icon and the default theme all read as one object. The full five-stop field lives
    // in art/synkplay_logo_palette.md; change the logo art and these move with it.
    const val TRINITY_1 = 0xFF9879EF  // Gentle ultraviolet (logo stop 25%)
    const val TRINITY_2 = 0xFFC331D8  // Softened orchid-magenta (logo stop 55%)
    const val TRINITY_3 = 0xFFD86B75  // Dusty coral (logo stop 88%)

    val abiCodes = mapOf(
        "armeabi-v7a" to "armv7l",
        "arm64-v8a" to "arm64",
        "x86" to "x86",
        "x86_64" to "x86_64"
    )

    val mpvLibs = listOf(
        "libavcodec.so", "libavdevice.so", "libavfilter.so",
        "libavformat.so", "libavutil.so", "libmpv.so", "libplayer.so",
        "libswresample.so", "libswscale.so"
    )

    /* ── Propagation: trinity colors → Android vector drawable gradients ────────────────────────── */
    fun Project.propagateTrinityColors() {
        val hex1 = "#FF${TRINITY_1.toString(16).takeLast(6).uppercase()}"
        val hex2 = "#FF${TRINITY_2.toString(16).takeLast(6).uppercase()}"
        val hex3 = "#FF${TRINITY_3.toString(16).takeLast(6).uppercase()}"

        val fgFile = File("${rootDir}/shared/src/androidMain/res/drawable/ic_launcher_foreground.xml")
        if (fgFile.exists()) {
            val original = fgFile.readText()
            val updated = original
                .replace(Regex("""(<item android:offset="0" android:color=")#[0-9A-Fa-f]+("/>)""")) { m ->
                    "${m.groupValues[1]}$hex1${m.groupValues[2]}"
                }
                .replace(Regex("""(<item android:offset="0.5" android:color=")#[0-9A-Fa-f]+("/>)""")) { m ->
                    "${m.groupValues[1]}$hex2${m.groupValues[2]}"
                }
                .replace(Regex("""(<item android:offset="0.9" android:color=")#[0-9A-Fa-f]+("/>)""")) { m ->
                    "${m.groupValues[1]}$hex3${m.groupValues[2]}"
                }
            if (updated != original) {
                fgFile.writeText(updated)
                logger.lifecycle("✅ ic_launcher_foreground.xml trinity colors updated")
            }
        }
    }

    /* ── Propagation: values-en/strings.xml → values/strings.xml (default fallback) ─────────────── */
    fun Project.propagateDefaultStrings() {
        val resDir = File("${rootDir}/shared/src/commonMain/composeResources")
        val src = File(resDir, "values-en/strings.xml")
        val dst = File(resDir, "values/strings.xml")
        val untranslatable = File(resDir, "values/strings_untranslatable.xml")
        if (!src.exists()) return
        // A key declared in strings_untranslatable.xml must not also land in values/strings.xml:
        // two default-qualifier declarations of one key make the resource resolver throw on
        // every device whose language has no translation of it (issue #151).
        val reserved = if (untranslatable.exists()) {
            Regex("""<string(?:-array)?\s+name="([^"]+)"""").findAll(untranslatable.readText()).map { it.groupValues[1] }.toSet()
        } else emptySet()
        val nameOf = Regex("""^\s*<string\s+name="([^"]+)"""")
        val filtered = src.readLines().filterNot { line ->
            nameOf.find(line)?.groupValues?.get(1) in reserved
        }
        val text = filtered.joinToString("\n") + "\n"
        if (!dst.exists() || dst.readText() != text) {
            dst.writeText(text)
            logger.lifecycle("✅ Default strings fallback synced from values-en")
        }
    }

    /* ── Master propagation: invoke all custom (non-plugin) SSOT propagators ────────────────────── */
    fun Project.propagateAllCustom() {
        propagateTrinityColors()
        propagateDefaultStrings()
    }
}
