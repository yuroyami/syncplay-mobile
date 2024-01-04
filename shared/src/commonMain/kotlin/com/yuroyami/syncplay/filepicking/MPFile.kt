package com.yuroyami.syncplay.filepicking

import androidx.compose.runtime.Composable

interface MPFile<out T : Any> {
	// on JS this will be a file name, on other platforms it will be a file path
	val path: String
	val platformFile: T
	suspend fun getFileByteArray(): ByteArray
}

typealias FileSelected = (MPFile<Any>?) -> Unit

typealias FilesSelected = (List<MPFile<Any>>?) -> Unit

@Composable
expect fun FilePicker(
	show: Boolean,
	initialDirectory: String? = null,
	fileExtensions: List<String> = emptyList(),
	title: String? = null,
	onFileSelected: FileSelected,
)

@Composable
expect fun MultipleFilePicker(
	show: Boolean,
	initialDirectory: String? = null,
	fileExtensions: List<String> = emptyList(),
	title: String? = null,
	onFileSelected: FilesSelected
)

@Composable
expect fun DirectoryPicker(
	show: Boolean,
	initialDirectory: String? = null,
	title: String? = null,
	onFileSelected: (String?) -> Unit,
)