package com.still.music.offline

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

/** Bounded HTTP ranges avoid one long CDN response; never append an ignored or misaligned range. */
class AudioTransfer(private val client: OkHttpClient, private val chunkBytes: Long = 1024 * 1024,
    private val maxBytes: Long = Downloads.MAX_BYTES) {
    suspend fun copy(url: String, destination: File, progress: suspend (Int) -> Unit): Long = withContext(Dispatchers.IO) {
        var received = 0L
        var total = -1L
        var lastUpdate = 0L
        destination.outputStream().use { output ->
            do {
                coroutineContext.ensureActive()
                val requestedEnd = minOf(received + chunkBytes - 1, maxBytes - 1)
                val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0")
                    .header("Accept-Encoding", "identity").header("Range", "bytes=$received-$requestedEnd").build()
                client.newCall(request).execute().use { response ->
                    if (response.code != 200 && response.code != 206)
                        throw IOException("Download server returned HTTP ${response.code}. Retry.")
                    val type = response.body.contentType()?.toString().orEmpty()
                    if (type.startsWith("text/") || type.contains("json")) throw IOException("The server did not return audio.")
                    val contentLength = response.body.contentLength()
                    val expectedChunk: Long
                    if (response.code == 206) {
                        val range = Regex("bytes ([0-9]+)-([0-9]+)/([0-9]+)").matchEntire(response.header("Content-Range").orEmpty())
                            ?: throw IOException("The server returned an invalid audio range.")
                        val start = range.groupValues[1].toLong()
                        val end = range.groupValues[2].toLong()
                        val declaredTotal = range.groupValues[3].toLong()
                        if (start != received || end < start || end > requestedEnd || end >= declaredTotal ||
                            (total >= 0 && total != declaredTotal)) throw IOException("The audio changed during download. Retry.")
                        total = declaredTotal
                        expectedChunk = end - start + 1
                        if (contentLength >= 0 && contentLength != expectedChunk) throw IOException("Incomplete audio range.")
                    } else {
                        if (received != 0L) throw IOException("The server ignored the audio range. Retry.")
                        total = contentLength
                        expectedChunk = contentLength
                    }
                    if (total > maxBytes || (total > 0 && destination.parentFile!!.usableSpace < total - received + 10_000_000))
                        throw IOException("Not enough storage, or this track exceeds the 256 MB limit.")
                    var chunkReceived = 0L
                    response.body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            chunkReceived += read
                            received += read
                            if (received > maxBytes || (expectedChunk >= 0 && chunkReceived > expectedChunk))
                                throw IOException("The server returned too much audio.")
                            output.write(buffer, 0, read)
                            val now = System.nanoTime()
                            if (now - lastUpdate > 500_000_000) {
                                progress(if (total > 0) (received * 100 / total).toInt().coerceIn(0, 99) else -1)
                                lastUpdate = now
                            }
                        }
                    }
                    if (chunkReceived <= 0 || (expectedChunk >= 0 && chunkReceived != expectedChunk))
                        throw IOException("Download was incomplete. Retry.")
                    if (response.code == 200 && total < 0) total = received
                }
            } while (received < total)
        }
        if (received <= 0 || received != total) throw IOException("Download was incomplete. Retry.")
        received
    }
}
