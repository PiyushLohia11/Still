package com.still.music

import com.still.music.offline.AudioTransfer
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class AudioTransferTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun assemblesAndValidatesMultipleRanges() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 0-2/6").setBody("abc"))
            server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 3-5/6").setBody("def"))
            val file = folder.newFile()
            assertEquals(6L, AudioTransfer(OkHttpClient(), 3).copy(server.url("/audio").toString(), file) { })
            assertEquals("abcdef", file.readText())
            assertEquals("bytes=0-2", server.takeRequest().getHeader("Range"))
            assertEquals("bytes=3-5", server.takeRequest().getHeader("Range"))
        }
    }
    @Test fun rejectsIgnoredRangeInsteadOfDuplicatingAudio() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 0-2/6").setBody("abc"))
            server.enqueue(MockResponse().setBody("abcdef"))
            try {
                AudioTransfer(OkHttpClient(), 3).copy(server.url("/audio").toString(), folder.newFile()) { }
                fail("Must reject ignored continuation range")
            } catch (_: IOException) { }
        }
    }
    @Test fun acceptsFullResponseAndRejectsMismatchedRangeOrHtml() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("abcdef"))
            assertEquals(6L, AudioTransfer(OkHttpClient(), 3).copy(server.url("/audio").toString(), folder.newFile()) { })
            server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 1-3/6").setBody("bcd"))
            server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("<html>error</html>"))
            repeat(2) {
                try {
                    AudioTransfer(OkHttpClient(), 3).copy(server.url("/audio").toString(), folder.newFile()) { }
                    fail("Must reject invalid response")
                } catch (_: IOException) { }
            }
        }
    }
}
