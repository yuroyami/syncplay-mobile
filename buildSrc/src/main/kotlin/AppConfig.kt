import org.gradle.api.Project
import org.gradle.api.provider.ProviderFactory
import java.io.File
import java.util.Properties

/**
 * Build helpers that are not app identity. The root kiteConfig block declares the app identity
 * (name, version, bundle ids) once, and the build reads it through the kiteConfig accessor;
 * nothing here repeats it. This object holds the exoOnly flavor flag, the local.properties
 * loader, the native library list and the brand colors.
 */
object AppConfig {
    /* ── Shared framework name (not app identity; kiteConfig does not expose it) ─────────────────── */
    const val SHARED_MODULE_NAME = "shared"

    /**
     * Reads `<rootDir>/local.properties` (signing secrets and local keys) and returns the parsed
     * [Properties]. Pass the caller's `rootDir` explicitly. A lookup through the JVM working
     * directory returns empty Properties when the Gradle daemon's working directory is not the
     * project root, and that shows up as a misleading "SigningConfig missing storePassword"
     * error at sign time.
     */
    fun localProperties(rootDir: File): Properties = Properties().apply {
        val file = File(rootDir, "local.properties")
        if (file.exists()) load(file.inputStream())
    }

    /** Compile-time default for the [exoOnly] flavor. Override it at build time with
     *  `-PexoOnly=true` (or a line in gradle.properties); see [resolveExoOnly]. */
    const val exoOnly = false

    /**
     * Resolves the [exoOnly] flavor flag from the command line or gradle.properties
     * (`-PexoOnly=true`), so no source edit is needed. A reproducible-build setup (for example
     * IzzyOnDroid) uses this to select the exoOnly variant, which ships no native player library,
     * with a plain Gradle invocation. The build logic (androidApp) and the EXOPLAYER_ONLY
     * BuildConfig field must both read the flag through this function, so the build and the app
     * code always agree. Falls back to [exoOnly].
     */
    fun resolveExoOnly(providers: ProviderFactory): Boolean =
        providers.gradleProperty("exoOnly").orNull?.toBooleanStrictOrNull() ?: exoOnly

    /* ── Trinity brand colors (the single definition of the logo gradient) ───────────────────── */
    // Trinity is the name of these three brand colors: the gradient stops that cover most of the
    // logo's visible sail area. The wordmark, the launcher icon and the default theme use them,
    // so all three look like one object. The full five-stop gradient is in
    // art/synkplay_logo_palette.md; when the logo art changes, these values change with it.
    const val TRINITY_1 = 0xFF9879EF  // Gentle ultraviolet (logo stop 25%)
    const val TRINITY_2 = 0xFFC331D8  // Softened orchid-magenta (logo stop 55%)
    const val TRINITY_3 = 0xFFD86B75  // Dusty coral (logo stop 88%)

    /**
     * Every native library the libmpvkt AAR carries. The exoOnly flavor removes them at packaging
     * time, so that build ships no native player, which IzzyOnDroid's reproducible build relies
     * on. Keep the list equal to what the pinned libmpvkt version ships; verifyExoOnlyApk fails
     * the build if a player library still reaches the APK.
     */
    val libmpvNativeLibs = listOf(
        "libavcodec.so", "libavdevice.so", "libavfilter.so", "libavformat.so", "libavutil.so",
        "libswresample.so", "libswscale.so", "libmpv.so", "libmpvkt_jni.so", "libc++_shared.so",
    )

    /* ── Propagation: trinity colors → Android vector drawable gradients ────────────────────────── */
    // The syncTrinityColors task in PropagationTasks.kt copies these colors into the launcher
    // drawable.
}
