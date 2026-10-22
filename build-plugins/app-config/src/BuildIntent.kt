package io.github.yuroyami.synkplay.buildplugins.appconfig

/**
 * What the current toolchain run builds. Plugin tasks run inside the toolchain JVM, so its
 * command line tells a release build from a debug one. Xcode passes its own configuration.
 */
internal object BuildIntent {
    private val commands = setOf("build", "package", "run", "test", "check", "publish", "task", "do", "tool")

    /** The Xcode build configuration when Xcode runs the Build Kotlin phase, or null. */
    private val xcodeConfiguration: String? =
        System.getenv("CONFIGURATION")?.takeIf { System.getenv("PLATFORM_NAME") != null }

    /**
     * True for a debug build: an Xcode Debug configuration, or `build`, `run` and `test` without a
     * release variant. A release variant, `package`, `do` and `publish` count as release, so a
     * shipped binary never gets the debug-only code paths.
     */
    fun isDebug(): Boolean {
        xcodeConfiguration?.let { return it.equals("Debug", ignoreCase = true) }
        val args = System.getProperty("sun.java.command").orEmpty().split(' ').filter { it.isNotBlank() }.drop(1)
        val command = args.firstOrNull { it in commands } ?: return false
        val variants = mutableListOf<String>()
        args.forEachIndexed { index, arg ->
            when {
                arg == "-v" || arg == "--variant" -> args.getOrNull(index + 1)?.let(variants::add)
                arg.startsWith("--variant=") -> variants += arg.substringAfter('=')
            }
        }
        if ("release" in variants) return false
        return command in setOf("build", "run", "test", "task")
    }
}
