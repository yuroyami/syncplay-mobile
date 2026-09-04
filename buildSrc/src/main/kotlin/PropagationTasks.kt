import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * The two source rewrites this repo does for itself, as real tasks.
 *
 * They used to run at configuration time, on every single Gradle invocation, whatever the build
 * was actually asked to do. As tasks they run when their input changes and not otherwise, and
 * Gradle can see what they read and write.
 */
object PropagationTasks {

    /**
     * Registers both tasks and makes the Compose resource preparation depend on the strings one,
     * so the default-language fallback is always in place before resources are read.
     */
    fun Project.registerPropagationTasks() {
        val resDir = rootProject.layout.projectDirectory.dir("shared/src/commonMain/composeResources")

        val syncStrings = tasks.register("syncDefaultStrings", SyncDefaultStringsTask::class.java) {
            source.set(resDir.file("values-en/strings.xml"))
            untranslatable.set(resDir.file("values/strings_untranslatable.xml"))
            target.set(resDir.file("values/strings.xml"))
        }

        tasks.register("syncTrinityColors", SyncTrinityColorsTask::class.java) {
            icon.set(rootProject.layout.projectDirectory.file("shared/src/androidMain/res/drawable/ic_launcher_foreground.xml"))
            stops.set(listOf(AppConfig.TRINITY_1, AppConfig.TRINITY_2, AppConfig.TRINITY_3).map(::hex))
        }

        // Everything that reads composeResources has to run after the fallback is written.
        // Gradle names these per source set, so they are matched by prefix.
        val readers = listOf(
            "prepareComposeResourcesTaskFor",
            "convertXmlValueResourcesFor",
            "copyNonXmlValueResourcesFor",
            "generateResourceAccessorsFor",
            "generateComposeResClass",
            "generateExpectResourceCollectorsFor",
            "generateActualResourceCollectorsFor",
        )
        tasks.matching { task -> readers.any { task.name.startsWith(it) } }
            .configureEach { dependsOn(syncStrings) }
    }

    private fun hex(argb: Long) = "#FF${argb.toString(16).takeLast(6).uppercase()}"
}

/**
 * Copies the English strings into the default-qualifier file, minus any key that is already
 * declared as untranslatable. Two declarations of one key under the default qualifier make the
 * resource lookup throw on every device whose language has no translation of it.
 */
@CacheableTask
abstract class SyncDefaultStringsTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val source: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val untranslatable: RegularFileProperty

    @get:OutputFile
    abstract val target: RegularFileProperty

    @TaskAction
    fun sync() {
        val src = source.get().asFile
        if (!src.exists()) return
        val reservedFile = untranslatable.orNull?.asFile
        val reserved = if (reservedFile != null && reservedFile.exists()) {
            Regex("""<string(?:-array)?\s+name="([^"]+)"""")
                .findAll(reservedFile.readText())
                .map { it.groupValues[1] }
                .toSet()
        } else emptySet()

        val nameOf = Regex("""^\s*<string\s+name="([^"]+)"""")
        val text = src.readLines()
            .filterNot { nameOf.find(it)?.groupValues?.get(1) in reserved }
            .joinToString("\n") + "\n"

        val dst = target.get().asFile
        if (!dst.exists() || dst.readText() != text) dst.writeText(text)
    }
}

/** Rewrites the launcher foreground's three gradient stops to the brand colours. */
abstract class SyncTrinityColorsTask : DefaultTask() {

    @get:OutputFile
    abstract val icon: RegularFileProperty

    @get:Input
    abstract val stops: org.gradle.api.provider.ListProperty<String>

    @TaskAction
    fun sync() {
        val file = icon.get().asFile
        if (!file.exists()) return
        val colours = stops.get()
        if (colours.size < 3) return

        val original = file.readText()
        var updated = original
        listOf("0" to colours[0], "0.5" to colours[1], "0.9" to colours[2]).forEach { (offset, colour) ->
            updated = updated.replace(
                Regex("""(<item android:offset="$offset" android:color=")#[0-9A-Fa-f]+("/>)""")
            ) { m -> "${m.groupValues[1]}$colour${m.groupValues[2]}" }
        }
        if (updated != original) file.writeText(updated)
    }
}
