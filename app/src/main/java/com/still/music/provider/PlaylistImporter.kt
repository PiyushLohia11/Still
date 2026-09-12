package com.still.music.provider

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import java.util.Locale
import kotlin.coroutines.coroutineContext

class PlaylistImporter(private val youtube: MusicProvider) {
    data class Entry(val title: String, val artist: String = "")

    suspend fun read(rawInput: String): List<Entry> = withContext(Dispatchers.IO) {
        val input = rawInput.trim().removePrefix("\uFEFF")
        require(input.isNotBlank()) { "Paste a YouTube playlist URL or song list." }
        if (input.startsWith("https://") || input.startsWith("http://")) {
            val url = input.toHttpUrlOrNull()
                ?: throw IllegalArgumentException("Enter a valid YouTube playlist URL.")
            require(url.host in setOf("youtube.com", "www.youtube.com", "music.youtube.com", "m.youtube.com", "youtu.be")) {
                "Paste a YouTube playlist URL, or export other playlists as text or CSV."
            }
            val id = url.queryParameter("list")
            require(id != null && id.matches(Regex("[A-Za-z0-9_-]+"))) { "The URL must contain a YouTube playlist ID." }
            val playlistUrl = "https://www.youtube.com/playlist?list=$id"
            val service = ServiceList.YouTube
            val info = PlaylistInfo.getInfo(service, playlistUrl)
            val items = info.relatedItems.toMutableList()
            var next = info.nextPage
            while (Page.isValid(next)) {
                coroutineContext.ensureActive()
                val page = PlaylistInfo.getMoreItems(service, playlistUrl, next)
                items.addAll(page.items)
                next = page.nextPage
            }
            items.filter { it.name.isNotBlank() && it.name !in listOf("[Deleted video]", "[Private video]") }
                .map { Entry(it.name, it.uploaderName.orEmpty().removeSuffix(" - Topic")) }
        } else parseText(input)
    }

    suspend fun match(entry: Entry): Track? {
        return try {
            val titleWords = words(entry.title)
            if (titleWords.isEmpty()) return null
            val artistWords = words(entry.artist)
            youtube.search("${entry.title} ${entry.artist}".trim()).filter(Track::playable)
                .map { track ->
                    val titleScore = titleWords.intersect(words(track.title)).size.toDouble() / titleWords.size
                    val artistScore = if (artistWords.isEmpty()) 1.0 else
                        artistWords.intersect(words(track.artist + " " + track.title)).size.toDouble() / artistWords.size
                    Triple(track, titleScore, artistScore)
                }.filter { it.second >= 0.6 && it.third >= 0.5 }
                .maxByOrNull { it.second * 2 + it.third }?.first
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
    }

    private fun words(text: String) = text.lowercase(Locale.ROOT)
        .split(Regex("[^\\p{L}\\p{N}]+")).filter(String::isNotBlank).toSet()

    internal fun parseText(input: String): List<Entry> {
        val firstLine = input.lineSequence().firstOrNull().orEmpty()
        val delimiter = when {
            Regex("\\s+[-–—]\\s+").containsMatchIn(firstLine) && !firstLine.startsWith('"') -> null
            '\t' in firstLine -> '\t'
            ';' in firstLine -> ';'
            ',' in firstLine -> ','
            else -> null
        }
        if (delimiter == null) return input.lineSequence().filter(String::isNotBlank).map { line ->
            val parts = line.trim().split(Regex("\\s+[-–—]\\s+"), limit = 2)
            Entry(parts[0], parts.getOrElse(1) { "" })
        }.toList()
        val rows = csvRows(input, delimiter)
        if (rows.isEmpty()) return emptyList()
        val headers = rows.first().map { it.trim().lowercase(Locale.ROOT) }
        val titleColumn = headers.indexOfFirst { it in setOf("track name", "track title", "song name", "song title", "title", "name", "song") }
        val artistColumn = headers.indexOfFirst { it in setOf("artist", "artists", "artist name", "artist names", "artist name(s)") }
        val hasHeader = titleColumn >= 0
        return rows.drop(if (hasHeader) 1 else 0).mapNotNull { row ->
            val title = row.getOrNull(if (hasHeader) titleColumn else 0)?.trim().orEmpty()
            val artist = row.getOrNull(if (hasHeader) artistColumn else 1)?.trim().orEmpty()
            title.takeIf(String::isNotBlank)?.let { Entry(it, artist) }
        }
    }

    private fun csvRows(input: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0
        while (index < input.length) {
            val char = input[index]
            when {
                char == '"' && quoted && input.getOrNull(index + 1) == '"' -> { field.append('"'); index++ }
                char == '"' -> quoted = !quoted
                char == delimiter && !quoted -> { row.add(field.toString()); field.clear() }
                (char == '\n' || char == '\r') && !quoted -> {
                    row.add(field.toString()); field.clear()
                    if (row.any(String::isNotBlank)) rows.add(row)
                    row = mutableListOf()
                    if (char == '\r' && input.getOrNull(index + 1) == '\n') index++
                }
                else -> field.append(char)
            }
            index++
        }
        require(!quoted) { "CSV has an unclosed quote. Check the pasted export." }
        row.add(field.toString())
        if (row.any(String::isNotBlank)) rows.add(row)
        return rows
    }
}
