package com.yuroyami.syncplay.managers.settings

import androidx.compose.ui.graphics.vector.ImageVector
import com.yuroyami.syncplay.managers.preferences.Pref
import org.jetbrains.compose.resources.StringResource

data class SettingCategory(
    val keyID: String,
    val title: StringResource,
    val icon: ImageVector
) {
    val booleanSettings: MutableList<Pref<Boolean>> = mutableListOf()
    val actionSettings: MutableList<Pref<Any>> = mutableListOf()
    val stringSettings: MutableList<Pref<String>> = mutableListOf()
    val intSettings: MutableList<Pref<Int>> = mutableListOf()
    val stringSetSettings: MutableList<Pref<Set<String>>> = mutableListOf()
}