package app.utils

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

object MediastoreUtils {

    fun fetchVideoProperties(context: Context, videoUri: Uri): MediaItem? {
        var item: MediaItem? = null

        runBlocking(Dispatchers.IO) {
            val query = context.contentResolver.query(
                videoUri, projectionVideo, null, null, null
            )

            query?.use { cursor ->
                // Cache column indices.
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DISPLAY_NAME)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DURATION)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.SIZE)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.TITLE)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DATA)


                while (cursor.moveToNext()) {
                    // Get values of columns for a given video.
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val duration = cursor.getInt(durationColumn)
                    val size = cursor.getInt(sizeColumn)
                    val title = cursor.getString(titleColumn)
                    val data = cursor.getString(dataColumn)

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id
                    )

                    item =
                        MediaItem.Builder()
                            .setMediaId("$contentUri")
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(title)
                                    .setDisplayTitle(name)
                                    .setIsBrowsable(false)
                                    .setIsPlayable(true)
                                    .setMediaType(MediaMetadata.MEDIA_TYPE_VIDEO)
                                    .setExtras(Bundle().apply {
                                        putLong("duration", duration.toLong())
                                        putLong("size", size.toLong())
                                    })
                                    .build()
                            )
                            .build()
                }
            }
        }

        return item
    }

    fun fetchSong(context: Context, songUri: Uri): MediaItem? {
        var item: MediaItem? = null

        runBlocking(Dispatchers.IO) {
            val filename = songUri.lastPathSegment ?: return@runBlocking

            val selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(filename)

            val query = context.contentResolver.query(
                collectionAudio, projectionSong, selection, selectionArgs, null
            )

            query?.use { cursor ->
                // Cache column indices.
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.DISPLAY_NAME)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.DURATION)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.SIZE)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ARTIST)
                val albumidColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ALBUM_ID)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ALBUM)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.DATA)

                while (cursor.moveToNext()) {
                    // Get values of columns for a given video.
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val duration = cursor.getInt(durationColumn)
                    val size = cursor.getInt(sizeColumn)
                    val title = cursor.getString(titleColumn)
                    val artist = cursor.getString(artistColumn)
                    val albumId = cursor.getLong(albumidColumn)
                    val album = cursor.getString(albumColumn)
                    val data = cursor.getString(dataColumn)

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                    )

                    val sArtworkUri = Uri.parse("content://media/external/audio/albumart")
                    val albumArtUri = ContentUris.withAppendedId(sArtworkUri, albumId)

                    item =
                        MediaItem.Builder()
                            .setMediaId("$contentUri")
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(title)
                                    .setArtist(artist)
                                    .setAlbumArtist(artist)
                                    .setDisplayTitle(name)
                                    .setIsBrowsable(false)
                                    .setIsPlayable(true)
                                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                    .setArtworkUri(albumArtUri)
                                    .setAlbumTitle(album /* albumMap[albumId] */)
                                    .setExtras(Bundle().apply {
                                        putLong("duration", duration.toLong())
                                        putLong("size", size.toLong())
                                    })
                                    .build()
                            )
                            .build()
                }
            }
        }

        return item
    }

    /** Collection used in querying albums and songs in all the device */
    val collectionAudio =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }


    /** Collection used in querying albums and songs in all the device */
    val collectionVideo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

    /** Projection used for querying song-relevant data */
    val projectionSong = arrayOf(
        MediaStore.Audio.AudioColumns._ID, //Unique ID to fetch Uri
        MediaStore.Audio.AudioColumns.DISPLAY_NAME, //File name
        MediaStore.Audio.AudioColumns.DURATION, //Song Duration
        MediaStore.Audio.AudioColumns.SIZE, //File size
        MediaStore.Audio.AudioColumns.TITLE, //Title != Filename
        MediaStore.Audio.AudioColumns.ARTIST, //Artist
        MediaStore.Audio.AudioColumns.ALBUM_ID, //AlbumID for cover art retrieval
        MediaStore.Audio.AudioColumns.ALBUM, //Album name
        MediaStore.Audio.AudioColumns.DATA //Path (important when selecting folders for querying)
    )

    /** Projection used for querying song-relevant data */
    val projectionVideo = arrayOf(
        MediaStore.Video.VideoColumns._ID, //Unique ID to fetch Uri
        MediaStore.Video.VideoColumns.DISPLAY_NAME, //File name
        MediaStore.Video.VideoColumns.DURATION, //Video Duration
        MediaStore.Video.VideoColumns.SIZE, //File size
        MediaStore.Video.VideoColumns.TITLE, //Title != Filename
        MediaStore.Audio.AudioColumns.DATA //Path (important when selecting folders for querying)
    )

    /** Projection used for querying album-relevant data */
    val projectionAlbum = arrayOf(
        MediaStore.Audio.Albums.ALBUM,
        MediaStore.Audio.Albums.ALBUM_ID,
    )
}