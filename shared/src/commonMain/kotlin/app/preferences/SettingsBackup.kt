package app.preferences

import app.preferences.settings.SETTINGS_GLOBAL
import app.preferences.settings.SETTINGS_ROOM
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Settings in and out of a file.
 *
 * Half of this existed: mpv's own config could be imported and exported and nothing else could.
 * Moving to a new phone, or keeping two devices in step, meant setting a hundred preferences by
 * hand.
 *
 * What travels is exactly what the settings screens show. Identity, saved themes, the join
 * config and anything holding a password stay behind, because a settings file is something
 * people mail to each other.
 */

/** The file's shape. Versioned so a later format can still read this one. */
@Serializable
data class SettingsBackup(
    val version: Int = FORMAT_VERSION,
    val app: String = "Synkplay",
    val values: Map<String, String>,
) {
    companion object {
        const val FORMAT_VERSION = 1
    }
}

/** Keys that never leave the device, whatever a settings screen shows. */
private val NEVER_EXPORTED = setOf(
    "misc_user_id",
    "misc_join_config",
    "misc_server_salt",
    "pref_server_password",
    "misc_resume_positions",
)

private val backupJson = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

/** Every preference a settings screen shows, minus the ones that stay behind. */
fun exportableSettings(): List<Pref<*>> =
    (SETTINGS_GLOBAL + SETTINGS_ROOM)
        .flatMap { it.settings }
        .distinctBy { it.anyKey.name }
        .filterNot { it.anyKey.name in NEVER_EXPORTED }

/** Reads the live values into a file. */
fun buildSettingsBackup(): String {
    val values = exportableSettings().mapNotNull { pref ->
        val value = pref.valueAny() ?: return@mapNotNull null
        pref.anyKey.name to value.toString()
    }.toMap()
    return backupJson.encodeToString(SettingsBackup(values = values))
}

/** What a restore found, so the caller can say something specific. */
data class RestoreOutcome(val applied: Int, val skipped: Int, val error: String? = null)

/**
 * Reads a backup and hands back the values to write, already matched to real preferences and
 * converted to the type each one stores.
 *
 * A value that does not fit its preference is skipped, not guessed at: a settings file is a
 * text file, and someone will edit one by hand.
 */
fun readSettingsBackup(raw: String): Pair<Map<Pref<*>, Any>, RestoreOutcome> {
    val parsed = runCatching { backupJson.decodeFromString<SettingsBackup>(raw) }.getOrNull()
        ?: return emptyMap<Pref<*>, Any>() to RestoreOutcome(0, 0, "not a settings file")
    if (parsed.version > SettingsBackup.FORMAT_VERSION) {
        return emptyMap<Pref<*>, Any>() to RestoreOutcome(0, 0, "from a newer version of the app")
    }

    val byName = exportableSettings().associateBy { it.anyKey.name }
    val toApply = mutableMapOf<Pref<*>, Any>()
    var skipped = 0
    for ((name, text) in parsed.values) {
        val pref = byName[name]
        if (pref == null) {
            skipped++
            continue
        }
        val converted = convertTo(pref.default, text)
        if (converted == null) skipped++ else toApply[pref] = converted
    }
    return toApply to RestoreOutcome(toApply.size, skipped)
}

/** Reads [text] as whatever type [example] is. Null means it did not fit. */
private fun convertTo(example: Any?, text: String): Any? = when (example) {
    is Boolean -> text.toBooleanStrictOrNull()
    is Int -> text.toIntOrNull()
    is Long -> text.toLongOrNull()
    is Float -> text.toFloatOrNull()
    is Double -> text.toDoubleOrNull()
    is String -> text
    is Set<*> -> text.removeSurrounding("[", "]")
        .split(", ").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    else -> null
}
