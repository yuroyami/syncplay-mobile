package com.yuroyami.syncplay.compose

import androidx.compose.ui.text.font.Font
import org.jetbrains.compose.resources.readResourceBytes

suspend fun daynightAsset() = readResourceBytes("assets/daynight_toggle.json").decodeToString()