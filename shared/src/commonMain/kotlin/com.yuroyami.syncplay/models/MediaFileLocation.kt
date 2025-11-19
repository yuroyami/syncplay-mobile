package com.yuroyami.syncplay.models

import io.github.vinceglb.filekit.PlatformFile

sealed class MediaFileLocation {
    class Local(val file: PlatformFile): MediaFileLocation()
    class Remote(val url: String): MediaFileLocation()
}