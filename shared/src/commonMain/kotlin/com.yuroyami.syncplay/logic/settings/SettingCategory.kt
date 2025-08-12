package com.yuroyami.syncplay.logic.settings

import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.StringResource

data class SettingCategory(
    val keyID: String,
    val title: StringResource,
    val icon: ImageVector
)