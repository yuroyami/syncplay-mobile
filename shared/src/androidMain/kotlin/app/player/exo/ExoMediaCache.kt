package app.player.exo

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import android.net.Uri
import java.io.File

@OptIn(UnstableApi::class)
internal object ExoMediaCache {
    private const val MAX_BYTES = 512L * 1024L * 1024L
    private var instance: SimpleCache? = null

    @Synchronized
    fun cache(context: Context): SimpleCache = instance ?: SimpleCache(
        File(context.applicationContext.cacheDir, "exo-media"),
        LeastRecentlyUsedCacheEvictor(MAX_BYTES),
        StandaloneDatabaseProvider(context.applicationContext),
    ).also { instance = it }

    fun supports(uri: Uri): Boolean =
        (uri.scheme == "http" || uri.scheme == "https") &&
            Util.inferContentType(uri) == C.CONTENT_TYPE_OTHER

    fun mediaSourceFactory(context: Context): DefaultMediaSourceFactory {
        val http = DefaultHttpDataSource.Factory()
        val cachedHttp = CacheDataSource.Factory()
            .setCache(cache(context))
            .setUpstreamDataSourceFactory(http)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        return DefaultMediaSourceFactory(context)
            .setDataSourceFactory(DefaultDataSource.Factory(context, cachedHttp))
    }
}
