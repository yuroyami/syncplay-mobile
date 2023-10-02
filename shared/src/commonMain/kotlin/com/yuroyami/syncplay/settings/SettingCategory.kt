package com.yuroyami.syncplay.settings

import androidx.compose.ui.graphics.vector.ImageVector

data class SettingCategory(
    val title: String,
    val icon: ImageVector,
    val settingList: List<Setting>,
)