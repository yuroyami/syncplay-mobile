package com.yuroyami.syncplay.utils

import androidx.lifecycle.viewModelScope
import com.yuroyami.syncplay.managers.SharedPlaylistManager
import com.yuroyami.syncplay.managers.protocol.creator.PacketOut
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.Foundation.NSDirectoryEnumerationSkipsHiddenFiles
import platform.Foundation.NSFileCoordinator
import platform.Foundation.NSFileCoordinatorReadingWithoutChanges
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLBookmarkCreationMinimalBookmark
import platform.Foundation.NSURLBookmarkResolutionWithSecurityScope
import platform.Foundation.NSURLIsDirectoryKey
import platform.Foundation.NSURLNameKey
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.dataUsingEncoding
import platform.Foundation.stringWithContentsOfURL
import platform.Foundation.stringWithString
import platform.Foundation.writeToURL

@OptIn(BetaInteropApi::class)
actual suspend fun SharedPlaylistManager.addFolderToPlaylist(uri: String) {
    /* First, we save it in our media directories as a common directory */
    saveFolderPathAsMediaDirectory(uri)

    /* Now we get children files */
    val newList = mutableListOf<String>()

    val url = NSURL.fileURLWithPath(uri, isDirectory = true)
    val access = url.startAccessingSecurityScopedResource()

    NSFileCoordinator().coordinateReadingItemAtURL(url, NSFileCoordinatorReadingWithoutChanges, error = null) { dirUrl ->
        if (dirUrl == null) return@coordinateReadingItemAtURL

        dirUrl.startAccessingSecurityScopedResource()//Start accessing securely
        val enumerator = NSFileManager.defaultManager.enumeratorAtURL(
            url = dirUrl,
            includingPropertiesForKeys = listOf(NSURLNameKey, NSURLIsDirectoryKey),
            options = NSDirectoryEnumerationSkipsHiddenFiles,
            errorHandler = null
        )

        var obj = enumerator?.nextObject() as? NSURL
        while (obj != null) {
            val isDirectory = memScoped {
                val isDirectoryPointer = nativeHeap.alloc<ObjCObjectVar<Any?>>()

                obj?.getResourceValue(isDirectoryPointer.ptr, forKey = NSURLIsDirectoryKey, null)

                return@memScoped isDirectoryPointer.value as? Boolean
            }

            if (isDirectory == false && isValidMediaFileExtension(obj.path?.substringAfterLast("."))) {
                val filename = obj.lastPathComponent
                if (!viewmodel.session.sharedPlaylist.contains(filename)) newList.add(filename!!)
            }

            obj = enumerator?.nextObject() as? NSURL //Next object now
        }
        dirUrl.stopAccessingSecurityScopedResource()//Start accessing securely
    }

    if (access) url.stopAccessingSecurityScopedResource()

    newList.sort()

    if (viewmodel.session.spIndex.intValue == -1) {
        retrieveFile(newList.first())
        viewmodel.networkManager.send<PacketOut.PlaylistIndex> {
            index = 0
        }
    }
    viewmodel.networkManager.send<PacketOut.PlaylistChange> {
        files = viewmodel.session.sharedPlaylist + newList
    }
}

val spExtScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

@OptIn(BetaInteropApi::class)
actual suspend fun iterateDirectory(uri: String, target: String, onFileFound: suspend (String) -> Unit) {
    val data = userDefaults().dataForKey(uri) ?: return

    val resolvedUrl = NSURL.URLByResolvingBookmarkData(
        bookmarkData = data,
        options = NSURLBookmarkResolutionWithSecurityScope,
        relativeToURL = null,
        bookmarkDataIsStale = null,
        error = null
    ) ?: return

    val access = resolvedUrl.startAccessingSecurityScopedResource()

    NSFileCoordinator().coordinateReadingItemAtURL(resolvedUrl, NSFileCoordinatorReadingWithoutChanges, error = null) { dirUrl ->
        if (dirUrl == null) return@coordinateReadingItemAtURL

        val access2 = dirUrl.startAccessingSecurityScopedResource()//Start accessing securely

        val enumerator = NSFileManager.defaultManager.enumeratorAtURL(
            url = dirUrl,
            includingPropertiesForKeys = listOf(NSURLNameKey, NSURLIsDirectoryKey),
            options = NSDirectoryEnumerationSkipsHiddenFiles
        ) { urlOnError, theError ->

            println("This URL caused an error: ${urlOnError?.path}")
            println("The error is: ${theError?.localizedDescription}")

            return@enumeratorAtURL true
        }

        var obj = enumerator?.nextObject() as? NSURL

        while (obj != null) {
            val objAccess = obj.startAccessingSecurityScopedResource()

            val isDirectory = memScoped {
                val isDirectoryPointer = nativeHeap.alloc<ObjCObjectVar<Any?>>()

                obj.getResourceValue(isDirectoryPointer.ptr, forKey = NSURLIsDirectoryKey, null)

                return@memScoped isDirectoryPointer.value as? Boolean
            }

            if (isDirectory == false) {
                val filename = obj.lastPathComponent

                if (filename == target) {

                    obj.path?.let {
                        spExtScope.launch {
                            onFileFound(it)
                        }
                    }
                    break
                }
            }

            if (objAccess) obj.stopAccessingSecurityScopedResource()

            obj = enumerator?.nextObject() as? NSURL //Next object now
        }

        if (access2) dirUrl.stopAccessingSecurityScopedResource()
    }

    if (access) resolvedUrl.stopAccessingSecurityScopedResource()
}


actual fun SharedPlaylistManager.savePlaylistLocally(toFolderUri: String) {
    val destFolder = NSURL.URLWithString(toFolderUri) ?: return
    val destFile = destFolder.URLByAppendingPathComponent("SharedPlaylist_${generateTimestampMillis()}.txt")
        ?: return


    /** Converting the shared playlist to a text string, line by line */
    val stringBuilder = StringBuilder()
    for (f in viewmodel.session.sharedPlaylist) {
        stringBuilder.appendLine(f)
    }
    val string = stringBuilder.appendLine().trim().toString()


    val destFileAccess = destFile.startAccessingSecurityScopedResource()

    viewmodel.viewModelScope.launch(Dispatchers.IO) {
        try {
            val data = (NSString.stringWithString(string) as NSString).dataUsingEncoding(NSUTF8StringEncoding)
            data?.writeToURL(destFile, true)
        } catch (e: Exception) {
            println("Error saving playlist: ${e.stackTraceToString()}")
        }
    }

    if (destFileAccess) {
        destFile.stopAccessingSecurityScopedResource()
    }
}

actual fun SharedPlaylistManager.loadPlaylistLocally(fromUri: String, alsoShuffle: Boolean) {
    val url = NSURL.fileURLWithPath(fromUri, isDirectory = false)
    val access = url.startAccessingSecurityScopedResource()
    val content = NSString.stringWithContentsOfURL(url, NSUTF8StringEncoding, null) ?: return
    if (access) url.stopAccessingSecurityScopedResource()

    /** Reading content */
    val lines = content.split("\n").toMutableList()

    /** If the user chose the shuffling option along with it, then we shuffle it */
    if (alsoShuffle) lines.shuffle()

    /** Updating the shared playlist */
    viewmodel.networkManager.sendAsync<PacketOut.PlaylistChange> {
        files = lines
    }
}


fun isValidMediaFileExtension(fileExtension: String?): Boolean {
    val audioExs = setOf("mp3", "m4a", "wav", "aiff", "aac", "flac", "alac", "ogg", "wma", "ogg")
    return audioExs.contains(fileExtension?.lowercase() ?: "#") || vidExs.contains(fileExtension?.lowercase() ?: "#")
}

@OptIn(ExperimentalForeignApi::class)
fun bookmarkDirectory(dir: String) {
    val url = NSURL.URLWithString(dir)
    url?.startAccessingSecurityScopedResource()
    url?.bookmarkDataWithOptions(
        NSURLBookmarkCreationMinimalBookmark,
        includingResourceValuesForKeys = null,
        relativeToURL = null, error = null
    )?.let {
        userDefaults().setObject(it, dir)
    }
    url?.stopAccessingSecurityScopedResource()
}

fun userDefaults(): NSUserDefaults = NSUserDefaults.standardUserDefaults()