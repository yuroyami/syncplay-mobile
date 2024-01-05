package com.yuroyami.syncplay.filepicking

import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.core.net.toFile
import com.yuroyami.syncplay.watchroom.viewmodel

data class AndroidFile(
	override val path: String,
	override val platformFile: Uri,
) : MPFile<Uri> {
	override suspend fun getFileByteArray(): ByteArray = platformFile.toFile().readBytes()
}

@Composable
actual fun FilePicker(
	show: Boolean,
	initialDirectory: String?,
	fileExtensions: List<String>,
	title: String?,
	onFileSelected: FileSelected
) {
	val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { result ->
		viewmodel?.wentForFilePick = false

		if (result != null) {
			onFileSelected(AndroidFile(result.toString(), result))
		} else {
			onFileSelected(null)
		}
	}

	/* val mimeTypeMap = MimeTypeMap.getSingleton()
	val mimeTypes = if (fileExtensions.isNotEmpty()) {
		fileExtensions.mapNotNull { ext ->
			mimeTypeMap.getMimeTypeFromExtension(ext)
		}.toTypedArray()
	} else { */
		//arrayOf("*/*") }

	LaunchedEffect(show) {
		if (show) {
			viewmodel?.wentForFilePick = true
			launcher.launch(arrayOf("*/*"))
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
	val launcher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.OpenMultipleDocuments()
	) { result ->
		viewmodel?.wentForFilePick = false

		val files = result.mapNotNull { uri ->
			uri.path?.let {path ->
				AndroidFile(path, uri)
			}
		}

		if (files.isNotEmpty()) {
			onFileSelected(files)
		} else {
			onFileSelected(null)
		}
	}

	val mimeTypeMap = MimeTypeMap.getSingleton()
	val mimeTypes = if (fileExtensions.isNotEmpty()) {
		fileExtensions.mapNotNull { ext ->
			mimeTypeMap.getMimeTypeFromExtension(ext)
		}.toTypedArray()
	} else {
		emptyArray()
	}

	LaunchedEffect(show) {
		if (show) {
			viewmodel?.wentForFilePick = true
			launcher.launch(mimeTypes)
		}

	}
}

@Composable
actual fun DirectoryPicker(
	show: Boolean,
	initialDirectory: String?,
	title: String?,
	onFileSelected: (String?) -> Unit
) {
	val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocumentTree()) { result ->
		viewmodel?.wentForFilePick = false

		onFileSelected(result?.toString())
	}

	LaunchedEffect(show) {
		if (show) {
			viewmodel?.wentForFilePick = true
			launcher.launch(null)
		}
	}
}