package com.still.music

import com.still.music.provider.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PlaylistImporterTest {
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
}
