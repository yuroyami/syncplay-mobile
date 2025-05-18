package com.yuroyami.syncplay.utils

import androidx.compose.ui.platform.ClipEntry

actual fun ClipEntry.getText(): String? {
    return this.getPlainText()
}