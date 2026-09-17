package com.still.music

import com.still.music.provider.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PlaylistImporterTest {
    @Test fun bomAndOversizedImportsAreHandledBeforeNetworkRequests() = runBlocking {
        val importer = PlaylistImporter(Provider())
        assertEquals(listOf(PlaylistImporter.Entry("One", "U2")), importer.read("\uFEFFTitle,Artist\r\nOne,U2"))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { importer.read("a".repeat(PlaylistImporter.MAX_INPUT_BYTES + 1)) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { importer.read((0..PlaylistImporter.MAX_ENTRIES).joinToString("\n") { "Song $it" }) }
        }
        Unit
    }
    private class Provider : MusicProvider {
        override val id = "youtube"
        var results = emptyList<Track>()
        var failure: Exception? = null
        override suspend fun search(query: String, offset: Int): List<Track> {
            failure?.let { throw it }
            return results
        }
        override suspend fun trackDetails(qualifiedId: String): Track = error("unused")
        override suspend fun resolvePlayback(qualifiedId: String): PlaybackAccess = error("unused")
    }

    @Test fun textKeepsCommasAndSplitsArtist() {
        val entries = PlaylistImporter(Provider()).parseText("Hello, Goodbye - The Beatles\nOne – U2\n\n")
        assertEquals(listOf(PlaylistImporter.Entry("Hello, Goodbye", "The Beatles"),
            PlaylistImporter.Entry("One", "U2")), entries)
    }

    @Test fun spotifyCsvFindsColumnsAndHandlesQuotedFields() {
        val entries = PlaylistImporter(Provider()).parseText(
            "Track URI,Track Name,Artist Name(s),Album\r\n" +
                "spotify:track:1,\"Hello, Goodbye\",The Beatles,Example\r\n" +
                "spotify:track:2,\"Say \"\"Hello\"\"\",Artist,Example")
        assertEquals("Hello, Goodbye", entries[0].title)
        assertEquals("The Beatles", entries[0].artist)
        assertEquals("Say \"Hello\"", entries[1].title)
    }

    @Test fun tabExportAndMalformedCsv() {
        val importer = PlaylistImporter(Provider())
        assertEquals(listOf(PlaylistImporter.Entry("One", "U2")),
            importer.parseText("Name\tArtist\tAlbum\nOne\tU2\tAchtung Baby"))
        assertThrows(IllegalArgumentException::class.java) { importer.parseText("Title,Artist\n\"Unclosed,Artist") }
    }

    @Test fun skipsWrongArtistsUnavailableTracksAndSearchFailures() = runBlocking {
        val provider = Provider()
        val importer = PlaylistImporter(provider)
        val entry = PlaylistImporter.Entry("One", "U2")
        provider.results = listOf(Track("youtube:1", "One", "Other"),
            Track("youtube:2", "One", "U2", unavailableReason = "Unavailable"))
        assertNull(importer.match(entry))
        provider.failure = ProviderException("Rate limited", 429)
        assertNull(importer.match(entry))
        provider.failure = null
        val expected = Track("youtube:3", "One (Official Audio)", "U2")
        provider.results = listOf(expected)
        assertEquals(expected, importer.match(entry))
    }

    @Test fun cancellationIsNotSwallowed() {
        val provider = Provider().apply { failure = CancellationException("cancel") }
        assertThrows(CancellationException::class.java) {
            runBlocking { PlaylistImporter(provider).match(PlaylistImporter.Entry("One")) }
        }
    }

    private val spotifyHtml = """<html><script type="application/json" id="__NEXT_DATA__">
        {"props":{"pageProps":{"state":{"data":{"entity":{"trackList":[
          {"uri":"spotify:track:one","title":"One","subtitle":"U2","isPlayable":false},
          {"uri":"spotify:episode:two","title":"Podcast","subtitle":"Host"},
          {"uri":"spotify:track:three","title":"Hello, Goodbye","subtitle":"The Beatles"}
        ]}}}}}}</script></html>"""

    @Test fun parsesSpotifyMetadataWithoutRequiringSpotifyPlayback() {
        assertEquals(listOf(PlaylistImporter.Entry("One", "U2"),
            PlaylistImporter.Entry("Hello, Goodbye", "The Beatles")),
            PlaylistImporter(Provider()).parseSpotifyPage(spotifyHtml))
    }

    @Test fun validatesSpotifyPlaylistLinks() {
        val importer = PlaylistImporter(Provider())
        val id = "3cEYpjA9oz9GiPac4AsH4n"
        for (path in listOf("playlist", "intl-en/playlist", "embed/playlist")) {
            assertEquals(id, importer.spotifyPlaylistId("https://open.spotify.com/$path/$id?si=example"))
        }
        for (link in listOf("https://open.spotify.com/track/$id", "https://example.com/playlist/$id")) {
            assertThrows(IllegalArgumentException::class.java) { importer.spotifyPlaylistId(link) }
        }
    }

    @Test fun readsSpotifySharedLinkAndReportsPreviewLimitation() = runBlocking {
        val requests = mutableListOf<String>()
        val http = okhttp3.OkHttpClient.Builder().followRedirects(false).addInterceptor { chain ->
            val request = chain.request()
            requests.add(request.url.toString())
            okhttp3.Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(if (request.url.host == "spotify.link") 302 else 200).message("test")
                .header("Location", "https://open.spotify.com/playlist/3cEYpjA9oz9GiPac4AsH4n?si=test")
                .body(okhttp3.ResponseBody.create(null, spotifyHtml)).build()
        }.build()
        val importer = PlaylistImporter(Provider(), http)
        assertEquals(2, importer.read("https://spotify.link/example").size)
        assertTrue(requests.last().startsWith("https://open.spotify.com/embed/playlist/"))
        assertNotNull(importer.notice)
    }

    @Test fun unavailableSpotifyPageHasHelpfulError() {
        val error = assertThrows(ProviderException::class.java) {
            PlaylistImporter(Provider()).parseSpotifyPage(
                """<script id="__NEXT_DATA__">{"props":{"pageProps":{"status":500}}}</script>""")
        }
        assertTrue(error.message!!.contains("unavailable"))
    }

    @Test fun youtubeEntriesKeepOriginalTrackAndDoNotSearchAgain() = runBlocking {
        val provider = Provider().apply { failure = IllegalStateException("Search must not run") }
        val track = Track("youtube:abcdefghijk", "One", "Music uploader")
        assertEquals(track, PlaylistImporter(provider).match(PlaylistImporter.Entry(track.title, track.artist, track)))
    }
}
