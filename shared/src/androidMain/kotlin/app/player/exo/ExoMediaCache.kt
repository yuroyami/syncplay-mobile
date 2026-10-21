package app.player.exo

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.ForwardingExtractorOutput
import androidx.media3.extractor.SeekMap
import android.net.Uri
import java.io.File

@OptIn(UnstableApi::class)
internal object ExoMediaCache {
    private const val MAX_BYTES = 2L * 1024L * 1024L * 1024L
    const val WINDOW_LIMIT_BYTES = MAX_BYTES - 16L * 1024L * 1024L
    const val FRAGMENT_BYTES = 1024L * 1024L
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

    fun cacheDataSourceFactory(context: Context): CacheDataSource.Factory {
        val http = DefaultHttpDataSource.Factory()
        return CacheDataSource.Factory()
            .setCache(cache(context))
            .setUpstreamDataSourceFactory(http)
            .setCacheWriteDataSinkFactory(
                CacheDataSink.Factory().setCache(cache(context)).setFragmentSize(FRAGMENT_BYTES),
            )
    }

    fun mediaSourceFactory(context: Context, onSeekMap: (SeekMap) -> Unit): DefaultMediaSourceFactory {
        // Bounded background writers populate the window; playback can always bypass a busy cache.
        val cachedHttp = cacheDataSourceFactory(context)
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val extractors = object : ExtractorsFactory {
            private val delegate = DefaultExtractorsFactory()
            override fun createExtractors(): Array<Extractor> = wrap(delegate.createExtractors())
            override fun createExtractors(uri: Uri, headers: Map<String, List<String>>): Array<Extractor> =
                wrap(delegate.createExtractors(uri, headers))

            private fun wrap(extractors: Array<Extractor>): Array<Extractor> = extractors.map { extractor ->
                object : Extractor by extractor {
                    override fun init(output: ExtractorOutput) {
                        extractor.init(object : ForwardingExtractorOutput(output) {
                            override fun seekMap(seekMap: SeekMap) {
                                runCatching { onSeekMap(seekMap) }
                                super.seekMap(seekMap)
                            }
                        })
                    }
                }
            }.toTypedArray()
        }
        return DefaultMediaSourceFactory(context, extractors)
            .setDataSourceFactory(DefaultDataSource.Factory(context, cachedHttp))
    }
}
