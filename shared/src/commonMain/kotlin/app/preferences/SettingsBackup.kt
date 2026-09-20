package app.preferences

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

/**
 * Every setting with a row, minus actions and the ones that stay behind.
 *
 * Read from the registry, not from the two settings screens: an engine's own rows and the
 * colours behind a nested editor are real preferences that were quietly not travelling.
 */
fun exportableSettings(): List<Pref<*>> {
    // Before the snapshot, not after: the registry fills as the preferences are constructed.
    Preferences.ensureAllRegistered()
    return PrefRegistry.snapshot()
        .filter { it.config != null }
        .filterNot { it.key in NEVER_EXPORTED }
        .filterNot {
            // A button is not a setting: exporting one would restore a press.
            when (it.config?.extraConfig) {
                is PrefExtraConfig.PerformAction, is PrefExtraConfig.ShowComposable,
                is PrefExtraConfig.Nested, is PrefExtraConfig.YesNoDialog -> true
                else -> false
            }
        }
        .distinctBy { it.key }
}

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
        val converted = convertTo(pref, text)
        if (converted == null) skipped++ else toApply[pref] = converted
    }
    return toApply to RestoreOutcome(toApply.size, skipped)
}

/**
 * Reads [text] as whatever type [pref] stores, and refuses a value the row itself would not
 * offer. Null means it did not fit.
 *
 * Type alone is not enough. A settings file is a text file and somebody will edit one by hand, so
 * a number outside its slider or a choice that names nothing has to be skipped rather than
 * written: the second one leaves a code path selecting neither branch.
 */
private fun convertTo(pref: Pref<*>, text: String): Any? {
    val converted = when (pref.default) {
        is Boolean -> text.toBooleanStrictOrNull()
        is Int -> text.toIntOrNull()
        is Long -> text.toLongOrNull()
        is Float -> text.toFloatOrNull()
        is Double -> text.toDoubleOrNull()
        is String -> text
        is Set<*> -> text.removeSurrounding("[", "]")
            .split(", ").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        else -> null
    } ?: return null

    val extra = pref.config?.extraConfig
    if (converted is Int && extra is PrefExtraConfig.Slider && converted !in extra.minValue..extra.maxValue) return null
    // A file from an earlier version can carry the old "use the theme's colour" marker for a chat colour.
    if (converted == ChatColorCleanup.OLD_THEME_MARKER && pref.key in ChatColorCleanup.keyNames) return null
    if (converted is Float && !converted.isFinite()) return null
    if (converted is Double && !converted.isFinite()) return null
    KNOWN_CHOICES[pref.key]?.let { allowed -> if (converted !in allowed) return null }

    return converted
}

/**
 * Choice preferences that steer code paths, and the values their rows offer.
 *
 * Only the ones where an unknown value selects nothing at all. The mpv lists are copied here
 * because they live in Android source that common code cannot see; they change about once a
 * decade, and a value missing from this list is skipped rather than mis-applied.
 */
private val KNOWN_CHOICES: Map<String, Set<String>> = mapOf(
    "pref_unpause_action" to setOf("IfAlreadyReady", "IfOthersReady", "IfMinUsersReady", "Always"),
    "pref_network_engine" to setOf("ktor", "netty", "swiftnio"),
    "pref_hash_filename" to setOf("1", "2", "3"),
    "pref_hash_filesize" to setOf("1", "2", "3"),
    "pref_mpv_video_sync" to setOf(
        "audio", "display-resample", "display-resample-vdrop", "display-resample-desync",
        "display-tempo", "display-vdrop", "display-adrop", "display-desync", "desync",
    ),
    "pref_mpv_profile" to setOf("fast", "high-quality", "gpu-hq", "low-latency", "sw-fast"),
)
