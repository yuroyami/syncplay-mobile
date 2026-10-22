package io.github.yuroyami.synkplay.buildplugins.androidrelease

/** What the current toolchain run builds, read from the command line of the toolchain JVM. */
internal object BuildIntent {
    private val commands = setOf("build", "package", "run", "test", "check", "publish", "task", "do", "tool")

    /** True for `./kotlin package` without `-v debug`, and for any command with `-v release`. */
    fun isRelease(): Boolean {
        val args = System.getProperty("sun.java.command").orEmpty().split(' ').filter { it.isNotBlank() }.drop(1)
        val command = args.firstOrNull { it in commands } ?: return false
        val variants = mutableListOf<String>()
        args.forEachIndexed { index, arg ->
            when {
                arg == "-v" || arg == "--variant" -> args.getOrNull(index + 1)?.let(variants::add)
                arg.startsWith("--variant=") -> variants += arg.substringAfter('=')
            }
        }
        return "release" in variants || (command == "package" && variants.isEmpty())
    }
}
