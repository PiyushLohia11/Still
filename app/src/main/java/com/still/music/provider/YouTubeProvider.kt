package com.still.music.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import com.still.music.playback.PlaybackResolver
import java.util.concurrent.TimeUnit
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import kotlin.coroutines.coroutineContext

/** No account cookies or external extraction server. Streams are resolved at playback time. */
class YouTubeProvider(http: OkHttpClient) : MusicProvider {
    override val id = "youtube"
    private val service = ServiceList.YouTube
    private val streamHttp = http.newBuilder()
        .callTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()
    init { NewPipe.init(ExtractorDownloader(http), Localization("en", "IN")) }

    private suspend fun <T> extract(block: () -> T): T = withContext(Dispatchers.IO) {
        try {
            coroutineContext.ensureActive()
            block().also { coroutineContext.ensureActive() }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: ProviderException) { throw e }
        catch (e: Exception) {
            throw ProviderException("YouTube could not load this music. Try again later; the unofficial provider may need an update.").apply { initCause(e) }
        }
    }

    override suspend fun search(query: String, offset: Int): List<Track> {
        if (query.isBlank()) return emptyList()
        require(offset >= 0)
        return extract {
            val handler = service.searchQHFactory.fromQuery(query.trim(), listOf("music_songs"), "")
            val info = SearchInfo.getInfo(service, handler)
            val items = info.relatedItems.toMutableList()
            var next = info.nextPage
            var pages = 0
            // Adapt continuation pages to Still's existing offset contract; bound network work.
            while (items.size < offset + 30 && org.schabi.newpipe.extractor.Page.isValid(next) && pages++ < 10) {
                val page = SearchInfo.getMoreItems(service, handler, next)
                items.addAll(page.items)
                next = page.nextPage
            }
            items.filterIsInstance<StreamInfoItem>().map { item ->
                Track("youtube:" + service.streamLHFactory.getId(item.url), item.name,
                    item.uploaderName.orEmpty(), bestArtwork(item.thumbnails),
                    (item.duration * 1000).coerceAtLeast(0))
            }.distinctBy { it.id }.drop(offset).take(30)
        }
    }

    override suspend fun trackDetails(qualifiedId: String): Track {
        val url = watchUrl(qualifiedId)
        return extract {
            val info = StreamInfo.getInfo(service, url)
            Track(qualifiedId, info.name, info.uploaderName.orEmpty(),
                bestArtwork(info.thumbnails), (info.duration * 1000).coerceAtLeast(0))
        }
    }

    suspend fun getRelatedTracks(track: Track): List<Track> {
        val related = try {
            extract {
                StreamInfo.getInfo(service, watchUrl(track.id)).relatedItems.orEmpty()
                    .filterIsInstance<StreamInfoItem>().map { item ->
                        Track("youtube:" + service.streamLHFactory.getId(item.url), item.name,
                            item.uploaderName.orEmpty(), bestArtwork(item.thumbnails),
                            (item.duration * 1000).coerceAtLeast(0))
                    }
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { emptyList() }
        val pool = if (related.isNotEmpty()) related else search(
            track.artist.takeIf(String::isNotBlank)?.let { "$it songs" } ?: "${track.title} music")
        return pool.filter { it.playable && it.id != track.id }.distinctBy(Track::id).take(30)
    }

    override suspend fun resolvePlayback(qualifiedId: String): PlaybackAccess {
        val url = watchUrl(qualifiedId)
        return extract {
            val info = StreamInfo.getInfo(service, url)
            val audio = info.audioStreams.filter {
                it.isUrl && it.content.startsWith("https://") &&
                    it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP
            }.sortedWith(compareBy<org.schabi.newpipe.extractor.stream.AudioStream> {
                when (it.format) {
                    MediaFormat.M4A -> 0
                    MediaFormat.OPUS, MediaFormat.WEBMA, MediaFormat.WEBMA_OPUS -> 1
                    else -> 2
                }
            }.thenByDescending { it.averageBitrate }).distinctBy { it.content }
            var rejectedStatus = 0
            for (stream in audio) {
                val status = streamStatus(stream.content)
                if (status in 200..299) return@extract PlaybackAccess(stream.content)
                rejectedStatus = status
                // Do not hammer the audio endpoint after a rate-limit response.
                if (status == 429) break
            }
            val progressive = info.videoStreams.filter {
                !it.isVideoOnly && it.isUrl && it.content.startsWith("https://") &&
                    it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP
            }.minByOrNull {
                Regex("""\d+""").find(it.resolution)?.value?.toIntOrNull() ?: Int.MAX_VALUE
            }
            if (progressive != null) return@extract PlaybackAccess(progressive.content)
            throw ProviderException(
                if (rejectedStatus == 429) "YouTube is rate-limiting playback. Try again later."
                else "No playable audio is available for this song.",
                rejectedStatus
            )
        }
    }

    private fun streamStatus(url: String): Int {
        val request = okhttp3.Request.Builder().url(url)
            .header("Range", "bytes=0-0")
        PlaybackResolver.YOUTUBE_HEADERS.forEach { (name, value) -> request.header(name, value) }
        return try {
            streamHttp.newCall(request.build()).execute().use { it.code }
        } catch (e: java.io.InterruptedIOException) {
            if (Thread.currentThread().isInterrupted) throw e
            0 // A timed-out audio endpoint may still have a usable progressive alternative.
        } catch (_: java.io.IOException) {
            0
        }
    }

    internal fun watchUrl(qualifiedId: String): String {
        require(qualifiedId.matches(Regex("youtube:[A-Za-z0-9_-]{11}"))) { "Invalid YouTube track identifier." }
        return "https://www.youtube.com/watch?v=" + qualifiedId.removePrefix("youtube:")
    }
}

internal class ExtractorDownloader(private val http: OkHttpClient) : Downloader() {
    override fun execute(request: Request): Response {
        val builder = okhttp3.Request.Builder().url(request.url())
            .header("User-Agent", "Mozilla/5.0")
        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }
        val body = request.dataToSend()?.toRequestBody()
            ?: if (request.httpMethod() in listOf("POST", "PUT", "PATCH")) ByteArray(0).toRequestBody() else null
        return http.newCall(builder.method(request.httpMethod(), body).build()).execute().use {
            Response(it.code, it.message, it.headers.toMultimap(), it.body.string(), it.request.url.toString())
        }
    }
}
