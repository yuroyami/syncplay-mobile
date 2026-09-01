package app.preferences.settings

import androidx.compose.ui.graphics.vector.ImageVector
import app.preferences.Pref
import app.preferences.PrefExtraConfig
import org.jetbrains.compose.resources.StringResource

/**
 * One row in a category: a preference plus, optionally, a control and an enabled rule that
 * apply only here. Engines attach their live callbacks this way instead of editing the shared
 * declaration, so the same pref can render with a control in the room and without one elsewhere.
 */
class SettingEntry(
    val pref: Pref<*>,
    val control: PrefExtraConfig? = null,
    val enabledWhen: (() -> Boolean)? = null,
) {
    val extra: PrefExtraConfig? get() = control ?: pref.config?.extraConfig

    fun isEnabled(): Boolean = (enabledWhen ?: pref.config?.dependencyEnable)?.invoke() ?: true
}

fun Pref<*>.withControl(control: PrefExtraConfig): SettingEntry = SettingEntry(this, control)
fun Pref<*>.enabledWhen(rule: () -> Boolean): SettingEntry = SettingEntry(this, null, rule)
fun SettingEntry.enabledWhen(rule: () -> Boolean): SettingEntry = SettingEntry(pref, control, rule)

/** A run of entries under one heading. A null title is the implicit first group. */
class SettingGroup(val title: StringResource?, val entries: List<SettingEntry>)

class SettingCategory(
    val title: StringResource,
    val icon: ImageVector,
    settingBuilder: SettingListBuilder.() -> Unit
) {
    val groups: List<SettingGroup> = SettingListBuilder().apply(settingBuilder).build()

    /** Every entry, in order, ignoring groups. */
    val entries: List<SettingEntry> get() = groups.flatMap { it.entries }

    /** Every pref, in order. Kept for callers that only need the keys (reset, search). */
    val settings: List<Pref<*>> get() = entries.map { it.pref }

    /** Stable identity for deep links, from the title resource. */
    val key: String get() = title.key

    class SettingListBuilder {
        private val groups = mutableListOf<SettingGroup>()
        private val loose = mutableListOf<SettingEntry>()

        operator fun Pref<*>.unaryPlus() { loose.add(SettingEntry(this)) }
        operator fun SettingEntry.unaryPlus() { loose.add(this) }

        /** Starts a titled group. Entries added before the first group land in an implicit one. */
        fun group(title: StringResource, body: SettingListBuilder.() -> Unit) {
            flushLoose()
            val inner = SettingListBuilder().apply(body).build()
            groups.add(SettingGroup(title, inner.flatMap { it.entries }))
        }

        private fun flushLoose() {
            if (loose.isNotEmpty()) {
                groups.add(SettingGroup(null, loose.toList()))
                loose.clear()
            }
        }

        internal fun build(): List<SettingGroup> {
            flushLoose()
            return groups.toList()
        }
    }
}
