package io.github.yuroyami.synkplay.buildplugins.commonksp

import org.jetbrains.amper.plugins.Configurable
import java.nio.file.Path

@Configurable
interface CommonKspSettings {
    /** The source folders that hold the annotated code (the Ktorfit interfaces), relative to the module directory. */
    val sourceRoots: List<Path>

    /** The Compose resources folder whose string files (values and values-xx) Lyricist reads, relative to the module directory. */
    val resources: Path

    /** The KSP processor options. `lyricist.xml.resourcesPath` is added from [resources]. */
    val processorOptions: Map<String, String> get() = emptyMap()

    /** The Kotlin language and API version that KSP analyzes the sources with. */
    val languageVersion: String get() = "2.4"
}
