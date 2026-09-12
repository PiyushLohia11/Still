package com.still.music

import com.still.music.lyrics.*
import com.still.music.provider.Track
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LyricsTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun parsesOffsetsRepeatedStampsFractionsAndSeekBackwards() {
        val lines = LrcParser.parse("[offset:-100]\n[00:01.5][00:03.050]Example\n[00:99]invalid\n[00:02]Next")
        assertEquals(listOf(1400L, 1900L, 2950L), lines.map { it.timeMs })
        assertEquals(-1, LrcParser.active(lines, 0))
        assertEquals(2, LrcParser.active(lines, 3000))
        assertEquals(0, LrcParser.active(lines, 1500))
    }
    @Test fun lyricsCacheSurvivesReopeningWithoutAnotherRequest() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"plainLyrics":"Example","syncedLyrics":"[00:01.00]Example","instrumental":false}"""))
            val root = folder.newFolder()
            val track = Track("test:1", "Example & Song", "Artist", durationMs = 90000)
            val repository = LyricsRepository(root, OkHttpClient(), server.url("/api/").toString())
            assertEquals(1, repository.load(track)!!.lines.size)
            val request = server.takeRequest()
            assertEquals("Example & Song", request.requestUrl!!.queryParameter("track_name"))
            assertEquals("90", request.requestUrl!!.queryParameter("duration"))
            val reopened = LyricsRepository(root, OkHttpClient(), "http://127.0.0.1:1/")
            assertEquals("Example", reopened.load(track)!!.plain)
            assertEquals(1, server.requestCount)
        }
    }
    @Test fun missingLyricsAndInstrumentalAreDistinct() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(MockResponse().setBody("""{"plainLyrics":null,"syncedLyrics":null,"instrumental":true}"""))
            val repo = LyricsRepository(folder.newFolder(), OkHttpClient(), server.url("/api/").toString())
            assertNull(repo.load(Track("test:1", "Missing", "")))
            assertTrue(repo.load(Track("test:2", "Instrumental", ""))!!.instrumental)
        }
    }
}
