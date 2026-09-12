package com.still.music

import com.still.music.provider.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class AudiusProviderTest {
    private fun row(extra: String = "") = """{"id":"abc12","title":"Test title","user":{"name":"Artist"},"duration":12.5$extra}"""
    private fun parse(extra: String = "") = AudiusParser.track(Json.parseToJsonElement(row(extra)).jsonObject)!!
    @Test fun parsesDurationAndQualifiedIdentity() {
        val track = parse(""","artwork":{"480x480":"https://example.org/art.jpg"}""")
        assertEquals("audius:abc12", track.id)
        assertEquals(12500L, track.durationMs)
        assertTrue(track.playable)
        assertEquals("https://example.org/art.jpg", track.artwork)
    }
    @Test fun rejectsEveryAccessRestriction() {
        listOf(""","is_delete":true""", ""","is_available":false""", ""","is_streamable":false""",
            ""","is_stream_gated":true""", ""","access":{"stream":false}""",
            ""","allowed_api_keys":["another-app"]""", ""","access_authorities":["authority"]""").forEach {
            assertFalse(it, parse(it).playable)
        }
    }
    @Test fun ignoresMalformedRowsAndNullArtwork() {
        assertNull(AudiusParser.track(Json.parseToJsonElement("""{"id":"missing-fields"}""").jsonObject))
        assertNull(parse(""","artwork":null""").artwork)
        assertEquals(0L, AudiusParser.track(Json.parseToJsonElement("""{"id":"t","title":"T","user":{"name":"A"}}""").jsonObject)!!.durationMs)
    }
    @Test fun allowedPublicKeyDoesNotOverrideGate() {
        val parsed = AudiusParser.track(Json.parseToJsonElement(row(""","allowed_api_keys":["public-key"],"is_stream_gated":true""")).jsonObject, "public-key")!!
        assertFalse(parsed.playable)
    }
    @Test fun searchEncodesQueryAndNoCredentialsNeeded() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"data":[${row()}]}"""))
            val provider = AudiusProvider(OkHttpClient(), baseUrl = server.url("/v1/").toString())
            assertEquals(1, provider.search("a & b").size)
            val request = server.takeRequest()
            assertEquals("a & b", request.requestUrl!!.queryParameter("query"))
            assertEquals("Still", request.requestUrl!!.queryParameter("app_name"))
            assertNull(request.requestUrl!!.queryParameter("api_key"))
        }
    }
    @Test fun resolvesOnlyOfficialSecureAccess() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"data":${row()}}"""))
            server.enqueue(MockResponse().setBody("""{"data":"https://audio.example.org/signed.mp3"}"""))
            val provider = AudiusProvider(OkHttpClient(), baseUrl = server.url("/v1/").toString())
            assertEquals("https://audio.example.org/signed.mp3", provider.resolvePlayback("audius:abc12").url)
            assertEquals("/v1/tracks/abc12?app_name=Still", server.takeRequest().path)
            val request = server.takeRequest()
            assertEquals("true", request.requestUrl!!.queryParameter("no_redirect"))
            assertNull(request.requestUrl!!.queryParameter("skip_check"))
        }
    }
    @Test fun gatedTrackNeverRequestsStream() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"data":${row(""","is_stream_gated":true""")}}"""))
            val provider = AudiusProvider(OkHttpClient(), baseUrl = server.url("/v1/").toString())
            try { provider.resolvePlayback("audius:abc12"); fail("Expected access refusal") }
            catch (e: ProviderException) { assertEquals(403, e.status) }
            assertEquals(1, server.requestCount)
        }
    }
    @Test fun surfacesRateLimitAndBadResponse() = runBlocking {
        MockWebServer().use { server ->
            val provider = AudiusProvider(OkHttpClient(), baseUrl = server.url("/v1/").toString())
            server.enqueue(MockResponse().setResponseCode(429))
            try { provider.search("track"); fail() } catch (e: ProviderException) { assertEquals(429, e.status) }
            server.enqueue(MockResponse().setBody("not-json"))
            try { provider.search("track"); fail() } catch (e: ProviderException) { assertTrue(e.message!!.contains("unreadable")) }
        }
    }
}
