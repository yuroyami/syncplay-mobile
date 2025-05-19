package com.yuroyami.syncplay.filepicking

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.yuroyami.syncplay.utils.bookmarkDirectory
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.posix.memcpy

data class IosFile(
    override val path: String,
    override val platformFile: NSURL,
) : MPFile<NSURL> {
    @OptIn(ExperimentalForeignApi::class)
    fun NSData.toByteArray(): ByteArray = ByteArray(this@toByteArray.length.toInt()).apply {
        usePinned {
            memcpy(it.addressOf(0), this@toByteArray.bytes, this@toByteArray.length)
        }
    }

    override suspend fun getFileByteArray(): ByteArray = platformFile.dataRepresentation.toByteArray()
}

@Composable
actual fun FilePicker(
    show: Boolean,
    initialDirectory: String?,
    fileExtensions: List<String>,
    title: String?,
    onFileSelected: FileSelected,
) {
    val launcher = remember {
        FilePickerLauncher(
            initialDirectory = initialDirectory,
            pickerMode = FilePickerLauncher.Mode.File(fileExtensions),
            onFileSelected = {
                onFileSelected(it?.firstOrNull())
            },
        )
    }

    LaunchedEffect(show) {
        if (show) {
            launcher.launchFilePicker()
        }
    }
}

@Composable
actual fun MultipleFilePicker(
    show: Boolean,
    initialDirectory: String?,
    fileExtensions: List<String>,
    title: String?,
    onFileSelected: FilesSelected
) {
    val launcher = remember {
        FilePickerLauncher(
            initialDirectory = initialDirectory,
            pickerMode = FilePickerLauncher.Mode.MultipleFiles(fileExtensions),
            onFileSelected = onFileSelected,
        )
    }

    LaunchedEffect(show) {
        if (show) {
            launcher.launchFilePicker()
        }
    }
}

@Composable
actual fun DirectoryPicker(
    show: Boolean,
    initialDirectory: String?,
    title: String?,
    onFileSelected: (String?) -> Unit,
) {
    val launcher = remember {
        FilePickerLauncher(
            initialDirectory = initialDirectory,
            pickerMode = FilePickerLauncher.Mode.Directory,
            onFileSelected = {
                val actualFile = (it?.firstOrNull() as? IosFile)?.platformFile
                val accessed = actualFile?.startAccessingSecurityScopedResource()
                val path = it?.firstOrNull()?.path
                if (path != null) {
                    bookmarkDirectory(path)
                }
                if (accessed == true) actualFile.stopAccessingSecurityScopedResource()
                onFileSelected(path)
            },
        )
    }

    LaunchedEffect(show) {
        if (show) {
            launcher.launchFilePicker()
        }
    }
}