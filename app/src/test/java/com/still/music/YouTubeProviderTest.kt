package com.still.music

import com.still.music.provider.YouTubeProvider
import com.still.music.provider.ExtractorDownloader
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.schabi.newpipe.extractor.downloader.Request

class YouTubeProviderTest {
    @Test fun rejectsForeignIdsAndInjectedUrls() {
        val provider = YouTubeProvider(OkHttpClient())
        for (id in listOf("audius:abc", "youtube:", "youtube:abcdefghijk&x=y", "https://example.com")) {
            assertThrows(IllegalArgumentException::class.java) { provider.watchUrl(id) }
        }
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", provider.watchUrl("youtube:dQw4w9WgXcQ"))
    }
    @Test fun downloaderPreservesMethodBodyAndExtractorHeaders() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(429).setBody("limited").setHeader("Retry-After", "30"))
            val response = ExtractorDownloader(OkHttpClient()).execute(Request.newBuilder()
                .post(server.url("/search").toString(), "{}".toByteArray())
                .setHeader("User-Agent", "extractor-agent").setHeader("Content-Type", "application/json").build())
            val sent = server.takeRequest()
            assertEquals("POST", sent.method)
            assertEquals("{}", sent.body.readUtf8())
            assertEquals("extractor-agent", sent.getHeader("User-Agent"))
            assertEquals(429, response.responseCode())
            assertEquals("limited", response.responseBody())
            assertEquals("30", response.getHeader("Retry-After"))
        }
    }
}
