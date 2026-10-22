package io.github.yuroyami.synkplay.buildplugins.androidrelease

import org.jetbrains.amper.plugins.Configurable
import java.nio.file.Path

@Configurable
interface AndroidReleaseSettings {
    /** An AAR whose `.so` files under `jni/<abi>/` go into the app's jniLibs folder, relative to the module directory. */
    val nativeLibraryAar: Path
}
