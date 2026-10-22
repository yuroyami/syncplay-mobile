package io.github.yuroyami.synkplay.buildplugins.vlckit

import org.jetbrains.amper.plugins.ExecutionAvoidance
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.div
import kotlin.io.path.isDirectory

/**
 * Links the simulator slice of [xcframework] into `Frameworks/` in [binaryDir], the folder of one
 * iOS simulator test executable. The executable loads VLCKit from `@executable_path/Frameworks`.
 *
 * The CocoaPods Gradle plugin pointed the simulator test run at the pod frameworks. The toolchain
 * does not. A symbolic link saves a copy of about 100 MB on every test run.
 *
 * [binaryDir] is a string, not an `@Output` path, because it belongs to a built-in link task. The
 * link task empties it on every new link, so this task runs every time, after the link and before
 * the test run.
 */
@TaskAction(ExecutionAvoidance.Disabled)
fun linkTestFramework(@Input xcframework: Path, binaryDir: String) {
    val binaries = Path(binaryDir)
    if (!binaries.isDirectory()) return
    val framework = xcframework / SLICES.getValue("ios_simulator_arm64") / "VLCKit.framework"
    check(framework.isDirectory()) { "VLCKit has no simulator slice at $framework." }
    val link = (binaries / "Frameworks").createDirectories() / "VLCKit.framework"
    link.deleteIfExists()
    Files.createSymbolicLink(link, framework.toAbsolutePath())
}
