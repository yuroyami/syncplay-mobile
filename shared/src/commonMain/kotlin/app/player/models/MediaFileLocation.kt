package app.player.models

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.path

sealed class MediaFileLocation {
    abstract val commonUri: String

    class Local(val file: PlatformFile): MediaFileLocation() {
        override val commonUri: String = file.path
    }

    /**
     * A file on the network. [pageUrl] is the page the address came from when a resolver turned a
     * page (a YouTube link, for example) into [url], and null for a direct link.
     */
    class Remote(val url: String, val pageUrl: String? = null): MediaFileLocation() {
        override val commonUri: String = url
    }
}