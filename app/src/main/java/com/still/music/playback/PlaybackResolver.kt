package com.still.music.playback

import com.still.music.provider.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.io.IOException
import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import kotlinx.coroutines.runBlocking

/** Reuse a fresh stream across range reads/seeks; completed downloads never contact a provider. */
class PlaybackResolver(private val providers: ProviderRegistry, private val local: (String) -> File?,
    private val clock: () -> Long = System::currentTimeMillis) {
    private val mutex = Mutex()
    private val cached = LinkedHashMap<String, Pair<PlaybackAccess, Long>>()
    suspend fun resolve(id: String, forceRefresh: Boolean = false): PlaybackAccess = withContext(Dispatchers.IO) {
        local(id)?.let { return@withContext PlaybackAccess(it.toURI().toString()) }
        mutex.withLock {
            if (forceRefresh) cached.remove(id)
            cached[id]?.takeIf { it.second > clock() }?.let { return@withLock it.first }
            val access = providers.forTrack(id).resolvePlayback(id)
            val expiry = access.url.toHttpUrlOrNull()?.queryParameter("expire")?.toLongOrNull()?.times(1000)
            val until = minOf(clock() + 120_000, expiry?.minus(60_000) ?: Long.MAX_VALUE)
            if (cached.size >= 20) cached.remove(cached.keys.first())
            cached[id] = access to until
            access
        }
    }
    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        val YOUTUBE_HEADERS = mapOf(
            "User-Agent" to USER_AGENT,
            "Referer" to "https://www.youtube.com/"
        )
    }

    /** One refresh per data-source instance/track, including subsequent range opens. */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun dataSourceFactory(upstream: DataSource.Factory): DataSource.Factory = DataSource.Factory {
        val source = upstream.createDataSource()
        object : DataSource by source {
            private val refreshed = mutableSetOf<String>()

            override fun open(dataSpec: DataSpec): Long {
                if (dataSpec.uri.scheme != "still") throw IOException("Unsupported media source")
                val id = dataSpec.uri.lastPathSegment ?: throw IOException("Missing track identifier")

                fun openResolved(forceRefresh: Boolean): Long {
                    // Called on Media3's loader thread.
                    val access = runBlocking { resolve(id, forceRefresh) }
                    val spec = dataSpec.withUri(Uri.parse(access.url))
                    return source.open(
                        if (id.startsWith("youtube:") && spec.uri.scheme == "https")
                            spec.withRequestHeaders(spec.httpRequestHeaders + YOUTUBE_HEADERS)
                        else spec
                    )
                }

                try {
                    return openResolved(false)
                } catch (error: IOException) {
                    val status = generateSequence<Throwable>(error) { it.cause }
                        .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
                        .firstOrNull()?.responseCode
                        ?: (error as? ProviderException)?.status
                    if (status !in listOf(403, 410) || !refreshed.add(id)) throw error
                    try {
                        source.close()
                    } catch (closeError: IOException) {
                        error.addSuppressed(closeError)
                        throw error
                    }
                    // Retry outside the catch: a second failure reaches Media3 normally.
                    return openResolved(true)
                }
            }
        }
    }

    suspend fun invalidate(id: String) { mutex.withLock { cached.remove(id) } }
}
