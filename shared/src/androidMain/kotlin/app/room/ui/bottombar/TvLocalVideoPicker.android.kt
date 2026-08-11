package app.room.ui.bottombar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.uicomponents.SyncplayPopup
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.cancel
import syncplaymobile.shared.generated.resources.room_addmedia_offline_tv_empty
import syncplaymobile.shared.generated.resources.room_addmedia_offline_tv_permission
import syncplaymobile.shared.generated.resources.room_addmedia_offline_tv_title

@Composable
internal actual fun rememberTvLocalVideoPickerLauncher(
    onVideoSelected: (PlatformFile) -> Unit,
): (() -> Unit)? {
    val context = LocalContext.current
    if (!context.isTelevisionDevice()) return null

    val scope = rememberCoroutineScope()
    val firstVideoFocusRequester = remember { FocusRequester() }
    var dialogOpen by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    var videos by remember { mutableStateOf(emptyList<TvVideo>()) }

    fun loadVideos() {
        scope.launch {
            loading = true
            permissionDenied = false
            videos = withContext(Dispatchers.IO) { context.queryLocalVideos() }
            loading = false
            dialogOpen = true
        }
    }

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            loadVideos()
        } else {
            permissionDenied = true
            loading = false
            dialogOpen = true
        }
    }

    val launchPicker: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            loadVideos()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    LaunchedEffect(dialogOpen, videos) {
        if (dialogOpen && videos.isNotEmpty()) {
            delay(100)
            firstVideoFocusRequester.requestFocus()
        }
    }

    SyncplayPopup(
        dialogOpen = dialogOpen,
        widthPercent = 0.72f,
        heightPercent = 0.82f,
        onDismiss = { dialogOpen = false },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.room_addmedia_offline_tv_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(16.dp))

            when {
                loading -> {
                    CircularProgressIndicator()
                }

                permissionDenied -> {
                    Text(
                        text = stringResource(Res.string.room_addmedia_offline_tv_permission),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                videos.isEmpty() -> {
                    Text(
                        text = stringResource(Res.string.room_addmedia_offline_tv_empty),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(videos, key = { _, video -> video.uri }) { index, video ->
                            Button(
                                modifier = Modifier.fillMaxWidth()
                                    .then(
                                        if (index == 0) {
                                            Modifier.focusRequester(firstVideoFocusRequester)
                                        } else {
                                            Modifier
                                        }
                                    ),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                                onClick = {
                                    dialogOpen = false
                                    onVideoSelected(PlatformFile(video.uri))
                                },
                            ) {
                                Icon(Icons.Filled.Movie, contentDescription = null)
                                Spacer(Modifier.width(12.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.Start,
                                ) {
                                    Text(
                                        text = video.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = video.details,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { dialogOpen = false }) {
                Text(stringResource(Res.string.cancel))
            }
        }
    }

    return launchPicker
}

private data class TvVideo(
    val uri: String,
    val name: String,
    val durationMs: Long,
    val sizeBytes: Long,
) {
    val details: String
        get() = "${formatDuration(durationMs)} - ${formatFileSize(sizeBytes)}"
}

private fun Context.queryLocalVideos(): List<TvVideo> {
    val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.SIZE,
    )

    return contentResolver.query(
        collection,
        projection,
        null,
        null,
        "${MediaStore.Video.Media.DATE_MODIFIED} DESC",
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
        val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
        val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

        buildList {
            while (cursor.moveToNext()) {
                add(
                    TvVideo(
                        uri = ContentUris.withAppendedId(collection, cursor.getLong(idColumn)).toString(),
                        name = cursor.getString(nameColumn) ?: "Unnamed video",
                        durationMs = cursor.getLong(durationColumn),
                        sizeBytes = cursor.getLong(sizeColumn),
                    )
                )
            }
        }
    }.orEmpty()
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private fun formatFileSize(sizeBytes: Long): String {
    val megabytes = sizeBytes.coerceAtLeast(0L) / (1024.0 * 1024.0)
    return "${(megabytes * 10).toLong() / 10.0} MB"
}
