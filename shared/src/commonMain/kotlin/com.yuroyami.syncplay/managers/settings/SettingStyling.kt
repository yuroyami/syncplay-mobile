package com.yuroyami.syncplay.managers.settings

data class SettingStyling(
    val titleSize: Float = 15f,
    val summarySize: Float = 11f,
    val iconSize: Int = 26,
    val paddingUsed: Float = 12f,
    val showSummariesByDefault: Boolean = true
)