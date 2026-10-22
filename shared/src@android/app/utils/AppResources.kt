package app.utils

import android.annotation.SuppressLint
import android.content.Context

/**
 * The ID of an Android resource of the app, looked up by name.
 *
 * The Android resources (drawables, layouts) live in androidApp/res, and the shared module has no
 * R class for them. androidApp/res/raw/keep.xml keeps every resource that is read this way, so
 * resource shrinking in release builds does not remove it.
 */
@SuppressLint("DiscouragedApi")
internal fun Context.appResource(type: String, name: String): Int {
    val id = resources.getIdentifier(name, type, packageName)
    check(id != 0) { "The app has no resource $type/$name. Add it to androidApp/res and to res/raw/keep.xml." }
    return id
}
