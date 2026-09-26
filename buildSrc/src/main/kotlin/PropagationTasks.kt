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
 * The two tasks that rewrite source files in this repo: syncDefaultStrings and syncTrinityColors.
 *
 * Keep them as tasks, not as configuration-time code. As tasks they run only when their input
 * changes, and Gradle can see what they read and write. Configuration-time code runs on every
 * Gradle invocation, whatever the build was asked to do.
 */
object PropagationTasks {

    /**
     * Registers both tasks and makes the Compose resource preparation depend on the strings task,
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

        // Every task that reads composeResources must run after the fallback is written.
        // Most of these task names end with a source set name, so they are matched by prefix.
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
 * Copies the English strings into the default-qualifier file, without any key that is already
 * declared as untranslatable. Two declarations of one key under the default qualifier make the
 * resource lookup throw on every device whose language has no translation of that key.
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
            StringResources.read(reservedFile).map { it.name }.toSet()
        } else emptySet()

        // Space around the equals sign is allowed, as the XML parser of the gates allows it.
        val nameOf = Regex("""^\s*<string\s+name\s*=\s*"([^"]+)"""")
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
