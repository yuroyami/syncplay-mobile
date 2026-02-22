package com.yuroyami.syncplay.models

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.path

sealed class MediaFileLocation {
    abstract val commonUri: String

    class Local(val file: PlatformFile): MediaFileLocation() {
        override val commonUri: String = file.path
    }

    class Remote(val url: String): MediaFileLocation() {
        override val commonUri: String = url
    }
}