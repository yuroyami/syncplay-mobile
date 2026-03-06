package app.preferences.settings

import androidx.compose.ui.graphics.vector.ImageVector
import app.preferences.Pref
import org.jetbrains.compose.resources.StringResource

class SettingCategory(
    val title: StringResource,
    val icon: ImageVector,
    settingBuilder: SettingListBuilder.() -> Unit
) {
    val settings: List<Pref<*>> = SettingListBuilder().apply(settingBuilder).list

    class SettingListBuilder {
        internal val list = mutableListOf<Pref<*>>()

        operator fun <T> Pref<T>.unaryPlus() {
            list.add(this)
        }
    }
}