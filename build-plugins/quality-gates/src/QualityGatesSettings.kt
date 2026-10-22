package io.github.yuroyami.synkplay.buildplugins.qualitygates

import org.jetbrains.amper.plugins.Configurable
import java.nio.file.Path

@Configurable
interface QualityGatesSettings {
    /** The module folders whose Kotlin sources detekt and the gates scan, relative to the module directory. */
    val modules: List<Path>

    /** The detekt configuration, relative to the module directory. */
    val detektConfig: Path
}
