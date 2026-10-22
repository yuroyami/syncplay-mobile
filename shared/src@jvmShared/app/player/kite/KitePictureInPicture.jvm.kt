package app.player.kite

/** Android shrinks the whole activity for picture-in-picture, and the desktop has none. */
internal actual fun kitePictureInPicture(onActive: (Boolean) -> Unit): KitePictureInPicture? = null
