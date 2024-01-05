package com.yuroyami.syncplay.settings

import androidx.compose.ui.graphics.vector.ImageVector

data class SettingCategory(
    val keyID: String,
    val title: String,
    val icon: ImageVector
) {
    var settingList: MutableList<Setting<out Any>> = mutableListOf()
}