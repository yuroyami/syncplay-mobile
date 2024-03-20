package com.yuroyami.syncplay.filepicking

import com.yuroyami.syncplay.filepicking.FilePickerLauncher.Mode
import com.yuroyami.syncplay.filepicking.FilePickerLauncher.Mode.Directory
import com.yuroyami.syncplay.filepicking.FilePickerLauncher.Mode.File
import platform.Foundation.NSURL
import platform.UIKit.UIAdaptivePresentationControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIPresentationController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeContent
import platform.UniformTypeIdentifiers.UTTypeFolder
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.native.concurrent.ThreadLocal

/**
 * Wraps platform specific implementation for launching a
 * File Picker.
 *
 * @param initialDirectory Initial directory that the
 *  file picker should open to.
 * @param pickerMode [Mode] to open the picker with.
 *
 */
class FilePickerLauncher(
    private val initialDirectory: String?,
    private val pickerMode: Mode,
    private val onFileSelected: FilesSelected,
) {

    @ThreadLocal
    companion object {
        /**
         * For use only with launching plain (no compose dependencies)
         * file picker. When a function completes iOS deallocates
         * unreferenced objects created within it, so we need to
         * keep a reference of the active launcher.
         */
        internal var activeLauncher: FilePickerLauncher? = null
    }

    /**
     * Identifies the kind of file picker to open. Either
     * [Directory] or [File].
     */
    sealed interface Mode {
        /**
         * Use this mode to open a [FilePickerLauncher] for selecting
         * folders/directories.
         */
        data object Directory : Mode

        /**
         * Use this mode to open a [FilePickerLauncher] for selecting
         * multiple files.
         *
         * @param extensions List of file extensions that can be
         *  selected on this file picker.
         */
        data class MultipleFiles(val extensions: List<String>) : Mode

        /**
         * Use this mode to open a [FilePickerLauncher] for selecting
         * a single file.
         *
         * @param extensions List of file extensions that can be
         *  selected on this file picker.
         */
        data class File(val extensions: List<String>) : Mode
    }

    private val pickerDelegate = PickerDelegate()

    inner class PickerDelegate : NSObject(),
        UIDocumentPickerDelegateProtocol,
        UIAdaptivePresentationControllerDelegateProtocol {

        override fun documentPicker(
            controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>
        ) {

            (didPickDocumentsAtURLs as? List<*>)?.let { list ->
                val files = list.map { file ->
                    (file as? NSURL)?.let { nsUrl ->
                        nsUrl.path?.let { path ->
                            IosFile(path, nsUrl)
                        }
                    } ?: return@let listOf<IosFile>()
                }

                onFileSelected(files)

            }
        }

        override fun documentPickerWasCancelled(
            controller: UIDocumentPickerViewController
        ) {
            onFileSelected(null)
        }

        override fun presentationControllerWillDismiss(
            presentationController: UIPresentationController
        ) {
            (presentationController.presentedViewController as? UIDocumentPickerViewController)
                ?.let { documentPickerWasCancelled(it) }
        }
    }

    private val contentTypes: List<UTType>
        get() = when (pickerMode) {
            is FilePickerLauncher.Mode.Directory -> listOf(UTTypeFolder)
            is Mode.File -> pickerMode.extensions
                .mapNotNull { UTType.typeWithFilenameExtension(it) }
                .ifEmpty { listOf(UTTypeContent) }

            is Mode.MultipleFiles -> pickerMode.extensions
                .mapNotNull { UTType.typeWithFilenameExtension(it) }
                .ifEmpty { listOf(UTTypeContent) }
        }

    private fun createPicker() = UIDocumentPickerViewController(
        forOpeningContentTypes = contentTypes,
		asCopy = true
    ).apply {
        delegate = pickerDelegate
        initialDirectory?.let { directoryURL = NSURL(string = it) }
    }


    fun launchFilePicker() {
        activeLauncher = this
        val picker = createPicker()
        UIApplication.sharedApplication.keyWindow?.rootViewController?.presentViewController(
            // Reusing a closed/dismissed picker causes problems with
            // triggering delegate functions, launch with a new one.
            picker,
            animated = true,
            completion = {
                (picker as? UIDocumentPickerViewController)
                    ?.allowsMultipleSelection = pickerMode is Mode.MultipleFiles
            },
        )
    }
}

suspend fun launchFilePicker(
    initialDirectory: String? = null,
    fileExtensions: List<String>,
    allowMultiple: Boolean? = false,
): List<MPFile<Any>> = suspendCoroutine { cont ->
    try {
        FilePickerLauncher(
            initialDirectory = initialDirectory,
            pickerMode = if (allowMultiple == true) {
                FilePickerLauncher.Mode.MultipleFiles(fileExtensions)
            } else FilePickerLauncher.Mode.File(fileExtensions),
            onFileSelected = { selected ->
                // File selection has ended, no launcher is active anymore
                // dereference it
                FilePickerLauncher.activeLauncher = null
                cont.resume(selected.orEmpty())
            }
        ).also { launcher ->
            // We're showing the file picker at this time so we set
            // the activeLauncher here. This might be the last time
            // there's an outside reference to the file picker.
            FilePickerLauncher.activeLauncher = launcher
            launcher.launchFilePicker()
        }
    } catch (e: Throwable) {
        // don't swallow errors
        cont.resumeWithException(e)
    }
}

suspend fun launchDirectoryPicker(
    initialDirectory: String? = null,
): List<MPFile<Any>> = suspendCoroutine { cont ->
    try {
        FilePickerLauncher(
            initialDirectory = initialDirectory,
            pickerMode = FilePickerLauncher.Mode.Directory,
            onFileSelected = { selected ->
                // File selection has ended, no launcher is active anymore
                // dereference it
                FilePickerLauncher.activeLauncher = null
                cont.resume(selected.orEmpty())
            },
        ).also { launcher ->
            // We're showing the file picker at this time so we set
            // the activeLauncher here. This might be the last time
            // there's an outside reference to the file picker.
            FilePickerLauncher.activeLauncher = launcher
            launcher.launchFilePicker()
        }
    } catch (e: Throwable) {
        cont.resumeWithException(e)
    }
}