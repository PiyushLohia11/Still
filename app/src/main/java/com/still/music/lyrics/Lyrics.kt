package com.still.music.lyrics

import com.still.music.provider.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

data class LyricLine(val timeMs: Long, val text: String)
data class Lyrics(val plain: String, val lines: List<LyricLine>, val instrumental: Boolean = false)
object LrcParser {
    private val stamp = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    fun parse(text: String): List<LyricLine> {
        val offset = Regex("\\[offset:([+-]?\\d+)]", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0
        return text.lineSequence().flatMap { row ->
            val times = stamp.findAll(row).toList()
            val words = row.replace(stamp, "").trim()
            times.asSequence().mapNotNull { match ->
                val seconds = match.groupValues[2].toLong()
                if (seconds >= 60) null else LyricLine(
                    (match.groupValues[1].toLong() * 60000 + seconds * 1000 +
                        match.groupValues[3].padEnd(3, '0').take(3).toLong() + offset).coerceAtLeast(0), words)
            }
        }.sortedBy { it.timeMs }.toList()
    }
    fun active(lines: List<LyricLine>, position: Long): Int = lines.indexOfLast { it.timeMs <= position }
}

class LyricsRepository(private val root: File, private val http: OkHttpClient,
    private val baseUrl: String = "https://lrclib.net/api/") {
    private fun file(id: String) = File(root, MessageDigest.getInstance("SHA-256").digest(id.toByteArray())
        .joinToString("") { "%02x".format(it) } + ".json")
    suspend fun load(track: Track, refresh: Boolean = false): Lyrics? = withContext(Dispatchers.IO) {
        val target = file(track.id)
        if (!refresh && target.isFile) {
            runCatching { decode(target.readText()) }.getOrNull()?.let { return@withContext it }
        }
        val url = baseUrl.toHttpUrl().newBuilder().addPathSegment("get")
            .addQueryParameter("track_name", track.title)
            .addQueryParameter("artist_name", track.artist.removeSuffix(" - Topic"))
            .apply { if (track.durationMs > 0) addQueryParameter("duration", (track.durationMs / 1000).toString()) }
            .build()
        http.newCall(Request.Builder().url(url).header("User-Agent", "Still/0.1 (Android music player)").build()).execute().use {
            if (it.code == 404) return@withContext null
            if (!it.isSuccessful) throw IOException("Lyrics could not load. Check your connection and retry.")
            val text = it.body.string()
            val result = decode(text)
            root.mkdirs()
            // A cache write failure must not prevent online lyrics from displaying.
            runCatching {
                val temp = File.createTempFile("lyrics-", ".tmp", root)
                try { temp.writeText(text); if (!temp.renameTo(target)) throw IOException("Lyrics cache write failed.") }
                finally { temp.delete() }
            }
            result
        }
    }
    private fun decode(text: String): Lyrics {
        val row = Json.parseToJsonElement(text).jsonObject
        return Lyrics(row["plainLyrics"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            LrcParser.parse(row["syncedLyrics"]?.jsonPrimitive?.contentOrNull.orEmpty()),
            row["instrumental"]?.jsonPrimitive?.booleanOrNull ?: false)
    }
}
