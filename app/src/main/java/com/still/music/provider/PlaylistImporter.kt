package com.still.music.provider

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.serialization.json.*
import java.util.concurrent.TimeUnit
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import java.util.Locale
import kotlin.coroutines.coroutineContext

class PlaylistImporter(private val youtube: MusicProvider,
    private val http: OkHttpClient = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()) {
    data class Entry(val title: String, val artist: String = "", val sourceTrack: Track? = null)
    var notice: String? = null
        private set

    suspend fun read(rawInput: String): List<Entry> = withContext(Dispatchers.IO) {
        val input = rawInput.trim().removePrefix("\uFEFF")
        notice = null
        require(input.isNotBlank()) { "Paste a Spotify or YouTube playlist link." }
        if (input.startsWith("https://") || input.startsWith("http://")) {
            val url = input.toHttpUrlOrNull()
                ?: throw IllegalArgumentException("Enter a valid Spotify or YouTube playlist link.")
            if (url.host in setOf("open.spotify.com", "www.open.spotify.com", "spotify.link", "spotify.app.link")) {
                val resolved = if (url.host.endsWith(".link")) resolveSpotifyLink(url.toString()) else url.toString()
                val id = spotifyPlaylistId(resolved)
                val entries = parseSpotifyPage(fetchSpotify("https://open.spotify.com/embed/playlist/$id?utm_source=generator"))
                notice = "Spotify imports the songs exposed by its public preview; large playlists may be incomplete."
                return@withContext entries
            }
            require(url.host in setOf("youtube.com", "www.youtube.com", "music.youtube.com", "m.youtube.com", "youtu.be")) {
                "Paste a Spotify or YouTube playlist link, or a text/CSV song list."
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
                .mapNotNull { item ->
                    val videoId = try { service.streamLHFactory.getId(item.url) }
                        catch (_: Exception) { return@mapNotNull null }
                    val track = Track("youtube:$videoId", item.name, item.uploaderName.orEmpty(),
                        item.thumbnails.firstOrNull()?.url, (item.duration * 1000).coerceAtLeast(0))
                    Entry(track.title, track.artist, track)
                }
        } else parseText(input)
    }

    internal fun spotifyPlaylistId(link: String): String {
        val url = link.toHttpUrlOrNull()
        require(url != null && url.host in setOf("open.spotify.com", "www.open.spotify.com")) {
            "This Spotify link did not resolve to a playlist."
        }
        val segments = url.pathSegments.filter(String::isNotBlank)
            .dropWhile { it == "embed" || it.startsWith("intl-") }
        require(segments.size == 2 && segments[0] == "playlist" && segments[1].matches(Regex("[A-Za-z0-9]{22}"))) {
            "Use a Spotify playlist link, not a song, album, or artist link."
        }
        return segments[1]
    }

    private fun request(url: String) = Request.Builder().url(url)
        .header("User-Agent", com.still.music.playback.PlaybackResolver.USER_AGENT).build()

    private fun resolveSpotifyLink(link: String): String {
        var url = link.toHttpUrlOrNull() ?: throw IllegalArgumentException("Invalid Spotify link.")
        repeat(5) {
            require(url.isHttps && url.host in setOf("spotify.link", "spotify.app.link", "open.spotify.com", "www.open.spotify.com")) {
                "Unsupported Spotify link redirect. Use the full open.spotify.com playlist link."
            }
            if (url.host == "open.spotify.com" || url.host == "www.open.spotify.com") return url.toString()
            http.newCall(request(url.toString())).execute().use { response ->
                require(response.code in 300..399) { "Could not expand Spotify link. Paste the full playlist link." }
                url = response.header("Location")?.let(url::resolve)
                    ?: throw IllegalArgumentException("Spotify link has no playlist destination.")
            }
        }
        throw IllegalArgumentException("Too many Spotify redirects. Paste the full playlist link.")
    }

    private fun fetchSpotify(url: String): String = http.newCall(request(url)).execute().use { response ->
        if (!response.isSuccessful) throw ProviderException(
            if (response.code == 429) "Spotify is rate-limiting requests. Try again later."
            else "Spotify playlist is unavailable. Check that it is public and the link is correct.", response.code)
        response.body.string()
    }

    internal fun parseSpotifyPage(html: String): List<Entry> {
        val script = Regex("""<script\b[^>]*\bid\s*=\s*["']__NEXT_DATA__["'][^>]*>(.*?)</script>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).find(html)?.groupValues?.get(1)
            ?: throw ProviderException("Spotify did not expose this playlist. Check that it is public, or try later.")
        val root = Json.parseToJsonElement(script).jsonObject
        val props = root["props"]?.jsonObject?.get("pageProps")?.jsonObject
        val entity = props?.get("state")?.jsonObject?.get("data")?.jsonObject?.get("entity")?.jsonObject
        val tracks = entity?.get("trackList") as? JsonArray
            ?: throw ProviderException("Spotify playlist is private, unavailable, or could not be read. Try another public playlist.")
        return tracks.mapNotNull { value ->
            val track = value as? JsonObject ?: return@mapNotNull null
            val uri = track["uri"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (!uri.startsWith("spotify:track:")) return@mapNotNull null
            val title = track["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val artist = track["subtitle"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (title.isBlank()) null else Entry(title, artist)
        }
    }

    suspend fun match(entry: Entry): Track? {
        coroutineContext.ensureActive()
        entry.sourceTrack?.let { return it.takeIf(Track::playable) }
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
